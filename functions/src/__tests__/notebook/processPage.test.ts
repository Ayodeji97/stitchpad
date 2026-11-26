import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';
import { parseNotebookPath, processPageHandler, retryNotebookPageHandler, ProcessDeps } from '../../notebook/processPage';
import { fakeDb } from './fakeDb';

const PATH = 'users/u1/notebookImports/j1/p1.jpg';
const PAGE = 'users/u1/notebookImports/j1/pages/p1';
const JOB = 'users/u1/notebookImports/j1';
const USAGE = 'users/u1/usage/notebookImport';
const NOW = new Date('2026-11-24T10:00:00Z');

const person = (name: string) => ({
  name, phone: null, gender: 'female', unit: 'inches', dateWritten: null,
  values: [{ label: 'Bust', value: '36', fieldKey: 'bust', flagged: false }],
  box: null,
});
const goodJson = JSON.stringify([person('Ada Obi'), person('Bisi Ade')]);

function setup(opts: { initial?: Record<string, any>; model?: jest.Mock; read?: jest.Mock; noJob?: boolean } = {}) {
  const base: Record<string, any> = { 'users/u1': {}, [JOB]: { status: 'open', pageCount: 0 } };
  if (opts.noJob) delete base[JOB];
  const { db, store } = fakeDb({ ...base, ...opts.initial });
  const generateJsonFromImage = opts.model ?? jest.fn().mockResolvedValue(goodJson);
  const readImage = opts.read ?? jest.fn().mockResolvedValue(Buffer.from('img'));
  const deleteObject = jest.fn().mockResolvedValue(undefined);
  const deps: ProcessDeps = {
    db,
    readImage,
    deleteObject,
    vertex: { generateText: jest.fn(), generateJsonFromImage },
    now: () => NOW,
  };
  return { deps, store, generateJsonFromImage, readImage, deleteObject };
}

const cards = (store: Map<string, any>) => [...store.keys()].filter((k) => k.includes('/cards/'));
const authCtx = (uid: string) => ({ auth: { uid } } as unknown as functions.https.CallableContext);

describe('parseNotebookPath', () => {
  it('path_parses', () => {
    expect(parseNotebookPath(PATH)).toEqual({ uid: 'u1', jobId: 'j1', pageId: 'p1' });
  });
  it('path_rejectsOtherPrefixes', () => {
    expect(parseNotebookPath('users/u1/orders/o1/photos/a.jpg')).toBeNull();
    expect(parseNotebookPath('users/u1/notebookImports/j1/extra/p1.jpg')).toBeNull();
    expect(parseNotebookPath('users/u1/notebookImports/j1/p1.png')).toBeNull();
  });
});

describe('processPageHandler', () => {
  it('process_writesCardsAndDone', async () => {
    const { deps, store, generateJsonFromImage } = setup();
    await processPageHandler(PATH, deps);
    expect(store.get(PAGE)).toMatchObject({ status: 'done', personCount: 2, storagePath: PATH });
    expect(store.get(PAGE).error).toBeUndefined();
    expect(cards(store).sort()).toEqual([`${JOB}/cards/p1_0`, `${JOB}/cards/p1_1`]);
    expect(store.get(`${JOB}/cards/p1_1`)).toMatchObject({
      name: 'Bisi Ade', pageId: 'p1', personIndex: 1, status: 'new', matchedCustomerId: null, savedCustomerId: null,
    });
    expect(generateJsonFromImage.mock.calls[0][0]).toMatchObject({
      mimeType: 'image/jpeg', imageBase64: Buffer.from('img').toString('base64'),
    });
    expect(store.get(JOB).pageCount).toEqual(admin.firestore.FieldValue.increment(1));
  });

  it('ignores objects outside the notebook path', async () => {
    const { deps, store, generateJsonFromImage } = setup();
    await processPageHandler('users/u1/orders/o1/photos/a.jpg', deps);
    expect(generateJsonFromImage).not.toHaveBeenCalled();
    expect(store.has(PAGE)).toBe(false);
  });

  it('emptyPage_doneWithZeroCards', async () => {
    const { deps, store } = setup({ model: jest.fn().mockResolvedValue('[]') });
    await processPageHandler(PATH, deps);
    expect(store.get(PAGE)).toMatchObject({ status: 'done', personCount: 0 });
    expect(cards(store)).toEqual([]);
  });

  it('badModelOutput_pageFailedNoCards', async () => {
    const { deps, store } = setup({ model: jest.fn().mockResolvedValue('not json at all') });
    await processPageHandler(PATH, deps);
    expect(store.get(PAGE)).toMatchObject({ status: 'failed', error: 'read_failed', personCount: 0 });
    expect(cards(store)).toEqual([]);
  });

  it('vertexThrows_pageFailed', async () => {
    const { deps, store } = setup({ model: jest.fn().mockRejectedValue(new Error('boom')) });
    await processPageHandler(PATH, deps);
    expect(store.get(PAGE)).toMatchObject({ status: 'failed', error: 'read_failed' });
    expect(cards(store)).toEqual([]);
  });

  it('marks the page processing before calling the model', async () => {
    const { deps, store } = setup();
    let seen: unknown;
    (deps.vertex.generateJsonFromImage as jest.Mock).mockImplementation(async () => {
      seen = store.get(PAGE)?.status;
      return goodJson;
    });
    await processPageHandler(PATH, deps);
    expect(seen).toBe('processing');
  });

  it('finalizeTwice_cardsWrittenOnce', async () => {
    const { deps, store, generateJsonFromImage } = setup();
    await processPageHandler(PATH, deps);
    const usageAfterFirst = JSON.stringify(store.get(USAGE));
    const pageCountAfterFirst = store.get(JOB).pageCount;
    await processPageHandler(PATH, deps);
    expect(generateJsonFromImage).toHaveBeenCalledTimes(1);
    expect(JSON.stringify(store.get(USAGE))).toBe(usageAfterFirst);
    expect(store.get(JOB).pageCount).toBe(pageCountAfterFirst);
    expect(cards(store)).toHaveLength(2);
  });

  it('concurrent_delivery_onlyOneProcesses', async () => {
    const { deps, store, generateJsonFromImage } = setup();
    await Promise.all([processPageHandler(PATH, deps), processPageHandler(PATH, deps)]);
    expect(generateJsonFromImage).toHaveBeenCalledTimes(1);
    expect(store.get(USAGE).lifetimePages).toBe(1);
    expect(cards(store)).toHaveLength(2);
    expect(store.get(JOB).pageCount).toEqual(admin.firestore.FieldValue.increment(1));
  });

  it('a fresh processing claim is left alone', async () => {
    const { deps, generateJsonFromImage } = setup({
      initial: { [PAGE]: { status: 'processing', claimedAt: new Date(NOW.getTime() - 60_000) } },
    });
    await processPageHandler(PATH, deps);
    expect(generateJsonFromImage).not.toHaveBeenCalled();
  });

  it('stale_processing_isReclaimed', async () => {
    const { deps, store, generateJsonFromImage } = setup({
      initial: { [PAGE]: { status: 'processing', claimedAt: new Date(NOW.getTime() - 11 * 60_000) } },
    });
    await processPageHandler(PATH, deps);
    expect(generateJsonFromImage).toHaveBeenCalledTimes(1);
    expect(store.get(PAGE)).toMatchObject({ status: 'done', personCount: 2 });
  });

  it('closedJob_deletesUploadAndWritesNothing', async () => {
    const { deps, store, generateJsonFromImage, deleteObject } = setup({
      initial: { [JOB]: { status: 'saved', pageCount: 3 } },
    });
    await processPageHandler(PATH, deps);
    expect(generateJsonFromImage).not.toHaveBeenCalled();
    expect(deleteObject).toHaveBeenCalledWith(PATH);
    expect(store.has(PAGE)).toBe(false);
    expect(cards(store)).toEqual([]);
    expect(store.has(USAGE)).toBe(false);
    expect(store.get(JOB)).toEqual({ status: 'saved', pageCount: 3 });
  });

  it('deletedJob_deletesUploadAndNeverRecreatesTheJob', async () => {
    const { deps, store, generateJsonFromImage, deleteObject } = setup({ noJob: true });
    await processPageHandler(PATH, deps);
    expect(generateJsonFromImage).not.toHaveBeenCalled();
    expect(deleteObject).toHaveBeenCalledWith(PATH);
    expect(store.has(JOB)).toBe(false);
    expect(store.has(PAGE)).toBe(false);
    expect(cards(store)).toEqual([]);
    expect(store.has(USAGE)).toBe(false);
  });

  it('jobDeletedWhileReading_writesNoCardsAndNeverRecreatesTheJob', async () => {
    const { deps, store, deleteObject } = setup();
    (deps.vertex.generateJsonFromImage as jest.Mock).mockImplementation(async () => {
      store.delete(JOB);
      return goodJson;
    });
    await processPageHandler(PATH, deps);
    expect(store.has(JOB)).toBe(false);
    expect(cards(store)).toEqual([]);
    expect(deleteObject).toHaveBeenCalledWith(PATH);
  });

  it('cap_freeLifetime300: the 301st page fails without calling the model', async () => {
    const { deps, store, generateJsonFromImage } = setup({
      initial: { [USAGE]: { lifetimePages: 300, monthKey: '2026-11', monthPages: 300 } },
    });
    await processPageHandler(PATH, deps);
    expect(store.get(PAGE)).toMatchObject({ status: 'failed', error: 'page_cap_reached', personCount: 0 });
    expect(generateJsonFromImage).not.toHaveBeenCalled();
  });
});

describe('retryNotebookPageHandler', () => {
  it('retry_onlyOwnFailedPage', async () => {
    const { deps, store, generateJsonFromImage } = setup({
      initial: { [PAGE]: { status: 'failed', error: 'read_failed', storagePath: PATH, personCount: 0 } },
    });
    await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, {} as never, deps))
      .rejects.toMatchObject({ code: 'unauthenticated' });
    // another user's page is invisible to u2
    await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u2'), deps))
      .rejects.toMatchObject({ code: 'failed-precondition' });
    await expect(retryNotebookPageHandler({ jobId: 7, pageId: 'p1' }, authCtx('u1'), deps))
      .rejects.toMatchObject({ code: 'invalid-argument' });
    expect(generateJsonFromImage).not.toHaveBeenCalled();

    const result = await retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps);
    expect(result).toEqual({ status: 'done' });
    expect(store.get(PAGE)).toMatchObject({ status: 'done', personCount: 2 });
    expect(store.get(PAGE).error).toBeUndefined();
    expect(cards(store)).toHaveLength(2);
    // the earlier read_failed already consumed a reservation; retry does not take another
    expect(store.get(USAGE)).toBeUndefined();
    // and pageCount is not bumped again on retry
    expect(store.get(JOB).pageCount).toBe(0);
  });

  it('two concurrent retries run once', async () => {
    const { deps, generateJsonFromImage } = setup({
      initial: { [PAGE]: { status: 'failed', error: 'read_failed', storagePath: PATH, personCount: 0 } },
    });
    const results = await Promise.allSettled([
      retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps),
      retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps),
    ]);
    expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
    expect(generateJsonFromImage).toHaveBeenCalledTimes(1);
  });

  it('retry_stuckProcessingOver10Min_isRetriedWithoutNewReservation', async () => {
    const { deps, store, generateJsonFromImage } = setup({
      initial: { [PAGE]: { status: 'processing', storagePath: PATH, claimedAt: new Date(NOW.getTime() - 11 * 60_000) } },
    });
    const result = await retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps);
    expect(result).toEqual({ status: 'done' });
    expect(generateJsonFromImage).toHaveBeenCalledTimes(1);
    expect(store.get(USAGE)).toBeUndefined();
    expect(cards(store)).toHaveLength(2);
    // the crashed first run never counted the page
    expect(store.get(JOB).pageCount).toEqual(admin.firestore.FieldValue.increment(1));
  });

  it('retry_freshProcessing_isRefused', async () => {
    const { deps, generateJsonFromImage } = setup({
      initial: { [PAGE]: { status: 'processing', storagePath: PATH, claimedAt: new Date(NOW.getTime() - 60_000) } },
    });
    await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps))
      .rejects.toMatchObject({ code: 'failed-precondition' });
    expect(generateJsonFromImage).not.toHaveBeenCalled();
  });

  it('retry_countsRetriesAndRefusesBeyondThree', async () => {
    const { deps, store, generateJsonFromImage } = setup({
      model: jest.fn().mockResolvedValue('not json'),
      initial: { [PAGE]: { status: 'failed', error: 'read_failed', storagePath: PATH, personCount: 0 } },
    });
    for (let i = 1; i <= 3; i++) {
      await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps))
        .resolves.toEqual({ status: 'failed' });
      expect(store.get(PAGE)).toMatchObject({ status: 'failed', retryCount: i });
    }
    await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps))
      .rejects.toMatchObject({ code: 'resource-exhausted', message: 'retry_limit_reached' });
    expect(generateJsonFromImage).toHaveBeenCalledTimes(3);
    expect(store.get(PAGE)).toMatchObject({ status: 'failed', retryCount: 3 });
  });

  it('retry_onClosedJob_isRefused', async () => {
    const { deps, generateJsonFromImage } = setup({
      initial: {
        [JOB]: { status: 'abandoned', pageCount: 1 },
        [PAGE]: { status: 'failed', error: 'read_failed', storagePath: PATH, personCount: 0 },
      },
    });
    await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps))
      .rejects.toMatchObject({ code: 'failed-precondition', message: 'job_not_open' });
    expect(generateJsonFromImage).not.toHaveBeenCalled();
  });

  it('rejects a page that is not failed', async () => {
    const { deps } = setup({ initial: { [PAGE]: { status: 'done', storagePath: PATH } } });
    await expect(retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps))
      .rejects.toMatchObject({ code: 'failed-precondition' });
  });

  it('retry_capFailureDoesNotDoubleCount', async () => {
    const usage = { lifetimePages: 300, monthKey: '2026-11', monthPages: 300 };
    const { deps, store, generateJsonFromImage } = setup({
      initial: {
        [USAGE]: usage,
        [PAGE]: { status: 'failed', error: 'page_cap_reached', storagePath: PATH, personCount: 0 },
      },
    });
    const result = await retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps);
    expect(result).toEqual({ status: 'failed' });
    expect(store.get(PAGE)).toMatchObject({ status: 'failed', error: 'page_cap_reached' });
    expect(store.get(USAGE)).toEqual(usage);
    expect(store.get(JOB).pageCount).toBe(0);
    expect(generateJsonFromImage).not.toHaveBeenCalled();
  });

  it('retry after a cap failure reserves quota once the owner is under the cap', async () => {
    const { deps, store } = setup({
      initial: {
        [USAGE]: { lifetimePages: 10, monthKey: '2026-11', monthPages: 10 },
        [PAGE]: { status: 'failed', error: 'page_cap_reached', storagePath: PATH, personCount: 0 },
      },
    });
    const result = await retryNotebookPageHandler({ jobId: 'j1', pageId: 'p1' }, authCtx('u1'), deps);
    expect(result).toEqual({ status: 'done' });
    expect(store.get(USAGE).lifetimePages).toBe(11);
  });
});

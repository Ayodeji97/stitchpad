import { cleanupHandler } from '../../notebook/cleanup';

const DAY = 86_400_000;
const NOW = new Date('2026-12-01T03:15:00Z');
const ts = (ms: number) => ({ toMillis: () => ms });

// Minimal Firestore double: job docs + page/card subcollections, collectionGroup
// queries with where(), and recursiveDelete.
function fakeStore(jobs: Record<string, any>, pages: Record<string, Record<string, any>> = {}) {
  const jobStore = new Map(Object.entries(jobs));
  const pageStore = new Map(Object.entries(pages));
  const deletedTrees: string[] = [];
  const updates: Record<string, any> = {};
  const jobRef = (path: string) => ({
    path,
    set: async (d: any) => { updates[path] = { ...(updates[path] ?? {}), ...d }; jobStore.set(path, { ...jobStore.get(path), ...d }); },
    collection: (name: string) => ({
      get: async () => ({
        docs: Object.entries(name === 'pages' ? pageStore.get(path) ?? {} : {}).map(([id, data]) => ({ id, data: () => data })),
      }),
    }),
  });
  const snapDoc = (path: string) => { const d = jobStore.get(path); return { id: path.split('/').pop()!, ref: jobRef(path), data: () => d }; };
  const db = {
    collectionGroup: () => {
      const filters: Array<(d: any) => boolean> = [];
      const q: any = {
        where(field: string, op: string, value: any) {
          filters.push((d) => {
            const v = d[field];
            if (op === 'in') return value.includes(v);
            if (op === '<=') return v != null && v.toMillis() <= value.toMillis();
            return false;
          });
          return q;
        },
        get: async () => ({ docs: [...jobStore.keys()].filter((p) => filters.every((f) => f(jobStore.get(p)))).map(snapDoc) }),
      };
      return q;
    },
    recursiveDelete: async (ref: any) => { deletedTrees.push(ref.path); jobStore.delete(ref.path); },
  };
  return { db: db as never, jobStore, deletedTrees, updates };
}

const J = (uid: string, id: string) => `users/${uid}/notebookImports/${id}`;
const pagesOf = (uid: string, id: string) => ({
  p1: { storagePath: `${J(uid, id)}/p1.jpg` },
  p2: { storagePath: `${J(uid, id)}/p2.jpg` },
});

describe('cleanupHandler', () => {
  it('cleanup_deletesImagesAfter7Days sets imagesDeletedAt and keeps docs', async () => {
    const path = J('u1', 'old');
    const s = fakeStore({ [path]: { status: 'open', createdAt: ts(NOW.getTime() - 8 * DAY), pageCount: 2 } }, { [path]: pagesOf('u1', 'old') });
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    expect(deleted).toEqual([`${path}/`]);
    expect(s.updates[path].imagesDeletedAt).toBeDefined();
    expect(s.deletedTrees).toEqual([]);
    expect(s.jobStore.has(path)).toBe(true);
  });

  it('cleanup_leavesFreshOpenJobAlone', async () => {
    const path = J('u1', 'fresh');
    const s = fakeStore({ [path]: { status: 'open', createdAt: ts(NOW.getTime() - 2 * DAY), pageCount: 1 } }, { [path]: pagesOf('u1', 'fresh') });
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    expect(deleted).toEqual([]);
    expect(s.updates[path]).toBeUndefined();
  });

  it('cleanup_deletesImagesWhenSavedOrAbandoned even at day 1', async () => {
    const a = J('u1', 'saved');
    const b = J('u1', 'abandoned');
    const s = fakeStore(
      {
        [a]: { status: 'saved', createdAt: ts(NOW.getTime() - DAY), pageCount: 2 },
        [b]: { status: 'abandoned', createdAt: ts(NOW.getTime() - DAY), pageCount: 2 },
      },
      { [a]: pagesOf('u1', 'saved'), [b]: pagesOf('u1', 'abandoned') },
    );
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    expect(deleted.sort()).toEqual([`${a}/`, `${b}/`].sort());
    expect(s.updates[a].imagesDeletedAt).toBeDefined();
    expect(s.updates[b].imagesDeletedAt).toBeDefined();
  });

  it('cleanup_skipsJobsWhoseImagesAreAlreadyDeleted', async () => {
    const path = J('u1', 'done');
    const s = fakeStore({ [path]: { status: 'saved', createdAt: ts(NOW.getTime() - 3 * DAY), pageCount: 2, imagesDeletedAt: ts(1) } }, { [path]: pagesOf('u1', 'done') });
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    expect(deleted).toEqual([]);
  });

  it('cleanup_deletesJobTreeAfter30Days covering pages and cards', async () => {
    const path = J('u1', 'ancient');
    const s = fakeStore({ [path]: { status: 'saved', createdAt: ts(NOW.getTime() - 31 * DAY), pageCount: 2, imagesDeletedAt: ts(1) } });
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    // imagesDeletedAt was already set, yet a late upload must still be swept.
    expect(deleted).toEqual([`${path}/`]);
    expect(s.deletedTrees).toEqual([path]);
  });

  it('cleanup_deletesLeftoverImagesBeforeRemovingA30DayTree', async () => {
    const path = J('u1', 'ancient2');
    const s = fakeStore({ [path]: { status: 'open', createdAt: ts(NOW.getTime() - 31 * DAY), pageCount: 2 } }, { [path]: pagesOf('u1', 'ancient2') });
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    expect(deleted).toEqual([`${path}/`]);
    expect(s.deletedTrees).toEqual([path]);
  });

  it('cleanup_oneFailureDoesNotStopLoop', async () => {
    const bad = J('u1', 'bad');
    const good = J('u2', 'good');
    const s = fakeStore(
      {
        [bad]: { status: 'abandoned', createdAt: ts(NOW.getTime() - DAY), pageCount: 2 },
        [good]: { status: 'abandoned', createdAt: ts(NOW.getTime() - DAY), pageCount: 2 },
      },
      { [bad]: pagesOf('u1', 'bad'), [good]: pagesOf('u2', 'good') },
    );
    const deleted: string[] = [];
    await cleanupHandler({
      db: s.db,
      deletePrefix: async (p) => {
        if (p.includes('/bad/')) throw new Error('boom');
        deleted.push(p);
      },
      now: () => NOW,
    });
    expect(deleted).toEqual([`${good}/`]);
    expect(s.updates[good].imagesDeletedAt).toBeDefined();
    expect(s.updates[bad]).toBeUndefined();
  });

  it('cleanup_deletesImageWithNoPageDocAt7Days (prefix sweep, no pages subcollection)', async () => {
    const path = J('u1', 'nopage');
    const s = fakeStore({ [path]: { status: 'open', createdAt: ts(NOW.getTime() - 8 * DAY), pageCount: 0 } });
    const deleted: string[] = [];
    await cleanupHandler({ db: s.db, deletePrefix: async (p) => { deleted.push(p); }, now: () => NOW });
    expect(deleted).toEqual([`${path}/`]);
  });
});

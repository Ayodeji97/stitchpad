import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { getVertexClient, VertexClient } from '../smart/vertexClient';
import { buildSystemPrompt, RESPONSE_SCHEMA } from './extractionPrompt';
import { ExtractionParseError, parseExtraction } from './parseExtraction';
import { NOTEBOOK_MODEL_ID } from './notebookModel';
import { reservePage } from './pageCap';

const PATH_RE = /^users\/([^/]+)\/notebookImports\/([^/]+)\/([^/]+)\.jpg$/;

export function parseNotebookPath(name: string): { uid: string; jobId: string; pageId: string } | null {
  const m = PATH_RE.exec(name);
  return m ? { uid: m[1], jobId: m[2], pageId: m[3] } : null;
}

export interface ProcessDeps {
  db: admin.firestore.Firestore;
  readImage(path: string): Promise<Buffer>;
  deleteObject(path: string): Promise<void>;
  vertex: VertexClient;
  now: () => Date;
}

type PageStatus = 'processing' | 'done' | 'failed';
type PageError = 'page_cap_reached' | 'read_failed';

function pagePaths(uid: string, jobId: string, pageId: string) {
  const job = `users/${uid}/notebookImports/${jobId}`;
  return { job, page: `${job}/pages/${pageId}`, cards: `${job}/cards` };
}

interface ProcessOptions {
  /** Bump job.pageCount: true only the first time a page finishes. */
  countPage: boolean;
  /** Quota was already reserved by an earlier run of this page. */
  skipReserve: boolean;
  /** Server-owned retry counter, carried onto the finished page doc. */
  retryCount: number;
}

function isOpenJob(snap: { exists: boolean; data(): unknown }): boolean {
  return snap.exists && (snap.data() as { status?: string } | undefined)?.status === 'open';
}

/**
 * Reads one page. The page, its cards and the job's pageCount are written in
 * one transaction that re-reads the job: if the job was closed or deleted while
 * the model ran, nothing is written (the claim is removed) and the image goes.
 * The job doc is only ever update()d, so a deleted job is never re-created.
 */
async function processPage(
  path: { uid: string; jobId: string; pageId: string },
  storagePath: string,
  deps: ProcessDeps,
  opts: ProcessOptions,
): Promise<PageStatus> {
  // The caller has already claimed the page (status 'processing') atomically.
  const { uid, jobId, pageId } = path;
  const { db } = deps;
  const paths = pagePaths(uid, jobId, pageId);
  const pageRef = db.doc(paths.page);
  const jobRef = db.doc(paths.job);

  const finish = async (
    status: 'done' | 'failed',
    error: PageError | null,
    people: ReturnType<typeof parseExtraction>,
  ): Promise<PageStatus> => {
    const at = deps.now();
    const written = await db.runTransaction(async (tx) => {
      const jobSnap = await tx.get(jobRef);
      if (!isOpenJob(jobSnap)) {
        tx.delete(pageRef);
        return false;
      }
      tx.set(pageRef, {
        status,
        ...(error ? { error } : {}),
        personCount: people.length,
        storagePath,
        ...(opts.retryCount > 0 ? { retryCount: opts.retryCount } : {}),
        updatedAt: at,
      });
      people.forEach((person, i) => {
        tx.set(db.doc(`${paths.cards}/${pageId}_${i}`), {
          ...person,
          pageId,
          personIndex: i,
          status: 'new',
          matchedCustomerId: null,
          savedCustomerId: null,
          createdAt: at,
        });
      });
      if (opts.countPage) {
        tx.update(jobRef, { pageCount: admin.firestore.FieldValue.increment(1) });
      }
      return true;
    });
    if (!written) {
      await deps.deleteObject(storagePath);
      functions.logger.info('notebook_page_dropped_job_not_open', { uid, jobId, pageId });
      return 'failed';
    }
    functions.logger.info('notebook_page_finished', { uid, jobId, pageId, status, error, personCount: people.length });
    return status;
  };

  if (!opts.skipReserve) {
    const cap = await reservePage(db, uid, deps.now());
    if (!cap.ok) return finish('failed', cap.reason, []);
  }

  try {
    const image = await deps.readImage(storagePath);
    const raw = await deps.vertex.generateJsonFromImage({
      model: NOTEBOOK_MODEL_ID,
      systemPrompt: buildSystemPrompt(),
      imageBase64: image.toString('base64'),
      mimeType: 'image/jpeg',
      responseSchema: RESPONSE_SCHEMA,
    });
    return await finish('done', null, parseExtraction(raw));
  } catch (err) {
    // Never log the message: it can echo model output (names, phones).
    functions.logger.warn('notebook_page_read_failed', {
      uid,
      jobId,
      pageId,
      kind: err instanceof ExtractionParseError ? 'parse' : 'model_or_storage',
    });
    return finish('failed', 'read_failed', []);
  }
}

const STALE_CLAIM_MS = 10 * 60 * 1000;
export const MAX_PAGE_RETRIES = 3;

function millisOf(value: unknown): number | null {
  if (value instanceof Date) return value.getTime();
  if (value && typeof (value as { toMillis?: unknown }).toMillis === 'function') {
    return (value as { toMillis(): number }).toMillis();
  }
  return typeof value === 'number' ? value : null;
}

interface PageDoc {
  status?: string;
  error?: string;
  claimedAt?: unknown;
  retryCount?: number;
}

function isStaleProcessing(page: PageDoc, at: Date): boolean {
  if (page.status !== 'processing') return false;
  const claimedAt = millisOf(page.claimedAt);
  return claimedAt === null || at.getTime() - claimedAt > STALE_CLAIM_MS;
}

type Claim =
  | { ok: true; previous: PageDoc | undefined; retryCount: number }
  | { ok: false; reason: 'not_claimable' | 'retry_limit' };

/**
 * Atomically moves the page to 'processing'. Succeeds when the page is absent
 * (initial) or 'failed' (retry), or when an earlier claim is older than 10
 * minutes (crashed run). Done, failed (initial) or freshly processing pages are
 * not claimed, so concurrent deliveries cannot both run. A retry claim counts
 * itself on the page doc and is refused once MAX_PAGE_RETRIES were used.
 */
async function claimPage(
  deps: ProcessDeps,
  pagePath: string,
  storagePath: string,
  mode: 'initial' | 'retry',
): Promise<Claim> {
  const ref = deps.db.doc(pagePath);
  const at = deps.now();
  return deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const page = snap.exists ? (snap.data() as PageDoc) : undefined;
    let claimable = false;
    if (!page) claimable = mode === 'initial';
    else if (page.status === 'failed') claimable = mode === 'retry';
    else claimable = isStaleProcessing(page, at);
    if (!claimable) return { ok: false, reason: 'not_claimable' } as const;
    const previousRetries = page?.retryCount ?? 0;
    if (mode === 'retry' && previousRetries >= MAX_PAGE_RETRIES) {
      return { ok: false, reason: 'retry_limit' } as const;
    }
    const retryCount = mode === 'retry' ? previousRetries + 1 : previousRetries;
    tx.set(
      ref,
      { status: 'processing', storagePath, claimedAt: at, updatedAt: at, ...(retryCount > 0 ? { retryCount } : {}) },
      { merge: true },
    );
    return { ok: true, previous: page, retryCount } as const;
  });
}

export async function processPageHandler(objectName: string, deps: ProcessDeps): Promise<void> {
  const path = parseNotebookPath(objectName);
  if (!path) return;
  // A page uploaded to a closed or deleted job is dropped: no quota, no docs.
  const jobSnap = await deps.db.doc(pagePaths(path.uid, path.jobId, path.pageId).job).get();
  if (!isOpenJob(jobSnap)) {
    await deps.deleteObject(objectName);
    functions.logger.info('notebook_page_dropped_job_not_open', path);
    return;
  }
  // Duplicate or concurrent delivery must not reserve quota or write cards again.
  const claim = await claimPage(deps, pagePaths(path.uid, path.jobId, path.pageId).page, objectName, 'initial');
  if (!claim.ok) return;
  await processPage(path, objectName, deps, { countPage: true, skipReserve: false, retryCount: claim.retryCount });
}

export async function retryNotebookPageHandler(
  data: { jobId?: unknown; pageId?: unknown },
  context: functions.https.CallableContext,
  deps: ProcessDeps,
): Promise<{ status: string }> {
  const uid = context.auth?.uid;
  if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  const { jobId, pageId } = data;
  if (typeof jobId !== 'string' || typeof pageId !== 'string' || !/^[^/]+$/.test(jobId) || !/^[^/]+$/.test(pageId)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input');
  }
  const paths = pagePaths(uid, jobId, pageId);
  const snap = await deps.db.doc(paths.page).get();
  const page = snap.exists ? (snap.data() as PageDoc) : undefined;
  if (!page || (page.status !== 'failed' && !isStaleProcessing(page, deps.now()))) {
    throw new functions.https.HttpsError('failed-precondition', 'page_not_failed');
  }
  if (!isOpenJob(await deps.db.doc(paths.job).get())) {
    throw new functions.https.HttpsError('failed-precondition', 'job_not_open');
  }
  const storagePath = `users/${uid}/notebookImports/${jobId}/${pageId}.jpg`;
  const claim = await claimPage(deps, paths.page, storagePath, 'retry');
  if (!claim.ok) {
    if (claim.reason === 'retry_limit') {
      throw new functions.https.HttpsError('resource-exhausted', 'retry_limit_reached');
    }
    throw new functions.https.HttpsError('failed-precondition', 'page_not_failed');
  }
  const previous = claim.previous ?? {};
  // A stuck 'processing' page: its run already reserved quota (skip), and it only
  // still needs counting if that run was the first one (no earlier retries).
  const stuck = previous.status === 'processing';
  const status = await processPage({ uid, jobId, pageId }, storagePath, deps, {
    countPage: stuck && (previous.retryCount ?? 0) === 0,
    // A cap failure never reserved quota; every other failure already did.
    skipReserve: stuck || previous.error !== 'page_cap_reached',
    retryCount: claim.retryCount,
  });
  return { status };
}

function prodDeps(): ProcessDeps {
  return {
    db: admin.firestore(),
    readImage: async (p) => (await admin.storage().bucket().file(p).download())[0],
    deleteObject: async (p) => {
      await admin.storage().bucket().file(p).delete({ ignoreNotFound: true });
    },
    vertex: getVertexClient(),
    now: () => new Date(),
  };
}

export const processNotebookPage = functions
  .region('europe-west1')
  .runWith({ memory: '1GB', timeoutSeconds: 120 })
  .storage.object()
  .onFinalize(async (object) => {
    if (!object.name) return;
    await processPageHandler(object.name, prodDeps());
  });

export const retryNotebookPage = functions
  .region('europe-west1')
  .runWith({ memory: '1GB', timeoutSeconds: 120 })
  .https.onCall((data, context) => retryNotebookPageHandler(data, context, prodDeps()));

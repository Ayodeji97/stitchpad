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
  vertex: VertexClient;
  now: () => Date;
}

type PageStatus = 'processing' | 'done' | 'failed';
type PageError = 'page_cap_reached' | 'read_failed';

function pagePaths(uid: string, jobId: string, pageId: string) {
  const job = `users/${uid}/notebookImports/${jobId}`;
  return { job, page: `${job}/pages/${pageId}`, cards: `${job}/cards` };
}

/**
 * Reads one page. `retry` is true when re-running a page that already failed:
 * it never bumps the job's pageCount again, and it only reserves quota when the
 * earlier failure did not already consume a reservation.
 */
async function processPage(
  path: { uid: string; jobId: string; pageId: string },
  storagePath: string,
  deps: ProcessDeps,
  retry: boolean,
  skipReserve: boolean,
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
    const batch = db.batch();
    batch.set(pageRef, {
      status,
      ...(error ? { error } : {}),
      personCount: people.length,
      storagePath,
      updatedAt: at,
    });
    people.forEach((person, i) => {
      batch.set(db.doc(`${paths.cards}/${pageId}_${i}`), {
        ...person,
        pageId,
        personIndex: i,
        status: 'new',
        matchedCustomerId: null,
        savedCustomerId: null,
        createdAt: at,
      });
    });
    if (!retry) {
      batch.set(jobRef, { pageCount: admin.firestore.FieldValue.increment(1) }, { merge: true });
    }
    await batch.commit();
    functions.logger.info('notebook_page_finished', { uid, jobId, pageId, status, error, personCount: people.length });
    return status;
  };

  if (!skipReserve) {
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

function millisOf(value: unknown): number | null {
  if (value instanceof Date) return value.getTime();
  if (value && typeof (value as { toMillis?: unknown }).toMillis === 'function') {
    return (value as { toMillis(): number }).toMillis();
  }
  return typeof value === 'number' ? value : null;
}

/**
 * Atomically moves the page to 'processing'. Succeeds when the page is absent
 * (initial) or 'failed' (retry), or when an earlier claim is older than 10
 * minutes (crashed run). Done, failed (initial) or freshly processing pages are
 * not claimed, so concurrent deliveries cannot both run.
 */
async function claimPage(
  deps: ProcessDeps,
  pagePath: string,
  storagePath: string,
  mode: 'initial' | 'retry',
): Promise<boolean> {
  const ref = deps.db.doc(pagePath);
  const at = deps.now();
  return deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const page = snap.exists ? (snap.data() as { status?: string; claimedAt?: unknown }) : undefined;
    let claimable = false;
    if (!page) claimable = mode === 'initial';
    else if (page.status === 'failed') claimable = mode === 'retry';
    else if (page.status === 'processing') {
      const claimedAt = millisOf(page.claimedAt);
      claimable = claimedAt === null || at.getTime() - claimedAt > STALE_CLAIM_MS;
    }
    if (!claimable) return false;
    tx.set(ref, { status: 'processing', storagePath, claimedAt: at, updatedAt: at }, { merge: true });
    return true;
  });
}

export async function processPageHandler(objectName: string, deps: ProcessDeps): Promise<void> {
  const path = parseNotebookPath(objectName);
  if (!path) return;
  // Duplicate or concurrent delivery must not reserve quota or write cards again.
  if (!(await claimPage(deps, pagePaths(path.uid, path.jobId, path.pageId).page, objectName, 'initial'))) return;
  await processPage(path, objectName, deps, false, false);
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
  const snap = await deps.db.doc(pagePaths(uid, jobId, pageId).page).get();
  const page = snap.exists ? (snap.data() as { status?: string; error?: string; storagePath?: string }) : undefined;
  if (!page || page.status !== 'failed') {
    throw new functions.https.HttpsError('failed-precondition', 'page_not_failed');
  }
  const storagePath = `users/${uid}/notebookImports/${jobId}/${pageId}.jpg`;
  if (!(await claimPage(deps, pagePaths(uid, jobId, pageId).page, storagePath, 'retry'))) {
    throw new functions.https.HttpsError('failed-precondition', 'page_not_failed');
  }
  // A cap failure never reserved quota; every other failure already did.
  const status = await processPage({ uid, jobId, pageId }, storagePath, deps, true, page.error !== 'page_cap_reached');
  return { status };
}

function prodDeps(): ProcessDeps {
  return {
    db: admin.firestore(),
    readImage: async (p) => (await admin.storage().bucket().file(p).download())[0],
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

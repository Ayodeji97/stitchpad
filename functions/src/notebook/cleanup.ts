import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

const DAY_MS = 86_400_000;
const IMAGE_TTL_DAYS = 7;
const TREE_TTL_DAYS = 30;

export interface CleanupDeps {
  db: admin.firestore.Firestore;
  deletePrefix(prefix: string): Promise<void>;
  now: () => Date;
}

type JobSnap = admin.firestore.QueryDocumentSnapshot;

/**
 * Deletes every object under the job's Storage prefix, then stamps
 * imagesDeletedAt (kept as first set, so the stamp marks the first sweep).
 */
async function deleteImages(job: JobSnap, deps: CleanupDeps): Promise<void> {
  await deps.deletePrefix(`${job.ref.path}/`);
  if (job.data().imagesDeletedAt) return;
  await job.ref.set(
    { imagesDeletedAt: admin.firestore.Timestamp.fromDate(deps.now()) },
    { merge: true },
  );
}

/**
 * Daily: images go once a job is saved/abandoned or 7 days old (docs stay so
 * the review can still be read), and every job 7+ days old is swept again on
 * each run; the whole job tree goes at 30 days. One bad
 * job never stops the rest. Logs carry ids and counts only, never content.
 */
export async function cleanupHandler(deps: CleanupDeps): Promise<void> {
  const nowMs = deps.now().getTime();
  const ts = (ms: number) => admin.firestore.Timestamp.fromMillis(ms);
  const jobs = () => deps.db.collectionGroup('notebookImports');

  const [expired, finished, stale] = await Promise.all([
    jobs().where('createdAt', '<=', ts(nowMs - TREE_TTL_DAYS * DAY_MS)).get(),
    jobs().where('status', 'in', ['saved', 'abandoned']).get(),
    jobs().where('createdAt', '<=', ts(nowMs - IMAGE_TTL_DAYS * DAY_MS)).get(),
  ]);

  let imagesCleared = 0;
  let treesDeleted = 0;
  let failures = 0;
  const handled = new Set<string>();

  for (const job of expired.docs) {
    handled.add(job.ref.path);
    try {
      // Always sweep the prefix: an image may have landed after imagesDeletedAt was set.
      await deps.deletePrefix(`${job.ref.path}/`);
      await deps.db.recursiveDelete(job.ref);
      treesDeleted++;
    } catch (e) {
      failures++;
      functions.logger.error('notebook cleanup: tree delete failed', { jobId: job.id, error: (e as Error).message });
    }
  }

  // Jobs 7+ days old are swept on every run even when already stamped: an
  // upload can land after the stamp, and an empty prefix sweep is cheap.
  const needImages = [...stale.docs, ...finished.docs.filter((j) => !j.data().imagesDeletedAt)];
  for (const job of needImages) {
    if (handled.has(job.ref.path)) continue;
    handled.add(job.ref.path);
    try {
      await deleteImages(job, deps);
      imagesCleared++;
    } catch (e) {
      failures++;
      functions.logger.error('notebook cleanup: image delete failed', { jobId: job.id, error: (e as Error).message });
    }
  }

  functions.logger.info('notebook cleanup done', { imagesCleared, treesDeleted, failures });
}

export const cleanupNotebookImports = functions
  .region('europe-west1')
  .pubsub.schedule('15 3 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    const bucket = admin.storage().bucket();
    await cleanupHandler({
      db: admin.firestore(),
      deletePrefix: async (prefix) => {
        await bucket.deleteFiles({ prefix });
      },
      now: () => new Date(),
    });
  });

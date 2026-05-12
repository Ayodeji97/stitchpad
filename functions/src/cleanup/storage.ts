import * as functions from 'firebase-functions/v1';
import type { Bucket } from '@google-cloud/storage';

/**
 * Delete every Cloud Storage object under users/<uid>/.
 *
 * Best-effort: a failure here is logged but never thrown, so the surrounding
 * Promise.allSettled in index.ts can still report success and the function
 * never auto-retries (see spec for the rationale).
 */
export async function deleteUserStorageData(
  uid: string,
  bucket: Bucket,
): Promise<void> {
  try {
    await bucket.deleteFiles({ prefix: `users/${uid}/` });
  } catch (error) {
    functions.logger.error('storage cleanup failed', {
      uid,
      error: serialiseError(error),
    });
  }
}

function serialiseError(error: unknown): unknown {
  return error instanceof Error
    ? { name: error.name, message: error.message, stack: error.stack }
    : error;
}

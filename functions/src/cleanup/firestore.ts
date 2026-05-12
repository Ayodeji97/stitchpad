import * as functions from 'firebase-functions/v1';
import type { Firestore } from 'firebase-admin/firestore';

/**
 * Subcollections under users/<uid>/ that this function deletes.
 * Explicit allow-list (not runtime discovery) so accidental data writes
 * under users/<uid>/<unexpected>/ don't silently get nuked. New subcollections
 * must be added here AND logged via the drift-warning code (added in Task 5).
 */
export const ALLOWED_SUBCOLLECTIONS = [
  'customers',
  'orders',
  'measurements',
  'styles',
  'goals',
] as const;

export async function deleteUserFirestoreData(
  uid: string,
  db: Firestore,
): Promise<void> {
  const userDocRef = db.collection('users').doc(uid);

  for (const sub of ALLOWED_SUBCOLLECTIONS) {
    try {
      await db.recursiveDelete(userDocRef.collection(sub));
    } catch (error) {
      functions.logger.error('firestore subcollection cleanup failed', {
        uid,
        subcollection: sub,
        error: serialiseError(error),
      });
    }
  }

  try {
    await userDocRef.delete();
  } catch (error) {
    functions.logger.error('firestore user doc cleanup failed', {
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

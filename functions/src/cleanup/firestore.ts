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

  // Drift warning: surface subcollections not in the allow-list, but do NOT
  // auto-delete them. An accidental write under users/<uid>/<unexpected>/
  // should never silently nuke data; the operator must consciously update
  // the allow-list (or clean up the bad write).
  try {
    const found = await userDocRef.listCollections();
    for (const sub of found) {
      if (
        !ALLOWED_SUBCOLLECTIONS.includes(
          sub.id as typeof ALLOWED_SUBCOLLECTIONS[number],
        )
      ) {
        functions.logger.warn(
          'unexpected subcollection found under users/{uid}; not cleaned up',
          {
            uid,
            unexpectedSubcollection: sub.id,
            hint: 'update onAuthUserDeleted allow-list',
          },
        );
      }
    }
  } catch (error) {
    functions.logger.error('subcollection drift check failed', {
      uid,
      error: serialiseError(error),
    });
  }

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

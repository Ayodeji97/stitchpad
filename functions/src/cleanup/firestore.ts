import * as functions from 'firebase-functions/v1';
import type { Firestore } from 'firebase-admin/firestore';

/**
 * DIRECT subcollections of users/<uid>/ that this function deletes.
 * Explicit allow-list (not runtime discovery) so accidental data writes
 * under users/<uid>/<unexpected>/ don't silently get nuked.
 *
 * Admin SDK's recursiveDelete cascades through ALL nested subcollections under
 * the target collection. So deeper paths like:
 *   users/<uid>/customers/<cid>/styles
 *   users/<uid>/customers/<cid>/measurements
 * are deleted transitively when 'customers' is recursively deleted. They are
 * NOT listed here because they don't exist as direct children of users/<uid>/.
 *
 * If you add a new DIRECT subcollection of users/<uid>/, append it here.
 */
export const ALLOWED_SUBCOLLECTIONS = [
  'customers',
  'goals',
  'notifications',
  'orders',
  // Server-only per-user state (e.g. emailThrottle written by
  // sendVerificationEmail). Never client-readable; swept on account deletion.
  'private',
  // Paystack prepaid checkout/payment records (server-written). Swept on account
  // deletion so payment references/status don't outlive the account.
  'billingTransactions',
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

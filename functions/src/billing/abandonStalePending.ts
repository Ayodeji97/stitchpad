import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

const REGION = 'europe-west1';
const HOUR_MS = 60 * 60 * 1000;

/**
 * How long a checkout may sit `pending` before it's considered abandoned. 120h =
 * 5 working days — long enough that a delayed Paystack bank-transfer / USSD
 * settlement (which fires charge.success late) is never prematurely flagged, and
 * that an operator has time to verify a genuinely-pending payment before cleanup.
 */
export const STALE_PENDING_MS = 120 * HOUR_MS;

export interface AbandonStaleDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export interface AbandonStaleResult {
  scanned: number;
  abandoned: number;
}

/**
 * Marks `pending` billingTransactions older than STALE_PENDING_MS as `abandoned`.
 *
 * We MARK rather than delete: the webhook's idempotency guard only short-circuits
 * on `appliedAt` / `status === 'paid'`, so a late charge.success on an abandoned
 * doc still applies the upgrade and flips it to `paid`. Deleting would lose that
 * doc and silently drop a late payment. Account deletion still sweeps the whole
 * subcollection separately.
 */
export async function abandonStalePendingCheckoutsHandler(
  deps: AbandonStaleDeps,
): Promise<AbandonStaleResult> {
  const cutoff = admin.firestore.Timestamp.fromDate(new Date(deps.now().getTime() - STALE_PENDING_MS));
  const snap = await deps.db.collectionGroup('billingTransactions')
    .where('status', '==', 'pending')
    .where('createdAt', '<=', cutoff)
    .get();

  const BATCH_LIMIT = 500;
  for (let i = 0; i < snap.docs.length; i += BATCH_LIMIT) {
    const batch = deps.db.batch();
    for (const doc of snap.docs.slice(i, i + BATCH_LIMIT)) {
      batch.set(doc.ref, {
        status: 'abandoned',
        failureReason: 'checkout_abandoned',
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }
    await batch.commit();
  }

  const result: AbandonStaleResult = { scanned: snap.docs.length, abandoned: snap.docs.length };
  functions.logger.info('abandon stale pending checkouts complete', { ...result });
  return result;
}

export const abandonStalePendingCheckouts = functions
  .region(REGION)
  // 02:00 Africa/Lagos daily (cron, so the timeZone is honored — interval
  // schedules ignore it). Off-peak, after the 01:00 expiry sweep.
  .pubsub.schedule('0 2 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await abandonStalePendingCheckoutsHandler({ db: admin.firestore(), now: () => new Date() });
  });

import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, REFERRALS } from './referralConstants';
import { clawbackReferralOnDelete } from './clawback';

// sweepDeletedReferralUsers — the safety net behind onAuthUserDeleted's clawback.
//
// onAuthUserDeleted reverses an unpaid bounty when a referred user deletes their
// account, but it's a one-shot trigger with no retry: a transient Firestore error
// there is only logged, and a `confirmed` (past-hold) payout would then keep a
// real-money bounty for a user who is gone. confirmReferralPayouts already
// backstops `pending` payouts (it refuses to release one whose user doc is
// missing), but nothing re-checks `confirmed` ones. This nightly sweep closes
// that gap: for every still-owed (pending/confirmed) referral whose users/{uid}
// doc no longer exists, it runs the same clawback — idempotent, so a payout
// already reversed is a no-op.

export interface SweepDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export interface SweepResult {
  scanned: number;
  clawedBack: number;
  failed: number;
}

export async function sweepDeletedReferralUsersHandler(
  deps: SweepDeps,
): Promise<SweepResult> {
  const { db } = deps;
  // Only referrals with money still owed. Single-field `in` — no composite index.
  const snap = await db
    .collection(REFERRALS)
    .where('payoutState', 'in', ['pending', 'confirmed'])
    .get();

  let clawedBack = 0;
  let failed = 0;

  for (const doc of snap.docs) {
    const uid = doc.id;
    try {
      // A qualified/payable referral is always activated, so its user doc exists
      // unless the account was deleted. Skip the (common) live-account case cheaply.
      if ((await db.doc(`users/${uid}`).get()).exists) continue;
      const outcome = await clawbackReferralOnDelete(uid, db, deps.now);
      if (outcome === 'clawed_back') clawedBack++;
    } catch (error) {
      // Per-referral isolation — one failure must not abort the sweep.
      failed++;
      functions.logger.error('sweepDeletedReferralUsers: referral failed, continuing', {
        uid,
        error: error instanceof Error ? { name: error.name, message: error.message } : error,
      });
    }
  }

  const result: SweepResult = { scanned: snap.size, clawedBack, failed };
  functions.logger.info('sweepDeletedReferralUsers complete', { ...result });
  return result;
}

// ── Exports: nightly schedule + admin-only debug trigger ─────────────────────

export const sweepDeletedReferralUsers = functions
  .region(REGION)
  // 04:15 Africa/Lagos — just after confirmReferralPayouts (04:00), so a payout
  // confirmed tonight is still swept the same night if its user is already gone.
  .pubsub.schedule('15 4 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await sweepDeletedReferralUsersHandler({ db: admin.firestore(), now: () => new Date() });
  });

/** Admin-only manual trigger (same handler), for QA + backfills. */
export const debugSweepDeletedReferralUsers = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<SweepResult> => {
    if (context.auth?.token?.admin !== true) {
      throw new functions.https.HttpsError('permission-denied', 'admin_only');
    }
    return sweepDeletedReferralUsersHandler({ db: admin.firestore(), now: () => new Date() });
  });

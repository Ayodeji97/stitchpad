import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, REFERRALS, MARKETERS } from './referralConstants';
import type { PayoutState, ReferralFlag } from './referralConstants';

// confirmReferralPayouts — the second half of the payout lifecycle. Slice 6's
// grader opens a qualified referral's payout as `pending` with a `holdEndsAt`
// ~7 days out; that hold gives fraud checks + account-deletion clawback time to
// fire. This nightly job releases holds that have expired:
//
//   pending ──(hold expired, still clean)──▶ confirmed   (money: pending → confirmed)
//   pending ──(hold expired, a flag appeared)──▶ rejected (money: pending → reversed)
//
// A confirmed payout is later marked `paid` by the admin (Slice 9) after the
// offline bank transfer. Money lives in kobo on the marketer aggregates.

/** payoutRejectedReason written when a flagged referral is refused at hold-release. */
export const REJECT_FLAGGED_DURING_HOLD = 'flagged_during_hold';

export type HoldDecision = 'confirm' | 'reject';

/**
 * What to do with a held payout once its hold has expired. Only `pending`
 * referrals are actionable; a clean one confirms, a flagged one is refused so its
 * money never leaves `pendingAmount` limbo. Pure + fully unit-testable.
 */
export function decideHoldRelease(payoutState: PayoutState, hasFlags: boolean): HoldDecision | null {
  if (payoutState !== 'pending') return null;
  return hasFlags ? 'reject' : 'confirm';
}

export interface ConfirmPayoutsDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export interface ConfirmPayoutsResult {
  scanned: number;
  confirmed: number;
  rejected: number;
}

export async function confirmReferralPayoutsHandler(
  deps: ConfirmPayoutsDeps,
): Promise<ConfirmPayoutsResult> {
  const { db } = deps;
  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());

  // Pending payouts whose hold has elapsed. Pre-indexed by the
  // (payoutState, holdEndsAt) composite in firestore.indexes.json.
  const snap = await db
    .collection(REFERRALS)
    .where('payoutState', '==', 'pending')
    .where('holdEndsAt', '<=', nowTs)
    .get();

  let confirmed = 0;
  let rejected = 0;

  for (const doc of snap.docs) {
    // Re-read the racy payoutState/flags inside the transaction so a concurrent
    // run, a debug call, or a mid-run clawback can't double-move the money.
    const decision = await db.runTransaction(async (tx) => {
      const fresh = await tx.get(doc.ref);
      if (!fresh.exists) return null;
      const f = fresh.data() as {
        payoutState: PayoutState;
        flags?: ReferralFlag[];
        marketerId: string;
        payoutAmount?: number;
      };
      const release = decideHoldRelease(f.payoutState, (f.flags?.length ?? 0) > 0);
      if (!release) return null;

      const marketerRef = db.doc(`${MARKETERS}/${f.marketerId}`);
      const m = (await tx.get(marketerRef)).data() ?? {};
      const amount = f.payoutAmount ?? 0;
      const pendingAmount = ((m.pendingAmount as number) ?? 0) - amount;

      if (release === 'confirm') {
        tx.set(doc.ref, { payoutState: 'confirmed', updatedAt: nowTs }, { merge: true });
        tx.set(marketerRef, {
          pendingAmount,
          confirmedAmount: ((m.confirmedAmount as number) ?? 0) + amount,
          updatedAt: nowTs,
        }, { merge: true });
      } else {
        tx.set(doc.ref, {
          payoutState: 'rejected',
          payoutRejectedReason: REJECT_FLAGGED_DURING_HOLD,
          updatedAt: nowTs,
        }, { merge: true });
        tx.set(marketerRef, { pendingAmount, updatedAt: nowTs }, { merge: true });
      }
      return release;
    });

    if (decision === 'confirm') confirmed++;
    else if (decision === 'reject') rejected++;
  }

  const result: ConfirmPayoutsResult = { scanned: snap.size, confirmed, rejected };
  functions.logger.info('confirmReferralPayouts complete', { ...result });
  return result;
}

// ── Exports: nightly schedule + admin-only debug trigger ─────────────────────

export const confirmReferralPayouts = functions
  .region(REGION)
  // 04:00 Africa/Lagos daily — after reconcileReferrals (03:30) has opened any
  // new pending payouts for the day.
  .pubsub.schedule('0 4 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await confirmReferralPayoutsHandler({ db: admin.firestore(), now: () => new Date() });
  });

/** Admin-only manual trigger (same handler), for QA + backfills. */
export const debugConfirmReferralPayouts = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<ConfirmPayoutsResult> => {
    if (context.auth?.token?.admin !== true) {
      throw new functions.https.HttpsError('permission-denied', 'admin_only');
    }
    return confirmReferralPayoutsHandler({ db: admin.firestore(), now: () => new Date() });
  });

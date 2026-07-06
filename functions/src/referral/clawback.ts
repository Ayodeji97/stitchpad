import * as admin from 'firebase-admin';
import type { Firestore } from 'firebase-admin/firestore';
import { REFERRALS, MARKETERS } from './referralConstants';
import type { PayoutState } from './referralConstants';

// Account-deletion clawback. When a referred user deletes their account, any
// UNPAID payout owed for them is reversed — a marketer isn't paid a bounty for a
// user who is no longer here (the churn/fraud signal the ~7-day hold exists to
// catch). Wired into onAuthUserDeleted alongside the user-data cleanup.
//
// referrals/{uid} is TOP-LEVEL (not under users/{uid}), so it survives the
// user-doc recursive delete on purpose: we KEEP the record and mark it
// `rejected` (audit trail + idempotency) rather than deleting it.
//
// Only `pending`/`confirmed` are reversible here — `paid` money already left via
// an offline bank transfer and needs manual recovery, so it's left as-is. The
// marketer's lifetime funnel counters (installs/activated/qualified) are NOT
// decremented; they record what was reached, while the money aggregates reflect
// what is currently owed.

/** payoutRejectedReason written when a payout is clawed back on account deletion. */
export const REJECT_ACCOUNT_DELETED = 'account_deleted';

export type ClawbackOutcome = 'clawed_back' | 'none';

const REVERSIBLE: Record<string, 'pendingAmount' | 'confirmedAmount'> = {
  pending: 'pendingAmount',
  confirmed: 'confirmedAmount',
};

export async function clawbackReferralOnDelete(
  uid: string,
  db: Firestore,
  now: () => Date = () => new Date(),
): Promise<ClawbackOutcome> {
  const referralRef = db.doc(`${REFERRALS}/${uid}`);
  // Cheap pre-check outside the transaction — most deleted accounts were never
  // referred, or their payout isn't in a reversible state.
  const pre = (await referralRef.get()).data() as { payoutState?: PayoutState } | undefined;
  if (!pre || !REVERSIBLE[pre.payoutState ?? '']) return 'none';

  const nowTs = admin.firestore.Timestamp.fromDate(now());
  return db.runTransaction(async (tx) => {
    const fresh = await tx.get(referralRef);
    const f = fresh.data() as
      | { payoutState?: PayoutState; marketerId?: string; payoutAmount?: number }
      | undefined;
    const field = f ? REVERSIBLE[f.payoutState ?? ''] : undefined;
    if (!f || !field) return 'none'; // state changed under us (e.g. just paid)

    const marketerRef = db.doc(`${MARKETERS}/${f.marketerId}`);
    const m = (await tx.get(marketerRef)).data() ?? {};
    const amount = f.payoutAmount ?? 0;

    tx.set(referralRef, {
      payoutState: 'rejected',
      payoutRejectedReason: REJECT_ACCOUNT_DELETED,
      updatedAt: nowTs,
    }, { merge: true });
    tx.set(marketerRef, {
      [field]: ((m[field] as number) ?? 0) - amount,
      updatedAt: nowTs,
    }, { merge: true });
    return 'clawed_back';
  });
}

import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, REFERRALS, MARKETERS } from './referralConstants';
import type { PayoutState } from './referralConstants';
import { subtractKobo, addKobo } from './marketerBalance';

// markReferralPaid — the final step of the payout lifecycle, and the only WRITE
// on the admin dashboard. After the admin sends a marketer their confirmed total
// by offline bank transfer, they mark it paid: every `confirmed` referral for
// that marketer flips to `paid`, and the money moves confirmedAmount → paidAmount
// on the marketer aggregate.
//
//   confirmed ──(admin marks paid, per marketer)──▶ paid
//
// Admin-gated (Firebase Auth `admin: true`). Idempotent + per-referral
// transactional (re-reads the racy payoutState), so a double-click or a retry
// never moves the money twice. `paid` is terminal — nothing (not even clawback)
// reverses it, since the funds have physically left.

// marketerId is interpolated into a doc path, so constrain it to the id charset
// createMarketer mints (`mkt_<ts>_<hex>`) — never let a '/' or empty value through.
const MARKETER_ID_RE = /^[A-Za-z0-9_-]{1,128}$/;

export interface MarkReferralPaidRequest {
  marketerId?: unknown;
}

export interface MarkReferralPaidResponse {
  marketerId: string;
  paidCount: number;
  paidAmount: number;
}

export interface MarkPaidDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export async function markReferralPaidHandler(
  data: MarkReferralPaidRequest,
  context: functions.https.CallableContext,
  deps: MarkPaidDeps,
): Promise<MarkReferralPaidResponse> {
  if (context.auth?.token?.admin !== true) {
    throw new functions.https.HttpsError('permission-denied', 'admin_only');
  }
  const marketerId = typeof data.marketerId === 'string' ? data.marketerId : '';
  if (!MARKETER_ID_RE.test(marketerId)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_marketer_id');
  }

  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  // All confirmed payouts, filtered to this marketer in memory. `confirmed` is a
  // transient, small set (unpaid-but-approved), so no (marketerId, payoutState)
  // composite index is needed.
  const snap = await deps.db.collection(REFERRALS).where('payoutState', '==', 'confirmed').get();
  const mine = snap.docs.filter((d) => (d.data() as { marketerId?: string }).marketerId === marketerId);

  let paidCount = 0;
  let paidAmount = 0;

  for (const doc of mine) {
    // Re-read the racy payoutState inside the transaction so a concurrent
    // clawback/sweep (confirmed → rejected) or a double-click can't pay twice.
    const applied = await deps.db.runTransaction(async (tx) => {
      const fresh = await tx.get(doc.ref);
      const f = fresh.data() as { payoutState?: PayoutState; marketerId?: string; payoutAmount?: number } | undefined;
      if (!f || f.payoutState !== 'confirmed' || f.marketerId !== marketerId) return 0;

      const marketerRef = deps.db.doc(`${MARKETERS}/${marketerId}`);
      const m = (await tx.get(marketerRef)).data() ?? {};
      const amount = f.payoutAmount ?? 0;

      tx.set(doc.ref, { payoutState: 'paid', paidAt: nowTs, updatedAt: nowTs }, { merge: true });
      tx.set(marketerRef, {
        confirmedAmount: subtractKobo(m.confirmedAmount, amount),
        paidAmount: addKobo(m.paidAmount, amount),
        updatedAt: nowTs,
      }, { merge: true });
      return amount;
    });

    if (applied > 0) {
      paidCount += 1;
      paidAmount += applied;
    }
  }

  const result: MarkReferralPaidResponse = { marketerId, paidCount, paidAmount };
  functions.logger.info('markReferralPaid complete', { ...result });
  return result;
}

export const markReferralPaid = functions
  .region(REGION)
  .https.onCall(async (data, context): Promise<MarkReferralPaidResponse> =>
    markReferralPaidHandler(data as MarkReferralPaidRequest, context, {
      db: admin.firestore(),
      now: () => new Date(),
    }));

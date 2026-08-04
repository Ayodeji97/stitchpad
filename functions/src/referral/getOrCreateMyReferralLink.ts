import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
import { generateCode } from '../billing/giftBilling';
import {
  REGION,
  MARKETERS,
  REFERRAL_CODES,
  REFERRAL_CODE_LENGTH,
  REFERRAL_LINK_BASE,
  PLAY_PACKAGE,
} from './referralConstants';
import type { ReferrerType, PayoutKind, MarketerStatus } from './referralConstants';

// getOrCreateMyReferralLink — self-serve mint of a Founding Tailors outbound
// referral link. Unlike admin-onboarded marketers (marketerAdmin.ts), this is
// reachable by any authenticated user and mints a PAYOUT-DISABLED user-type
// marketer (payoutRatePerUser: 0) tagged with the `founding_tailors` program —
// this is a leaderboard/recognition mechanism, never a cash-payout path.
// Idempotent on users/{uid}.referralCode: a user gets exactly one outbound code.

export const FOUNDING_TAILORS_PROGRAM = 'founding_tailors';
const MAX_CODE_ATTEMPTS = 5;

export interface MyReferralLinkResponse { code: string; url: string; playUrl: string }
export interface MyReferralLinkDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  randomCode: () => string;
  randomId: () => string;
}

export const getOrCreateMyReferralLink = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<MyReferralLinkResponse> =>
    getOrCreateMyReferralLinkHandler(_data, context, {
      db: admin.firestore(),
      now: () => new Date(),
      randomCode: () => generateCode(REFERRAL_CODE_LENGTH),
      randomId: () => crypto.randomBytes(6).toString('hex'),
    }));

export async function getOrCreateMyReferralLinkHandler(
  _data: unknown,
  context: functions.https.CallableContext,
  deps: MyReferralLinkDeps,
): Promise<MyReferralLinkResponse> {
  const uid = context.auth?.uid;
  if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');

  const userRef = deps.db.doc(`users/${uid}`);
  const user = (await userRef.get()).data() as
    | { referralCode?: string; displayName?: string; businessName?: string; email?: string }
    | undefined;

  // Idempotent: a user already has exactly one outbound code.
  if (user?.referralCode) return linkFor(user.referralCode);

  const name = (user?.businessName?.trim() || user?.displayName?.trim() || 'Tailor');
  const email = (user?.email ?? '').toLowerCase();
  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const marketerId = `mkt_${deps.now().getTime()}_${deps.randomId()}`;

  let code = '';
  for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt += 1) {
    const candidate = deps.randomCode();
    const claimed = await deps.db.runTransaction(async (tx) => {
      const codeRef = deps.db.doc(`${REFERRAL_CODES}/${candidate}`);
      const freshUser = await tx.get(userRef);
      if ((freshUser.data() as { referralCode?: string } | undefined)?.referralCode) return false; // race: already minted
      if ((await tx.get(codeRef)).exists) return false;
      tx.set(deps.db.doc(`${MARKETERS}/${marketerId}`), {
        name,
        email,
        phone: null,
        type: 'user' as ReferrerType,
        program: FOUNDING_TAILORS_PROGRAM,
        referrerUid: uid,
        code: candidate,
        payoutRatePerUser: 0, // payout-disabled: leaderboard only, never queues cash
        payoutKind: 'credit' as PayoutKind,
        bankName: null,
        bankAccountName: null,
        bankAccountNumber: null,
        status: 'active' as MarketerStatus,
        installs: 0,
        activated: 0,
        qualified: 0,
        pendingAmount: 0,
        confirmedAmount: 0,
        paidAmount: 0,
        createdAt: nowTs,
        updatedAt: nowTs,
      });
      tx.set(codeRef, { marketerId, createdAt: nowTs });
      tx.set(userRef, { referralCode: candidate, updatedAt: nowTs }, { merge: true });
      return true;
    });
    if (claimed) {
      code = candidate;
      break;
    }
    // If the race path returned false because another mint won, re-read and return it.
    const reUser = (await userRef.get()).data() as { referralCode?: string } | undefined;
    if (reUser?.referralCode) return linkFor(reUser.referralCode);
  }
  if (!code) throw new functions.https.HttpsError('internal', 'code_generation_failed');
  return linkFor(code);
}

function linkFor(code: string): MyReferralLinkResponse {
  return {
    code,
    url: `${REFERRAL_LINK_BASE}/${code}`,
    playUrl:
      `https://play.google.com/store/apps/details?id=${PLAY_PACKAGE}` +
      `&referrer=${encodeURIComponent(`ref=${code}`)}`,
  };
}

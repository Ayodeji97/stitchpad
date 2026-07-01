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

// createMarketer — admin-only onboarding of a referral marketer. Mints a unique
// referral code and writes marketers/{id} + referralCodes/{code} race-safely
// (mirrors createGiftLink's transactional mint). Returns the shareable links.
//
// Gated by the Firebase Auth `admin: true` custom claim (set via the one-off
// scripts/setAdminClaim.js). No client path can reach this without that claim.

export interface CreateMarketerRequest {
  name?: unknown;
  email?: unknown;
  phone?: unknown;
  type?: unknown; // 'affiliate' (default) | 'user' (phase 2)
  referrerUid?: unknown; // required iff type === 'user'
  payoutRatePerUser?: unknown; // integer kobo, > 0
  payoutKind?: unknown; // 'cash' (default) | 'credit'
  bankName?: unknown;
  bankAccountName?: unknown;
  bankAccountNumber?: unknown;
}

export interface CreateMarketerResponse {
  marketerId: string;
  code: string;
  url: string; // referral link (link.getstitchpad.com/r/CODE)
  playUrl: string; // Play listing carrying the Install Referrer payload
}

export interface CreateMarketerDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  randomCode: () => string;
  randomId: () => string;
}

const MAX_CODE_ATTEMPTS = 5;

export const createMarketer = functions
  .region(REGION)
  .https.onCall(async (data, context): Promise<CreateMarketerResponse> => {
    return createMarketerHandler(data as CreateMarketerRequest, context, {
      db: admin.firestore(),
      now: () => new Date(),
      randomCode: () => generateCode(REFERRAL_CODE_LENGTH),
      randomId: () => crypto.randomBytes(6).toString('hex'),
    });
  });

export async function createMarketerHandler(
  data: CreateMarketerRequest,
  context: functions.https.CallableContext,
  deps: CreateMarketerDeps,
): Promise<CreateMarketerResponse> {
  requireAdmin(context);

  const name = asNonEmptyString(data.name);
  if (!name) throw invalid('missing_name');
  const email = asEmail(data.email);
  if (!email) throw invalid('missing_or_invalid_email');
  const type = parseReferrerType(data.type);
  const payoutKind = parsePayoutKind(data.payoutKind);
  const payoutRatePerUser = asPositiveInt(data.payoutRatePerUser);
  if (payoutRatePerUser === null) throw invalid('invalid_payout_rate');
  const phone = asNonEmptyString(data.phone);

  // Affiliates are paid by offline bank transfer, so bank details are required
  // up front. User-referrers (phase 2) take app credit and carry a referrerUid.
  let bankName: string | null = null;
  let bankAccountName: string | null = null;
  let bankAccountNumber: string | null = null;
  let referrerUid: string | null = null;
  if (type === 'affiliate') {
    if (payoutKind !== 'cash') throw invalid('affiliate_requires_cash');
    bankName = asNonEmptyString(data.bankName);
    bankAccountName = asNonEmptyString(data.bankAccountName);
    bankAccountNumber = asAccountNumber(data.bankAccountNumber);
    if (!bankName || !bankAccountName || !bankAccountNumber) throw invalid('missing_bank_details');
  } else {
    // User-referrers (phase 2) are rewarded with app credit — we never collect
    // their bank details — so a cash payout would leave an inconsistent record
    // (payoutKind: 'cash' with null bank fields). Require credit, symmetric to
    // the affiliate_requires_cash rule above.
    if (payoutKind !== 'credit') throw invalid('user_requires_credit');
    referrerUid = asNonEmptyString(data.referrerUid);
    if (!referrerUid) throw invalid('missing_referrer_uid');
  }

  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const marketerId = `mkt_${deps.now().getTime()}_${deps.randomId()}`;

  // Mint a unique code, retrying on the (rare) collision with an existing one.
  // The marketer doc is written only inside the winning transaction.
  let code = '';
  for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt += 1) {
    const candidate = deps.randomCode();
    const claimed = await deps.db.runTransaction(async (tx) => {
      const codeRef = deps.db.doc(`${REFERRAL_CODES}/${candidate}`);
      if ((await tx.get(codeRef)).exists) return false; // read before write
      tx.set(deps.db.doc(`${MARKETERS}/${marketerId}`), {
        name,
        email,
        phone: phone ?? null,
        type,
        referrerUid,
        code: candidate,
        payoutRatePerUser,
        payoutKind,
        bankName,
        bankAccountName,
        bankAccountNumber,
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
      return true;
    });
    if (claimed) {
      code = candidate;
      break;
    }
  }
  if (!code) throw new functions.https.HttpsError('internal', 'code_generation_failed');

  return {
    marketerId,
    code,
    url: `${REFERRAL_LINK_BASE}/${code}`,
    playUrl:
      `https://play.google.com/store/apps/details?id=${PLAY_PACKAGE}` +
      `&referrer=${encodeURIComponent(`ref=${code}`)}`,
  };
}

// ── Validation helpers ───────────────────────────────────────────────────────

function requireAdmin(context: functions.https.CallableContext): void {
  if (context.auth?.token?.admin !== true) {
    throw new functions.https.HttpsError('permission-denied', 'admin_only');
  }
}

function invalid(message: string): functions.https.HttpsError {
  return new functions.https.HttpsError('invalid-argument', message);
}

function asNonEmptyString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
function asEmail(value: unknown): string | null {
  const s = asNonEmptyString(value);
  return s && EMAIL_RE.test(s) ? s.toLowerCase() : null;
}

function asPositiveInt(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null;
}

// Nigerian NUBAN account numbers are exactly 10 digits.
function asAccountNumber(value: unknown): string | null {
  const s = asNonEmptyString(value);
  return s && /^\d{10}$/.test(s) ? s : null;
}

function parseReferrerType(value: unknown): ReferrerType {
  if (value === undefined || value === null || value === 'affiliate') return 'affiliate';
  if (value === 'user') return 'user';
  throw invalid('invalid_type');
}

function parsePayoutKind(value: unknown): PayoutKind {
  if (value === undefined || value === null || value === 'cash') return 'cash';
  if (value === 'credit') return 'credit';
  throw invalid('invalid_payout_kind');
}

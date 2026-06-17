/**
 * Gift subscriptions. Anyone can pay for a tailor's Pro/Atelier plan; the entitlement
 * lands on a DIFFERENT uid than the payer. Two flows:
 *
 *   - gift_me: a tailor shares their personal link (carrying an opaque token that
 *     resolves to their uid). On charge.success the gift AUTO-APPLIES to that uid.
 *   - public:  a gifter names a recipient by email. On charge.success we mint a
 *     bearer CODE, email the recipient a claim link + code, and they redeem in-app.
 *
 * Money/entitlement logic mirrors paystackBilling.ts: deps-injected handlers (pure +
 * testable), idempotent transactions, all work before res.send, 500 only on
 * RETRYABLE failures. Period/stacking math is shared via computeSubscriptionGrant.
 *
 * Gift charges ride the EXISTING paystackWebhook (routed by metadata.purpose) so no
 * second webhook URL needs registering — see applyGiftWebhook, called from
 * paystackWebhookHandler.
 */

import * as crypto from 'crypto';
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  BillingTier,
  BillingCadence,
  PaystackInitializeResponse,
  parseTier,
  parseCadence,
  priceFor,
} from './paystackBilling';
import { computeSubscriptionGrant, addYears, toDate } from './subscriptionPeriod';
import type { CurrentSubscription, SubscriptionGrant } from './subscriptionPeriod';
import { buildGiftReceivedEmail, buildGiftClaimEmail } from './giftEmailTemplate';
import { sendResendEmail, ResendError } from '../email/resendClient';

const REGION = 'europe-west1';
const CURRENCY = 'NGN';

/** Public web origin for the "Gift me" link (resolved by getPublicGiftProfile). */
const GIFT_LINK_BASE = 'https://getstitchpad.com/gift';
/**
 * Recipient claim link. https Universal Link / App Link (NOT a custom scheme) so it
 * survives Gmail's iOS app. Kept in sync with applinks:link.getstitchpad.com and the
 * Android /claim intent-filter; the app reads ?code= and routes to RedeemGiftRoute.
 */
const CLAIM_LINK_BASE = 'https://link.getstitchpad.com/claim';

/** Crockford-ish alphabet minus 0/O/1/I/L so codes are unambiguous to read/type. */
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

/** Max periods a single gift can buy, per cadence (12 months or 5 years). Caps abuse / typo'd amounts. */
const MAX_QUANTITY: Record<BillingCadence, number> = { monthly: 12, annual: 5 };

/** Give up resending a public gift's claim email after this many failed attempts. */
const MAX_CLAIM_EMAIL_ATTEMPTS = 5;

export type GiftFlow = 'gift_me' | 'public';
export type GiftStatus = 'pending' | 'paid' | 'claimed' | 'expired' | 'failed';

export interface InitializeGiftRequest {
  token?: unknown;          // gift_me
  recipientEmail?: unknown; // public
  tier?: unknown;
  cadence?: unknown;
  quantity?: unknown;       // how many periods (months if monthly, years if annual); default 1
  gifterName?: unknown;
  gifterEmail?: unknown;
  note?: unknown;
}

export interface InitializeGiftResponse {
  authorizationUrl: string;
  reference: string;
  code?: string; // public flow only — shown to the gifter to share directly
}

interface GiftPaystackInitializeRequest {
  amount: number;
  email: string;
  currency: string;
  reference: string;
  callback_url?: string;
  metadata: {
    giftId: string;
    tier: BillingTier;
    cadence: BillingCadence;
    purpose: 'stitchpad_gift';
  };
}

export interface GiftPaystackClient {
  initializeTransaction(request: GiftPaystackInitializeRequest): Promise<PaystackInitializeResponse>;
}

export interface InitializeGiftDeps {
  db: admin.firestore.Firestore;
  paystack: GiftPaystackClient;
  now: () => Date;
  randomCode: () => string;        // bearer code (public flow) — also the gift doc id
  randomId: () => string;          // gift doc id (gift_me) + Paystack reference suffix
  callbackUrl?: string;
  billingEmailFallback?: string;   // Paystack requires a payer email; used if none given
}

interface PaystackChargeMetadata {
  uid?: string;
  giftId?: string;
  tier?: string;
  cadence?: string;
  purpose?: string;
}

export interface PaystackGiftChargeData {
  reference?: string;
  amount?: number;
  currency?: string;
  status?: string;
  paid_at?: string;
  paidAt?: string;
  metadata?: PaystackChargeMetadata;
}

export interface GiftWebhookDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  /** Best-effort transactional email; omitted = skip (entitlement still applies). */
  sendEmail?: (params: { to: string; subject: string; html: string; text: string }) => Promise<void>;
  /** Resolve a uid's email for the gift_me celebratory email; defaults to Firebase Auth. */
  lookupUserEmail?: (uid: string) => Promise<string | null>;
}

export interface RedeemGiftRequest {
  code?: unknown;
}

export interface RedeemGiftResponse {
  tier: BillingTier;
  cadence: BillingCadence;
}

export interface RedeemGiftDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export interface PublicGiftProfileResponse {
  businessName: string | null;
  displayName: string | null;
  logoUrl: string | null;
}

export interface PublicGiftProfileDeps {
  db: admin.firestore.Firestore;
}

export interface CreateGiftLinkResponse {
  token: string;
  url: string;
}

export interface CreateGiftLinkDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  randomToken: () => string;
}

export interface ExpireGiftsDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

// ---------------------------------------------------------------------------
// Function wrappers (thin; production deps wired here)
// ---------------------------------------------------------------------------

export const initializeGiftCheckout = functions
  .region(REGION)
  .runWith({ secrets: ['PAYSTACK_SECRET_KEY'] })
  .https.onCall(async (data): Promise<InitializeGiftResponse> => {
    return initializeGiftCheckoutHandler(data as InitializeGiftRequest, {
      db: admin.firestore(),
      paystack: createGiftPaystackClient(getPaystackSecretKey()),
      now: () => new Date(),
      randomCode: () => generateCode(12),
      randomId: () => crypto.randomBytes(6).toString('hex'),
      callbackUrl: getCallbackUrl(),
      billingEmailFallback: getGiftBillingEmail(),
    });
  });

export const redeemGift = functions
  .region(REGION)
  .https.onCall(async (data, context): Promise<RedeemGiftResponse> => {
    return redeemGiftHandler(data as RedeemGiftRequest, context, {
      db: admin.firestore(),
      now: () => new Date(),
    });
  });

export const getPublicGiftProfile = functions
  .region(REGION)
  .https.onCall(async (data): Promise<PublicGiftProfileResponse> => {
    return getPublicGiftProfileHandler(data as { token?: unknown }, {
      db: admin.firestore(),
    });
  });

export const createGiftLink = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<CreateGiftLinkResponse> => {
    return createGiftLinkHandler(context, {
      db: admin.firestore(),
      now: () => new Date(),
      randomToken: () => generateCode(20),
    });
  });

export const expireUnclaimedGifts = functions
  .region(REGION)
  .pubsub.schedule('0 1 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await expireUnclaimedGiftsHandler({ db: admin.firestore(), now: () => new Date() });
  });

export const resendUnclaimedGiftEmails = functions
  .region(REGION)
  // RESEND_API_KEY to re-send claim emails that didn't go out at webhook time.
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .pubsub.schedule('0 * * * *') // hourly
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await resendUnclaimedGiftEmailsHandler({ db: admin.firestore(), sendEmail: giftEmailSender() });
  });

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

export async function initializeGiftCheckoutHandler(
  data: InitializeGiftRequest,
  deps: InitializeGiftDeps,
): Promise<InitializeGiftResponse> {
  const tier = parseTier(data.tier);
  const cadence = parseCadence(data.cadence);
  const quantity = parseQuantity(data.quantity, cadence);
  const amountKobo = priceFor(tier, cadence) * quantity;

  const token = asNonEmptyString(data.token);
  const recipientEmail = asEmail(data.recipientEmail);
  const gifterName = asNonEmptyString(data.gifterName) ?? null;
  const gifterEmail = asEmail(data.gifterEmail);
  const note = asNonEmptyString(data.note) ?? null;

  let flow: GiftFlow;
  let targetUid: string | null = null;
  let giftId: string;
  let code: string | null = null;

  if (token) {
    flow = 'gift_me';
    targetUid = await resolveTokenUid(deps.db, token);
    if (!targetUid) {
      throw new functions.https.HttpsError('invalid-argument', 'invalid_gift_link');
    }
    giftId = `gift_${deps.now().getTime()}_${deps.randomId()}`;
  } else if (recipientEmail) {
    flow = 'public';
    code = deps.randomCode();
    giftId = code;
  } else {
    throw new functions.https.HttpsError('invalid-argument', 'missing_recipient');
  }

  // Paystack needs a payer email for the receipt. Prefer the gifter's, fall back to
  // the recipient's (public), then a configured no-reply billing address (anonymous
  // gift_me fan). Without any deliverable address, fail fast.
  const billingEmail = gifterEmail ?? recipientEmail ?? deps.billingEmailFallback;
  if (!billingEmail) {
    throw new functions.https.HttpsError('invalid-argument', 'missing_email');
  }

  const reference = `gft_${deps.now().getTime()}_${deps.randomId()}`;
  const giftRef = deps.db.doc(`gifts/${giftId}`);
  const createdAt = admin.firestore.Timestamp.fromDate(deps.now());

  try {
    await giftRef.create({
      status: 'pending' as GiftStatus,
      flow,
      tier,
      cadence,
      quantity,
      amountKobo,
      currency: CURRENCY,
      code,
      targetUid,
      recipientEmail: recipientEmail ?? null,
      gifterName,
      gifterEmail: gifterEmail ?? null,
      note,
      paystackReference: reference,
      authorizationUrl: null,
      paidAt: null,
      appliedAt: null,
      claimedAt: null,
      claimedByUid: null,
      expiresAt: null,
      failureReason: null,
      createdAt,
      updatedAt: createdAt,
    });
  } catch (err) {
    // Astronomically unlikely code collision (or a duplicate giftId) — surface as
    // retryable rather than charging against a half-written doc.
    if ((err as { code?: number }).code === 6) {
      throw new functions.https.HttpsError('aborted', 'gift_id_collision');
    }
    throw err;
  }

  try {
    const initialized = await deps.paystack.initializeTransaction({
      amount: amountKobo,
      email: billingEmail,
      currency: CURRENCY,
      reference,
      ...(deps.callbackUrl ? { callback_url: deps.callbackUrl } : {}),
      metadata: { giftId, tier, cadence, purpose: 'stitchpad_gift' },
    });
    await giftRef.set({
      authorizationUrl: initialized.authorization_url,
      accessCode: initialized.access_code,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    return {
      authorizationUrl: initialized.authorization_url,
      reference,
      ...(code ? { code } : {}),
    };
  } catch (error) {
    await giftRef.set({
      status: 'failed' as GiftStatus,
      failureReason: error instanceof Error ? error.message : 'paystack_initialize_failed',
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    throw new functions.https.HttpsError('unavailable', 'payment_provider_unavailable');
  }
}

/**
 * Applies a successful gift charge. Called from paystackWebhookHandler when
 * metadata.purpose === 'stitchpad_gift'. Idempotent: only a gift still in 'pending'
 * is processed. Emails are best-effort AFTER the transaction commits — a failed
 * email must not 500 the webhook (that would make Paystack re-apply the entitlement).
 */
export async function applyGiftWebhook(data: PaystackGiftChargeData, deps: GiftWebhookDeps): Promise<void> {
  const giftId = data.metadata?.giftId;
  if (!giftId) {
    functions.logger.warn('gift charge.success missing giftId');
    return;
  }

  const giftRef = deps.db.doc(`gifts/${giftId}`);
  const paidAt = parsePaidAt(data, deps.now());
  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());

  // Captured inside the transaction, run after commit (so a flaky email never rolls
  // back an applied entitlement).
  let afterCommit: (() => Promise<void>) | null = null;

  await deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(giftRef);
    if (!snap.exists) {
      functions.logger.warn('gift charge.success for unknown gift', { giftId });
      return;
    }
    const gift = snap.data() as {
      status?: GiftStatus;
      flow?: GiftFlow;
      tier?: BillingTier;
      cadence?: BillingCadence;
      quantity?: number;
      amountKobo?: number;
      code?: string | null;
      targetUid?: string | null;
      recipientEmail?: string | null;
      gifterName?: string | null;
      note?: string | null;
    };

    // Idempotent: anything past 'pending' (paid/claimed/expired/failed) is terminal here.
    if (gift.status !== 'pending') return;

    const amountMatches = data.amount === gift.amountKobo;
    if (data.status !== 'success' || !amountMatches || data.currency !== CURRENCY) {
      tx.set(giftRef, {
        status: 'failed' as GiftStatus,
        failureReason: 'paystack_payload_mismatch',
        updatedAt: nowTs,
      }, { merge: true });
      return;
    }

    const tier = gift.tier as BillingTier;
    const cadence = gift.cadence as BillingCadence;
    const quantity = gift.quantity ?? 1; // default 1 for pre-quantity gift docs
    const expiresAt = admin.firestore.Timestamp.fromDate(addYears(paidAt, 1));

    if (gift.flow === 'gift_me' && gift.targetUid) {
      const targetUid = gift.targetUid;
      const userRef = deps.db.doc(`users/${targetUid}`);
      const userSnap = await tx.get(userRef);
      const userData = userSnap.data() as CurrentSubscription | undefined;
      const grant = computeSubscriptionGrant({ userData, tier, cadence, paidAt, mode: 'gift', quantity });

      tx.set(userRef, {
        subscriptionTier: grant.subscriptionTier,
        subscriptionStatus: 'active',
        subscriptionEndsAt: admin.firestore.Timestamp.fromDate(grant.subscriptionEndsAt),
        subscriptionRenews: false,
        ...fallbackFields(grant),
        updatedAt: nowTs,
      }, { merge: true });

      // Deterministic id so a re-delivery never double-notifies (also covered by the
      // status guard). Written with merge so an existing read-state is preserved.
      const notifRef = userRef.collection('notifications').doc(`${giftId}__GIFT_RECEIVED`);
      // Fields mirror the client NotificationDto exactly (type/tier/gifterName/
      // isRead/createdAt) — do NOT add keys the DTO lacks (e.g. cadence): the
      // GitLive decode is best-effort (runCatching) and an unknown key risks the
      // gift notification being silently dropped from the inbox on some platforms.
      tx.set(notifRef, {
        type: 'GIFT_RECEIVED',
        tier, // the tier that was GIFTED (may differ from the now-active tier when queued)
        gifterName: gift.gifterName ?? null,
        isRead: false,
        createdAt: deps.now().getTime(),
      }, { merge: true });

      tx.set(giftRef, {
        status: 'claimed' as GiftStatus,
        paidAt: admin.firestore.Timestamp.fromDate(paidAt),
        appliedAt: nowTs,
        claimedAt: nowTs,
        claimedByUid: targetUid,
        expiresAt,
        failureReason: null,
        updatedAt: nowTs,
      }, { merge: true });

      const gifterName = gift.gifterName ?? undefined;
      afterCommit = async () => {
        const email = await resolveUserEmail(deps, targetUid);
        if (email && deps.sendEmail) {
          const msg = buildGiftReceivedEmail({ gifterName, tier, cadence, quantity });
          await deps.sendEmail({ to: email, ...msg });
        }
      };
    } else {
      // public: payment captured; recipient claims later with the code.
      tx.set(giftRef, {
        status: 'paid' as GiftStatus,
        paidAt: admin.firestore.Timestamp.fromDate(paidAt),
        expiresAt,
        failureReason: null,
        // Claim-email delivery marker: the sweep cron (resendUnclaimedGiftEmails)
        // retries any gift left with needsClaimEmail=true if this first send fails.
        needsClaimEmail: true,
        claimEmailAttempts: 0,
        claimEmailSentAt: null,
        updatedAt: nowTs,
      }, { merge: true });

      // First send attempt happens now (best UX); deliverClaimEmail clears the
      // marker on success or leaves it for the cron to retry on failure.
      const giftForEmail = {
        recipientEmail: gift.recipientEmail ?? null,
        code: gift.code ?? giftId,
        gifterName: gift.gifterName ?? null,
        note: gift.note ?? null,
        tier,
        cadence,
        quantity,
        claimEmailAttempts: 0,
      };
      afterCommit = async () => {
        await deliverClaimEmail(deps.db, giftId, giftForEmail, deps.sendEmail);
      };
    }
  });

  if (afterCommit) {
    try {
      await (afterCommit as () => Promise<void>)();
    } catch (err) {
      functions.logger.error('gift email failed (entitlement already applied)', {
        giftId, error: err instanceof Error ? err.message : String(err),
      });
    }
  }
}

export async function redeemGiftHandler(
  data: RedeemGiftRequest,
  context: functions.https.CallableContext,
  deps: RedeemGiftDeps,
): Promise<RedeemGiftResponse> {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }
  const code = asNonEmptyString(data.code);
  if (!code) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_code');
  }

  const giftRef = deps.db.doc(`gifts/${code}`);
  const now = deps.now();
  const nowTs = admin.firestore.Timestamp.fromDate(now);
  let result: RedeemGiftResponse | null = null;

  await deps.db.runTransaction(async (tx) => {
    const snap = await tx.get(giftRef);
    if (!snap.exists) {
      throw new functions.https.HttpsError('not-found', 'gift_not_found');
    }
    const gift = snap.data() as {
      status?: GiftStatus;
      tier?: BillingTier;
      cadence?: BillingCadence;
      quantity?: number;
      claimedByUid?: string | null;
      expiresAt?: unknown;
    };

    if (gift.status === 'claimed') {
      // Same caller re-redeeming (double tap / retry) is a success, not an error.
      if (gift.claimedByUid === uid) {
        result = { tier: gift.tier as BillingTier, cadence: gift.cadence as BillingCadence };
        return;
      }
      throw new functions.https.HttpsError('failed-precondition', 'gift_already_claimed');
    }
    if (gift.status === 'expired') {
      throw new functions.https.HttpsError('failed-precondition', 'gift_expired');
    }
    if (gift.status !== 'paid') {
      throw new functions.https.HttpsError('failed-precondition', 'gift_not_payable');
    }
    const expiresAt = toDate(gift.expiresAt);
    if (expiresAt && expiresAt.getTime() <= now.getTime()) {
      throw new functions.https.HttpsError('failed-precondition', 'gift_expired');
    }

    const tier = gift.tier as BillingTier;
    const cadence = gift.cadence as BillingCadence;
    const quantity = gift.quantity ?? 1; // default 1 for pre-quantity gift docs
    const userRef = deps.db.doc(`users/${uid}`);
    const userSnap = await tx.get(userRef);
    const userData = userSnap.data() as CurrentSubscription | undefined;
    const grant = computeSubscriptionGrant({ userData, tier, cadence, paidAt: now, mode: 'gift', quantity });

    tx.set(userRef, {
      subscriptionTier: grant.subscriptionTier,
      subscriptionStatus: 'active',
      subscriptionEndsAt: admin.firestore.Timestamp.fromDate(grant.subscriptionEndsAt),
      subscriptionRenews: false,
      ...fallbackFields(grant),
      updatedAt: nowTs,
    }, { merge: true });
    tx.set(giftRef, {
      status: 'claimed' as GiftStatus,
      claimedAt: nowTs,
      appliedAt: nowTs,
      claimedByUid: uid,
      updatedAt: nowTs,
    }, { merge: true });

    result = { tier, cadence };
  });

  if (!result) {
    // Defensive: a transaction that neither set a result nor threw.
    throw new functions.https.HttpsError('internal', 'redeem_failed');
  }
  return result;
}

export async function getPublicGiftProfileHandler(
  data: { token?: unknown },
  deps: PublicGiftProfileDeps,
): Promise<PublicGiftProfileResponse> {
  const token = asNonEmptyString(data.token);
  if (!token) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_gift_link');
  }
  const uid = await resolveTokenUid(deps.db, token);
  if (!uid) {
    throw new functions.https.HttpsError('not-found', 'invalid_gift_link');
  }
  const snap = await deps.db.doc(`users/${uid}`).get();
  const u = snap.data() as
    | { businessName?: string; displayName?: string; businessLogoUrl?: string }
    | undefined;
  // Whitelist: never echo the whole user doc to an unauthenticated caller.
  return {
    businessName: u?.businessName?.trim() || null,
    displayName: u?.displayName?.trim() || null,
    logoUrl: u?.businessLogoUrl?.trim() || null,
  };
}

export async function createGiftLinkHandler(
  context: functions.https.CallableContext,
  deps: CreateGiftLinkDeps,
): Promise<CreateGiftLinkResponse> {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }
  const userRef = deps.db.doc(`users/${uid}`);
  const existing = (await userRef.get()).data() as { giftLinkToken?: string } | undefined;
  if (existing?.giftLinkToken) {
    return { token: existing.giftLinkToken, url: `${GIFT_LINK_BASE}/${existing.giftLinkToken}` };
  }

  const token = deps.randomToken();
  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  await deps.db.runTransaction(async (tx) => {
    const fresh = (await tx.get(userRef)).data() as { giftLinkToken?: string } | undefined;
    if (fresh?.giftLinkToken) return; // another call won the race
    tx.set(userRef, { giftLinkToken: token, updatedAt: nowTs }, { merge: true });
    tx.set(deps.db.doc(`giftLinkTokens/${token}`), { uid, createdAt: nowTs });
  });

  const settled = (await userRef.get()).data() as { giftLinkToken?: string } | undefined;
  const finalToken = settled?.giftLinkToken ?? token;
  return { token: finalToken, url: `${GIFT_LINK_BASE}/${finalToken}` };
}

export async function expireUnclaimedGiftsHandler(deps: ExpireGiftsDeps): Promise<void> {
  const now = deps.now();
  const expired = await deps.db.collection('gifts')
    .where('status', '==', 'paid')
    .where('expiresAt', '<=', admin.firestore.Timestamp.fromDate(now))
    .get();

  const BATCH_LIMIT = 500;
  for (let i = 0; i < expired.docs.length; i += BATCH_LIMIT) {
    const batch = deps.db.batch();
    for (const doc of expired.docs.slice(i, i + BATCH_LIMIT)) {
      batch.set(doc.ref, {
        status: 'expired' as GiftStatus,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }
    await batch.commit();
  }
}

export interface ResendClaimEmailsDeps {
  db: admin.firestore.Firestore;
  sendEmail?: (params: { to: string; subject: string; html: string; text: string }) => Promise<void>;
}

/**
 * Backstop sweep for public-gift claim emails that didn't go out at webhook time
 * (e.g. Resend was briefly down). Re-sends any gift left with needsClaimEmail=true.
 * The flag only exists on the small set of not-yet-delivered gifts, so the query
 * is cheap and needs no composite index.
 */
export async function resendUnclaimedGiftEmailsHandler(deps: ResendClaimEmailsDeps): Promise<void> {
  const pending = await deps.db.collection('gifts').where('needsClaimEmail', '==', true).get();
  for (const doc of pending.docs) {
    const gift = doc.data() as ClaimEmailGift;
    if ((gift.claimEmailAttempts ?? 0) >= MAX_CLAIM_EMAIL_ATTEMPTS) continue; // safety; deliver also guards
    await deliverClaimEmail(deps.db, doc.id, gift, deps.sendEmail);
  }
}

interface ClaimEmailGift {
  recipientEmail?: string | null;
  code?: string | null;
  gifterName?: string | null;
  note?: string | null;
  tier?: BillingTier;
  cadence?: BillingCadence;
  quantity?: number;
  claimEmailAttempts?: number;
}

/**
 * Sends a public gift's claim email and updates its delivery marker. Used by both
 * the webhook's first attempt and the hourly sweep. Never throws — it records the
 * outcome on the gift doc:
 *  - success            → needsClaimEmail=false, claimEmailSentAt set
 *  - permanent (4xx)    → needsClaimEmail=false (don't retry a bad address)
 *  - transient (5xx/net)→ needsClaimEmail stays true until MAX_CLAIM_EMAIL_ATTEMPTS
 */
export async function deliverClaimEmail(
  db: admin.firestore.Firestore,
  giftId: string,
  gift: ClaimEmailGift,
  sendEmail?: (params: { to: string; subject: string; html: string; text: string }) => Promise<void>,
): Promise<void> {
  const giftRef = db.doc(`gifts/${giftId}`);
  const recipientEmail = gift.recipientEmail ?? null;
  if (!recipientEmail) {
    await giftRef.set({ needsClaimEmail: false, claimEmailLastError: 'no_recipient_email' }, { merge: true });
    return;
  }
  if (!sendEmail) return; // sender not configured this run — leave the flag for the next sweep

  const code = gift.code ?? giftId;
  try {
    const msg = buildGiftClaimEmail({
      gifterName: gift.gifterName ?? undefined,
      note: gift.note ?? undefined,
      code,
      claimUrl: `${CLAIM_LINK_BASE}?code=${encodeURIComponent(code)}`,
      tier: gift.tier as BillingTier,
      cadence: gift.cadence as BillingCadence,
      quantity: gift.quantity,
    });
    await sendEmail({ to: recipientEmail, ...msg });
    await giftRef.set({
      needsClaimEmail: false,
      claimEmailSentAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  } catch (err) {
    const status = err instanceof ResendError ? err.status : undefined;
    const permanent = typeof status === 'number' && status >= 400 && status < 500;
    const attempts = (gift.claimEmailAttempts ?? 0) + 1;
    const giveUp = permanent || attempts >= MAX_CLAIM_EMAIL_ATTEMPTS;
    await giftRef.set({
      needsClaimEmail: !giveUp,
      claimEmailAttempts: attempts,
      claimEmailLastError: err instanceof Error ? err.message : String(err),
    }, { merge: true });
    functions.logger.warn('gift claim email send failed', {
      giftId, attempts, permanent, giveUp, error: err instanceof Error ? err.message : String(err),
    });
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

export function generateCode(length: number): string {
  const buf = crypto.randomBytes(length);
  let out = '';
  for (const b of buf) out += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return out;
}

async function resolveTokenUid(db: admin.firestore.Firestore, token: string): Promise<string | null> {
  const snap = await db.doc(`giftLinkTokens/${token}`).get();
  const uid = (snap.data() as { uid?: string } | undefined)?.uid;
  return typeof uid === 'string' && uid.length > 0 ? uid : null;
}

async function resolveUserEmail(deps: GiftWebhookDeps, uid: string): Promise<string | null> {
  if (deps.lookupUserEmail) return deps.lookupUserEmail(uid);
  try {
    const user = await admin.auth().getUser(uid);
    return user.email ?? null;
  } catch {
    return null;
  }
}

function asNonEmptyString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function asEmail(value: unknown): string | null {
  return typeof value === 'string' && value.includes('@') ? value.trim() : null;
}

/**
 * The queued-fallback fields to write for a grant. Explicit null clears any prior
 * fallback (e.g. a same-tier gift that no longer needs one) — gifts own these
 * fields end to end, so they must set OR clear, never leave stale.
 */
function fallbackFields(grant: SubscriptionGrant): {
  subscriptionFallbackTier: BillingTier | null;
  subscriptionFallbackEndsAt: admin.firestore.Timestamp | null;
} {
  return {
    subscriptionFallbackTier: grant.fallbackTier,
    subscriptionFallbackEndsAt: grant.fallbackEndsAt
      ? admin.firestore.Timestamp.fromDate(grant.fallbackEndsAt)
      : null,
  };
}

/**
 * Number of periods to gift. Defaults to 1 when absent (backwards-compatible with
 * callers that don't send it). Must be a whole number within the per-cadence cap
 * (12 months or 5 years) — anything else is rejected so a tampered/huge amount
 * can't be charged.
 */
function parseQuantity(value: unknown, cadence: BillingCadence): number {
  if (value === undefined || value === null) return 1;
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isInteger(n) || n < 1 || n > MAX_QUANTITY[cadence]) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_quantity');
  }
  return n;
}

function parsePaidAt(data: PaystackGiftChargeData, fallback: Date): Date {
  const raw = data.paid_at ?? data.paidAt;
  if (!raw) return fallback;
  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? fallback : parsed;
}

function createGiftPaystackClient(secretKey: string): GiftPaystackClient {
  return {
    async initializeTransaction(request: GiftPaystackInitializeRequest): Promise<PaystackInitializeResponse> {
      const response = await fetch('https://api.paystack.co/transaction/initialize', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${secretKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      const json = await response.json() as {
        status?: boolean;
        message?: string;
        data?: PaystackInitializeResponse;
      };
      if (!response.ok || !json.status || !json.data?.authorization_url) {
        throw new Error(json.message ?? 'paystack_initialize_failed');
      }
      return json.data;
    },
  };
}

function getPaystackSecretKey(): string {
  const key = process.env.PAYSTACK_SECRET_KEY ?? functions.config().paystack?.secret_key;
  if (!key) throw new Error('PAYSTACK_SECRET_KEY is not configured');
  return key;
}

function getCallbackUrl(): string | undefined {
  return process.env.PAYSTACK_GIFT_CALLBACK_URL
    ?? process.env.PAYSTACK_CALLBACK_URL
    ?? functions.config().paystack?.gift_callback_url
    ?? functions.config().paystack?.callback_url;
}

function getGiftBillingEmail(): string | undefined {
  return process.env.GIFT_BILLING_EMAIL ?? functions.config().gift?.billing_email;
}

/** Builds the production webhook email sender from the RESEND_API_KEY secret. */
export function giftEmailSender(): ((p: { to: string; subject: string; html: string; text: string }) => Promise<void>) | undefined {
  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    functions.logger.warn('RESEND_API_KEY not configured — gift emails will be skipped');
    return undefined;
  }
  return (p) => sendResendEmail(apiKey, p);
}

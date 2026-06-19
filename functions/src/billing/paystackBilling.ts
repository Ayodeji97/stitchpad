import * as crypto from 'crypto';
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { computeSubscriptionGrant, toDate } from './subscriptionPeriod';
import { applyGiftWebhook, giftEmailSender } from './giftBilling';

// Re-exported so existing importers (tests, templates) keep their import paths.
export { addPeriod } from './subscriptionPeriod';

export type BillingTier = 'pro' | 'atelier';
export type BillingCadence = 'monthly' | 'annual';
export type BillingStatus = 'pending' | 'paid' | 'failed';

const REGION = 'europe-west1';
const CURRENCY = 'NGN';

const PRICES_KOBO: Record<BillingTier, Record<BillingCadence, number>> = {
  pro: {
    monthly: 200_000,
    annual: 2_000_000,
  },
  atelier: {
    monthly: 400_000,
    annual: 4_000_000,
  },
};

export interface InitializeCheckoutRequest {
  tier?: unknown;
  cadence?: unknown;
}

export interface InitializeCheckoutResponse {
  authorizationUrl: string;
  reference: string;
}

export interface PaystackInitializeResponse {
  authorization_url: string;
  access_code: string;
  reference: string;
}

interface PaystackInitializeRequest {
  amount: number;
  email: string;
  currency: string;
  reference: string;
  callback_url?: string;
  metadata: {
    uid: string;
    tier: BillingTier;
    cadence: BillingCadence;
    purpose: 'stitchpad_subscription';
  };
}

export interface PaystackClient {
  initializeTransaction(request: PaystackInitializeRequest): Promise<PaystackInitializeResponse>;
}

export interface InitializeDeps {
  db: admin.firestore.Firestore;
  paystack: PaystackClient;
  now: () => Date;
  randomId: () => string;
  callbackUrl?: string;
}

interface PaystackChargeSuccessData {
  reference?: string;
  amount?: number;
  currency?: string;
  status?: string;
  paid_at?: string;
  paidAt?: string;
  metadata?: {
    uid?: string;
    giftId?: string;
    tier?: string;
    cadence?: string;
    purpose?: string;
  };
}

interface PaystackWebhookEvent {
  event?: string;
  data?: PaystackChargeSuccessData;
}

export interface WebhookDeps {
  db: admin.firestore.Firestore;
  secretKey: string;
  now: () => Date;
  // Gift charges (purpose === 'stitchpad_gift') ride this same webhook. Optional so
  // existing subscription tests need no wiring; production supplies both.
  sendGiftEmail?: (params: { to: string; subject: string; html: string; text: string }) => Promise<void>;
  lookupUserEmail?: (uid: string) => Promise<string | null>;
}

export interface ExpireDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export const initializeSubscriptionCheckout = functions
  .region(REGION)
  .runWith({ secrets: ['PAYSTACK_SECRET_KEY'] })
  .https.onCall(async (data, context): Promise<InitializeCheckoutResponse> => {
    return initializeSubscriptionCheckoutHandler(
      data as InitializeCheckoutRequest,
      context,
      {
        db: admin.firestore(),
        paystack: createPaystackClient(getPaystackSecretKey()),
        now: () => new Date(),
        randomId: () => crypto.randomBytes(6).toString('hex'),
        callbackUrl: getCallbackUrl(),
      },
    );
  });

export const paystackWebhook = functions
  .region(REGION)
  // RESEND_API_KEY is needed because gift charges ride this webhook and send the
  // recipient a claim / "you've been gifted" email.
  .runWith({ secrets: ['PAYSTACK_SECRET_KEY', 'RESEND_API_KEY'] })
  .https.onRequest(async (req, res) => {
    try {
      await paystackWebhookHandler(
        req.body as PaystackWebhookEvent,
        req.header('x-paystack-signature'),
        getRawBody(req),
        {
          db: admin.firestore(),
          secretKey: getPaystackSecretKey(),
          now: () => new Date(),
          sendGiftEmail: giftEmailSender(),
        },
      );
      res.status(200).send('ok');
    } catch (error) {
      functions.logger.warn('paystack webhook rejected', {
        reason: error instanceof Error ? error.message : String(error),
      });
      res.status(400).send('invalid');
    }
  });

export const expirePrepaidSubscriptions = functions
  .region(REGION)
  // Cron expression (not an 'every N hours' interval) so the timeZone is honored —
  // App Engine interval schedules ignore timeZone and drift off Lagos time. Runs
  // 01:00 Africa/Lagos daily.
  .pubsub.schedule('0 1 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await expirePrepaidSubscriptionsHandler({
      db: admin.firestore(),
      now: () => new Date(),
    });
  });

export async function initializeSubscriptionCheckoutHandler(
  data: InitializeCheckoutRequest,
  context: functions.https.CallableContext,
  deps: InitializeDeps,
): Promise<InitializeCheckoutResponse> {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }

  const tier = parseTier(data.tier);
  const cadence = parseCadence(data.cadence);
  const amountKobo = priceFor(tier, cadence);
  const reference = buildReference(uid, deps.now(), deps.randomId());
  const email = await resolveEmail(uid, context, deps.db);

  const billingRef = deps.db.doc(`users/${uid}/billingTransactions/${reference}`);
  const createdAt = admin.firestore.Timestamp.fromDate(deps.now());
  await billingRef.set({
    tier,
    cadence,
    amountKobo,
    currency: CURRENCY,
    status: 'pending',
    paystackReference: reference,
    authorizationUrl: null,
    paidAt: null,
    appliedAt: null,
    failureReason: null,
    createdAt,
    updatedAt: createdAt,
  });

  try {
    const initialized = await deps.paystack.initializeTransaction({
      amount: amountKobo,
      email,
      currency: CURRENCY,
      reference,
      ...(deps.callbackUrl ? { callback_url: deps.callbackUrl } : {}),
      metadata: {
        uid,
        tier,
        cadence,
        purpose: 'stitchpad_subscription',
      },
    });
    await billingRef.set({
      authorizationUrl: initialized.authorization_url,
      accessCode: initialized.access_code,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    return {
      authorizationUrl: initialized.authorization_url,
      reference,
    };
  } catch (error) {
    await billingRef.set({
      status: 'failed',
      failureReason: error instanceof Error ? error.message : 'paystack_initialize_failed',
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    throw new functions.https.HttpsError('unavailable', 'payment_provider_unavailable');
  }
}

export async function paystackWebhookHandler(
  event: PaystackWebhookEvent,
  signature: string | undefined,
  rawBody: Buffer,
  deps: WebhookDeps,
): Promise<void> {
  if (!isValidPaystackSignature(rawBody, signature, deps.secretKey)) {
    throw new Error('invalid_signature');
  }
  if (event.event !== 'charge.success') return;

  const data = event.data;
  const metadata = data?.metadata;

  // Gifts ride the same webhook URL — route by purpose before the subscription-only
  // metadata checks (gifts key on giftId, not uid).
  if (metadata?.purpose === 'stitchpad_gift') {
    await applyGiftWebhook(data ?? {}, {
      db: deps.db,
      now: deps.now,
      sendEmail: deps.sendGiftEmail,
      lookupUserEmail: deps.lookupUserEmail,
    });
    return;
  }

  if (
    !data?.reference ||
    !metadata?.uid ||
    metadata.purpose !== 'stitchpad_subscription'
  ) {
    functions.logger.warn('paystack charge.success missing subscription metadata', {
      reference: data?.reference,
    });
    return;
  }

  const tier = parseTier(metadata.tier);
  const cadence = parseCadence(metadata.cadence);
  const expectedAmount = priceFor(tier, cadence);
  const paidAt = parsePaystackPaidAt(data, deps.now());
  const billingRef = deps.db.doc(`users/${metadata.uid}/billingTransactions/${data.reference}`);
  const userRef = deps.db.doc(`users/${metadata.uid}`);

  await deps.db.runTransaction(async (tx) => {
    const billingSnap = await tx.get(billingRef);
    if (!billingSnap.exists) {
      functions.logger.warn('paystack billing transaction not found', {
        uid: metadata.uid,
        reference: data.reference,
      });
      return;
    }

    const billing = billingSnap.data() as {
      status?: BillingStatus;
      appliedAt?: admin.firestore.Timestamp | null;
      amountKobo?: number;
      tier?: string;
      cadence?: string;
    };
    if (billing.appliedAt || billing.status === 'paid') return;

    const amountMatches = data.amount === expectedAmount && billing.amountKobo === expectedAmount;
    const shapeMatches = billing.tier === tier && billing.cadence === cadence && data.currency === CURRENCY;
    if (data.status !== 'success' || !amountMatches || !shapeMatches) {
      tx.set(billingRef, {
        status: 'failed',
        failureReason: 'paystack_payload_mismatch',
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      return;
    }

    const userSnap = await tx.get(userRef);
    const userData = userSnap.data() as
      | { subscriptionTier?: string; subscriptionStatus?: string; subscriptionEndsAt?: unknown }
      | undefined;
    // Stacking/fresh-period logic lives in computeSubscriptionGrant (shared with
    // gifts). A purchase uses mode 'purchase' so a deliberate tier switch starts a
    // fresh period exactly as before — see that function for the full rationale.
    const grant = computeSubscriptionGrant({ userData, tier, cadence, paidAt, mode: 'purchase' });
    const nowTs = admin.firestore.Timestamp.fromDate(deps.now());

    tx.set(userRef, {
      subscriptionTier: grant.subscriptionTier,
      subscriptionStatus: 'active',
      subscriptionEndsAt: admin.firestore.Timestamp.fromDate(grant.subscriptionEndsAt),
      subscriptionRenews: false,
      // Mark provenance so a later, delayed Apple EXPIRED/REFUND for an OLD Apple
      // subscription can't clobber this Paystack grant (the Apple downgrade guard
      // in appleBilling.ts only clears the doc when subscriptionSource is 'apple').
      subscriptionSource: 'paystack',
      updatedAt: nowTs,
    }, { merge: true });
    tx.set(billingRef, {
      status: 'paid',
      paidAt: admin.firestore.Timestamp.fromDate(paidAt),
      appliedAt: nowTs,
      failureReason: null,
      updatedAt: nowTs,
    }, { merge: true });
  });
}

export async function expirePrepaidSubscriptionsHandler(deps: ExpireDeps): Promise<void> {
  const now = deps.now();
  const expired = await deps.db.collection('users')
    .where('subscriptionTier', 'in', ['pro', 'atelier'])
    .where('subscriptionRenews', '==', false)
    .where('subscriptionEndsAt', '<=', admin.firestore.Timestamp.fromDate(now))
    .get();

  const nowMs = now.getTime();
  const BATCH_LIMIT = 500;
  for (let i = 0; i < expired.docs.length; i += BATCH_LIMIT) {
    const batch = deps.db.batch();
    for (const doc of expired.docs.slice(i, i + BATCH_LIMIT)) {
      const data = (doc.data?.() ?? {}) as {
        subscriptionFallbackTier?: string;
        subscriptionFallbackEndsAt?: unknown;
      };
      const fbTier = data.subscriptionFallbackTier;
      const fbEnd = toDate(data.subscriptionFallbackEndsAt);
      const hasLiveFallback =
        (fbTier === 'pro' || fbTier === 'atelier') && fbEnd !== null && fbEnd.getTime() > nowMs;

      if (hasLiveFallback) {
        // Two-step unwind: the active (higher) segment ended — promote the queued
        // lower tier to active and clear the fallback slot. It returns to Free on a
        // later run when this promoted segment also expires.
        batch.set(doc.ref, {
          subscriptionTier: fbTier,
          subscriptionStatus: 'active',
          subscriptionEndsAt: admin.firestore.Timestamp.fromDate(fbEnd),
          subscriptionRenews: false,
          subscriptionFallbackTier: null,
          subscriptionFallbackEndsAt: null,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
      } else {
        // No live queued segment → back to Free (also clears any stale/expired fallback).
        batch.set(doc.ref, {
          subscriptionTier: 'free',
          subscriptionStatus: 'expired',
          subscriptionRenews: false,
          subscriptionFallbackTier: null,
          subscriptionFallbackEndsAt: null,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
      }
    }
    await batch.commit();
  }
}

export function priceFor(tier: BillingTier, cadence: BillingCadence): number {
  return PRICES_KOBO[tier][cadence];
}

export function parseTier(raw: unknown): BillingTier {
  if (raw === 'pro' || raw === 'atelier') return raw;
  throw new functions.https.HttpsError('invalid-argument', 'invalid_plan');
}

export function parseCadence(raw: unknown): BillingCadence {
  if (raw === 'monthly' || raw === 'annual') return raw;
  throw new functions.https.HttpsError('invalid-argument', 'invalid_plan');
}

export function buildReference(uid: string, now: Date, randomId: string): string {
  const uidSuffix = uid.replace(/[^a-zA-Z0-9]/g, '').slice(-8) || 'user';
  return `stp_${uidSuffix}_${now.getTime()}_${randomId}`;
}

export function isValidPaystackSignature(
  rawBody: Buffer,
  signature: string | undefined,
  secretKey: string,
): boolean {
  if (!signature) return false;
  const expected = crypto.createHmac('sha512', secretKey).update(rawBody).digest('hex');
  if (expected.length !== signature.length) return false;
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature));
}

function createPaystackClient(secretKey: string): PaystackClient {
  return {
    async initializeTransaction(request: PaystackInitializeRequest): Promise<PaystackInitializeResponse> {
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
  return process.env.PAYSTACK_CALLBACK_URL ?? functions.config().paystack?.callback_url;
}

function getRawBody(req: functions.https.Request): Buffer {
  const rawBody = (req as functions.https.Request & { rawBody?: Buffer }).rawBody;
  return rawBody ?? Buffer.from(JSON.stringify(req.body ?? {}));
}

async function resolveEmail(
  uid: string,
  context: functions.https.CallableContext,
  db: admin.firestore.Firestore,
): Promise<string> {
  const tokenEmail = context.auth?.token.email;
  if (typeof tokenEmail === 'string' && tokenEmail.includes('@')) return tokenEmail;
  const snap = await db.doc(`users/${uid}`).get();
  const docEmail = (snap.data() as { email?: unknown } | undefined)?.email;
  if (typeof docEmail === 'string' && docEmail.includes('@')) return docEmail;
  throw new functions.https.HttpsError('failed-precondition', 'missing_email');
}

function parsePaystackPaidAt(data: PaystackChargeSuccessData, fallback: Date): Date {
  const raw = data.paid_at ?? data.paidAt;
  if (!raw) return fallback;
  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? fallback : parsed;
}

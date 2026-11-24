import * as crypto from 'crypto';
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { planForProduct } from './appleBilling';
import { BillingTier, shouldWriteUserDoc } from './storeEntitlement';
import { createPlayApi } from './playApi';

// Google Play Billing (Android outside Nigeria, spec 6b). Modeled on
// appleBilling.ts: the client hands us a purchase token, and EVERY server path
// (callable, RTDN, reconcile) re-fetches the subscription from the Play
// Developer API. Neither the client nor the RTDN payload is trusted for state.
//
// users/{uid} gets subscriptionSource 'play' plus playPurchaseToken (raw, needed
// by reconcile), playPurchaseTokenHash (ownership) and playProductId. The reverse
// index playSubscriptions/{sha256(token)} -> { uid } lets RTDN find the user.

const REGION = 'europe-west1';

// Subset of androidpublisher v3 SubscriptionPurchaseV2 that we read.
export interface PlaySubscription {
  subscriptionState: string; // SUBSCRIPTION_STATE_*
  acknowledgementState?: string; // ACKNOWLEDGEMENT_STATE_PENDING | _ACKNOWLEDGED
  lineItems: {
    productId: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
  }[];
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  linkedPurchaseToken?: string;
  testPurchase?: object;
}

export interface PlayApi {
  getSubscription(purchaseToken: string): Promise<PlaySubscription>;
  acknowledge(productId: string, purchaseToken: string): Promise<void>;
}

function sha256Hex(value: string): string {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex');
}

// The client sets BillingFlowParams.obfuscatedAccountId to this value. KEEP IN
// SYNC with sha256Hex() in the Android PlayBillingPaymentRepository.
export function playAccountId(uid: string): string {
  return sha256Hex(uid);
}

// Purchase tokens are long and opaque; the hash is the Firestore doc id.
export function playTokenHash(purchaseToken: string): string {
  return sha256Hex(purchaseToken);
}

// License-tester purchases (testPurchase present) cost nothing, so they must not
// mint real entitlement in production. Mirror of APPLE_ALLOW_SANDBOX.
export function testPurchaseGrantsAllowed(): boolean {
  return process.env.PLAY_ALLOW_TEST_PURCHASES === 'true';
}

export interface PlayDesiredState {
  tier: BillingTier | 'free';
  status: 'active' | 'expired' | 'pending';
  subscriptionRenews: boolean;
  endsAtMs: number | null;
  productId: string | null;
}

const EXPIRED_STATE: PlayDesiredState = {
  tier: 'free', status: 'expired', subscriptionRenews: false, endsAtMs: null, productId: null,
};

function parseTime(value?: string): number | null {
  if (!value) return null;
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}

export function desiredStateForPlay(sub: PlaySubscription, nowMs: number): PlayDesiredState {
  const line = sub.lineItems?.[0];
  const plan = line ? planForProduct(line.productId) : null;
  if (!line || !plan) return { ...EXPIRED_STATE };
  const expiryMs = parseTime(line.expiryTime);

  switch (sub.subscriptionState) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      return {
        tier: plan.tier,
        status: 'active',
        subscriptionRenews: line.autoRenewingPlan?.autoRenewEnabled ?? true,
        endsAtMs: expiryMs,
        productId: line.productId,
      };
    case 'SUBSCRIPTION_STATE_CANCELED':
      // Cancelled but paid through expiryTime: keep access, stop renewing.
      if (expiryMs != null && expiryMs > nowMs) {
        return {
          tier: plan.tier, status: 'active', subscriptionRenews: false, endsAtMs: expiryMs, productId: line.productId,
        };
      }
      return { ...EXPIRED_STATE };
    case 'SUBSCRIPTION_STATE_PENDING':
      // Cash / carrier payment not settled yet. Never grants; RTDN or the
      // client sweep re-verifies once it completes.
      return { tier: 'free', status: 'pending', subscriptionRenews: false, endsAtMs: null, productId: line.productId };
    default:
      // ON_HOLD, PAUSED, EXPIRED, PENDING_PURCHASE_CANCELED, unknown.
      return { ...EXPIRED_STATE };
  }
}

export interface PlayDeps {
  db: admin.firestore.Firestore;
  api: PlayApi;
  now: () => Date;
}

export interface PlayCommitRefs {
  userRef: admin.firestore.DocumentReference;
  billingRef: admin.firestore.DocumentReference;
  indexRef: admin.firestore.DocumentReference;
  uid: string;
  purchaseToken: string;
}

export interface VerifyPlayPurchaseResponse {
  tier: BillingTier | 'free';
  status: 'active' | 'expired' | 'pending';
  endsAt: number | null;
}

function refsFor(db: admin.firestore.Firestore, uid: string, purchaseToken: string): PlayCommitRefs {
  const tokenHash = playTokenHash(purchaseToken);
  return {
    userRef: db.doc(`users/${uid}`),
    billingRef: db.doc(`users/${uid}/billingTransactions/play_${tokenHash}`),
    indexRef: db.doc(`playSubscriptions/${tokenHash}`),
    uid,
    purchaseToken,
  };
}

// ── Cloud Functions ───────────────────────────────────────────────────────

export const verifyPlayPurchase = functions
  .region(REGION)
  .https.onCall(async (data, context): Promise<VerifyPlayPurchaseResponse> => {
    return verifyPlayPurchaseHandler(
      data as { purchaseToken?: unknown },
      context,
      { db: admin.firestore(), api: createPlayApi(), now: () => new Date() },
    );
  });

// ── Handlers (pure, dependency-injected; unit tested) ─────────────────────

export async function verifyPlayPurchaseHandler(
  data: { purchaseToken?: unknown },
  context: functions.https.CallableContext,
  deps: PlayDeps,
): Promise<VerifyPlayPurchaseResponse> {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }
  const purchaseToken = data?.purchaseToken;
  if (typeof purchaseToken !== 'string' || !purchaseToken) {
    throw new functions.https.HttpsError('invalid-argument', 'missing_purchase_token');
  }

  const fetchedAtMs = deps.now().getTime();
  let sub: PlaySubscription;
  try {
    sub = await deps.api.getSubscription(purchaseToken);
  } catch (error) {
    functions.logger.warn('play subscription fetch failed', {
      uid,
      reason: error instanceof Error ? error.message : String(error),
    });
    throw new functions.https.HttpsError('unavailable', 'play_unavailable');
  }

  // Bind the purchase to the caller: the client set obfuscatedAccountId to
  // sha256(uid) at purchase. Absent or different means another account's token.
  if (sub.externalAccountIdentifiers?.obfuscatedExternalAccountId !== playAccountId(uid)) {
    throw new functions.https.HttpsError('failed-precondition', 'account_mismatch');
  }

  if (sub.testPurchase && !testPurchaseGrantsAllowed()) {
    functions.logger.warn('play test purchase refused', { uid, productId: sub.lineItems?.[0]?.productId });
    throw new functions.https.HttpsError('failed-precondition', 'test_purchase_not_allowed');
  }

  const desired = desiredStateForPlay(sub, deps.now().getTime());
  const refs = refsFor(deps.db, uid, purchaseToken);

  await deps.db.runTransaction(async (tx) => {
    // A Play subscription belongs to its first purchaser, never to whoever signs
    // in next on the same device / Google account.
    const indexSnap = await tx.get(refs.indexRef);
    const boundUid = (indexSnap.data() as { uid?: string } | undefined)?.uid;
    if (boundUid && boundUid !== uid) {
      throw new functions.https.HttpsError('failed-precondition', 'subscription_belongs_to_another_account');
    }
    await commitPlayState(tx, refs, desired, playTokenHash(purchaseToken), fetchedAtMs, deps.now(),
      sub.linkedPurchaseToken);
  });

  await acknowledgeIfNeeded(deps.api, sub, desired, purchaseToken, uid);
  return { tier: desired.tier, status: desired.status, endsAt: desired.endsAtMs };
}

// Google refunds any purchase not acknowledged within 3 days. Only the server
// acknowledges, and only after the entitlement is committed. A failure is
// logged, not thrown: the daily reconcile and the client sweep retry.
async function acknowledgeIfNeeded(
  api: PlayApi,
  sub: PlaySubscription,
  desired: PlayDesiredState,
  purchaseToken: string,
  uid: string,
): Promise<void> {
  if (desired.status !== 'active' || sub.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_PENDING') return;
  const productId = sub.lineItems?.[0]?.productId;
  if (!productId) return;
  try {
    await api.acknowledge(productId, purchaseToken);
  } catch (error) {
    functions.logger.warn('play acknowledge failed', {
      uid,
      productId,
      reason: error instanceof Error ? error.message : String(error),
    });
  }
}

// Idempotent, ordered writer for user doc + billing doc + reverse index.
// playLastFetchedAt makes an older API read (slow verify racing a newer RTDN)
// a no-op. `linkedPurchaseToken` comes from Google (never the client): when the
// new token replaces the one that currently owns the user doc (tier change via
// SubscriptionUpdateParams), the new token inherits ownership. Without that, the
// still-active old token would block the upgrade as a "foreign" entitlement.
export async function commitPlayState(
  tx: admin.firestore.Transaction,
  refs: PlayCommitRefs,
  desired: PlayDesiredState,
  tokenHash: string,
  fetchedAtMs: number,
  now: Date,
  linkedPurchaseToken?: string,
): Promise<{ applied: boolean }> {
  const billingSnap = await tx.get(refs.billingRef);
  const billing = (billingSnap.exists ? billingSnap.data() : {}) as { playLastFetchedAt?: number };
  if (typeof billing.playLastFetchedAt === 'number' && fetchedAtMs < billing.playLastFetchedAt) {
    return { applied: false };
  }

  const userSnap = await tx.get(refs.userRef);
  const userData = (userSnap.exists ? userSnap.data() : {}) as {
    subscriptionTier?: string;
    subscriptionStatus?: string;
    subscriptionSource?: string;
    subscriptionEndsAt?: admin.firestore.Timestamp;
    playPurchaseTokenHash?: string;
  };
  const isPlay = userData.subscriptionSource === 'play';
  const ownsCurrentEntitlement = isPlay && (
    userData.playPurchaseTokenHash === tokenHash
    || (!!linkedPurchaseToken && userData.playPurchaseTokenHash === playTokenHash(linkedPurchaseToken))
  );

  const nowTs = admin.firestore.Timestamp.fromDate(now);
  const writeUserDoc = desired.status !== 'pending' && shouldWriteUserDoc({
    desiredTier: desired.tier,
    ownsCurrentEntitlement,
    current: userData,
    nowMs: now.getTime(),
  });

  if (writeUserDoc) {
    const userUpdate: Record<string, unknown> = {
      subscriptionTier: desired.tier,
      subscriptionStatus: desired.status,
      subscriptionRenews: desired.subscriptionRenews,
      subscriptionSource: 'play',
      playPurchaseToken: refs.purchaseToken,
      playPurchaseTokenHash: tokenHash,
      updatedAt: nowTs,
    };
    if (desired.endsAtMs != null) {
      userUpdate.subscriptionEndsAt = admin.firestore.Timestamp.fromMillis(desired.endsAtMs);
    }
    if (desired.productId) {
      userUpdate.playProductId = desired.productId;
    }
    tx.set(refs.userRef, userUpdate, { merge: true });
  }

  tx.set(refs.billingRef, {
    source: 'play',
    tier: desired.tier,
    productId: desired.productId,
    purchaseTokenHash: tokenHash,
    appliedTier: desired.tier,
    appliedStatus: desired.status,
    playLastFetchedAt: fetchedAtMs,
    updatedAt: nowTs,
  }, { merge: true });

  tx.set(refs.indexRef, { uid: refs.uid, updatedAt: nowTs }, { merge: true });

  return { applied: true };
}

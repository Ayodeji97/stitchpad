import * as crypto from 'crypto';
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { createAppleVerifier, createAppStoreApi, AppStoreApi } from './appleVerifier';

// Apple In-App Purchase (StoreKit 2) billing — the iOS counterpart to Paystack.
//
// iOS sells the SAME Pro/Atelier subscriptions, but App Store Guideline 3.1.1
// forces purchases through Apple IAP. This module verifies Apple's signed
// transactions / server notifications and writes the exact same server-only
// subscription fields the Paystack webhook writes (see paystackBilling.ts), so
// entitlement stays a single Firestore source of truth regardless of platform.
//
// Auto-renewable model: Apple is the renewal engine. Grants set
// `subscriptionRenews = true`, which makes the expirePrepaidSubscriptions cron
// skip them (it filters subscriptionRenews == false). Renewals, cancellations,
// grace periods and refunds arrive via App Store Server Notifications V2.

export type BillingTier = 'pro' | 'atelier';
export type BillingCadence = 'monthly' | 'annual';

const REGION = 'europe-west1';

// productId -> plan. The product IDs are configured in App Store Connect and are
// the authoritative server-side mapping — never trust a client-claimed tier.
const PRODUCT_MAP: Record<string, { tier: BillingTier; cadence: BillingCadence }> = {
  'com.danzucker.stitchpad.pro.monthly': { tier: 'pro', cadence: 'monthly' },
  'com.danzucker.stitchpad.pro.annual': { tier: 'pro', cadence: 'annual' },
  'com.danzucker.stitchpad.atelier.monthly': { tier: 'atelier', cadence: 'monthly' },
  'com.danzucker.stitchpad.atelier.annual': { tier: 'atelier', cadence: 'annual' },
};

export function planForProduct(productId: string): { tier: BillingTier; cadence: BillingCadence } | null {
  return PRODUCT_MAP[productId] ?? null;
}

// Deterministic Firebase-uid -> UUID derivation, used as StoreKit's
// `appAccountToken`. The iOS client sends the same value at purchase time
// (Swift computes SHA-256(uid), takes the first 16 bytes, formats as a UUID), so
// the server can prove a verified transaction belongs to the calling user and
// reject account-hopping (User B replaying User A's signed transaction). KEEP IN
// SYNC with StoreKitPurchaserIos.swift's appAccountToken(forUid:).
export function appleAppAccountToken(uid: string): string {
  const hex = crypto.createHash('sha256').update(uid).digest('hex').slice(0, 32);
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20, 32),
  ].join('-');
}

// A decoded, signature-verified Apple transaction (the fields we use). The real
// verifier maps Apple's JWSTransactionDecodedPayload onto this; tests inject a
// fake so they need no Apple certificates.
export interface AppleTransaction {
  transactionId: string;
  originalTransactionId: string;
  productId: string;
  expiresDate?: number; // epoch ms
  appAccountToken?: string;
  revocationDate?: number; // epoch ms; set on refund/revoke
  signedDate?: number; // epoch ms
}

// A decoded, signature-verified App Store Server Notification V2 (the fields we
// use), with its embedded transaction + renewal info already decoded.
export interface AppleNotification {
  notificationType: string;
  subtype?: string;
  notificationUUID?: string;
  signedDate?: number; // epoch ms
  transaction?: AppleTransaction;
  autoRenewStatus?: number; // 1 = on, 0 = off
}

export interface AppleVerifier {
  verifyTransaction(signedTransactionJws: string): Promise<AppleTransaction>;
  verifyNotification(signedPayload: string): Promise<AppleNotification>;
}

// Thrown when the JWS / signed payload fails Apple signature verification. The
// webhook wrapper maps this to 400 (a forged/garbage POST) and everything else
// to 500 so Apple retries genuine transient failures.
export class AppleVerificationError extends Error {}

export interface VerifyDeps {
  db: admin.firestore.Firestore;
  verifier: AppleVerifier;
  now: () => Date;
}

export interface WebhookDeps {
  db: admin.firestore.Firestore;
  verifier: AppleVerifier;
  now: () => Date;
}

export interface ReconcileDeps {
  db: admin.firestore.Firestore;
  verifier: AppleVerifier;
  api: AppStoreApi;
  now: () => Date;
}

export interface VerifyAppleTransactionRequest {
  signedTransactionJws?: unknown;
}

export interface VerifyAppleTransactionResponse {
  tier: BillingTier | 'free';
  status: 'active' | 'expired';
  endsAt: number | null;
}

// The resolved entitlement state to write, computed by the caller (callable or
// notification handler) and committed idempotently by commitAppleState.
export interface DesiredState {
  tier: BillingTier | 'free';
  status: 'active' | 'expired';
  subscriptionRenews: boolean;
  endsAtMs: number | null; // null when downgrading
  productId: string | null;
  originalTransactionId: string;
  transactionId: string | null;
  signedDate?: number;
}

interface CommitRefs {
  userRef: admin.firestore.DocumentReference;
  billingRef: admin.firestore.DocumentReference;
  indexRef: admin.firestore.DocumentReference;
  uid: string;
}

// ── Cloud Functions ───────────────────────────────────────────────────────

export const verifyAppleTransaction = functions
  .region(REGION)
  .runWith({ secrets: ['APPLE_BUNDLE_ID', 'APPLE_APP_APPLE_ID'] })
  .https.onCall(async (data, context): Promise<VerifyAppleTransactionResponse> => {
    return verifyAppleTransactionHandler(
      data as VerifyAppleTransactionRequest,
      context,
      {
        db: admin.firestore(),
        verifier: createAppleVerifier(),
        now: () => new Date(),
      },
    );
  });

export const appStoreServerNotifications = functions
  .region(REGION)
  .runWith({ secrets: ['APPLE_BUNDLE_ID', 'APPLE_APP_APPLE_ID'] })
  .https.onRequest(async (req, res) => {
    const signedPayload = (req.body as { signedPayload?: unknown })?.signedPayload;
    if (typeof signedPayload !== 'string' || !signedPayload) {
      res.status(400).send('missing_payload');
      return;
    }
    try {
      await appStoreServerNotificationsHandler(signedPayload, {
        db: admin.firestore(),
        verifier: createAppleVerifier(),
        now: () => new Date(),
      });
      res.status(200).send('ok');
    } catch (error) {
      if (error instanceof AppleVerificationError) {
        functions.logger.warn('apple notification verification failed', { reason: error.message });
        res.status(400).send('invalid');
        return;
      }
      // Transient (e.g. Firestore) failure — 500 so Apple retries the notification.
      functions.logger.error('apple notification processing failed', {
        reason: error instanceof Error ? error.message : String(error),
      });
      res.status(500).send('error');
    }
  });

export const reconcileAppleSubscriptions = functions
  .region(REGION)
  .runWith({
    secrets: [
      'APPLE_BUNDLE_ID',
      'APPLE_APP_APPLE_ID',
      'APPLE_IAP_PRIVATE_KEY',
      'APPLE_IAP_KEY_ID',
      'APPLE_IAP_ISSUER_ID',
    ],
  })
  // 02:30 Africa/Lagos daily — a belt-and-suspenders safety net that re-syncs
  // active Apple subs from the App Store Server API in case a notification was
  // dropped. Offset from the Paystack expiry/abandon crons (01:00 / 02:00).
  .pubsub.schedule('30 2 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await reconcileAppleSubscriptionsHandler({
      db: admin.firestore(),
      verifier: createAppleVerifier(),
      api: createAppStoreApi(),
      now: () => new Date(),
    });
  });

// ── Handlers (pure, dependency-injected — unit tested) ────────────────────

export async function verifyAppleTransactionHandler(
  data: VerifyAppleTransactionRequest,
  context: functions.https.CallableContext,
  deps: VerifyDeps,
): Promise<VerifyAppleTransactionResponse> {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }
  const jws = data.signedTransactionJws;
  if (typeof jws !== 'string' || !jws) {
    throw new functions.https.HttpsError('invalid-argument', 'missing_transaction');
  }

  let txn: AppleTransaction;
  try {
    txn = await deps.verifier.verifyTransaction(jws);
  } catch (error) {
    functions.logger.warn('apple transaction verification failed', {
      uid,
      reason: error instanceof Error ? error.message : String(error),
    });
    throw new functions.https.HttpsError('invalid-argument', 'apple_verification_failed');
  }

  const plan = planForProduct(txn.productId);
  if (!plan) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_plan');
  }

  // Bind the transaction to the caller. Apple embeds the appAccountToken we set
  // at purchase (a deterministic UUID of the buyer's uid); a mismatch means this
  // signed transaction belongs to a different account (replay / account-hop).
  const expectedToken = appleAppAccountToken(uid);
  if (!txn.appAccountToken || txn.appAccountToken.toLowerCase() !== expectedToken.toLowerCase()) {
    throw new functions.https.HttpsError('failed-precondition', 'account_mismatch');
  }

  // Don't grant on a revoked OR already-expired transaction. A user could
  // otherwise replay their own old (expired) signed transaction to re-grant paid
  // access until the daily reconciliation / EXPIRED notification corrects it —
  // the prepaid cron skips subscriptionRenews == true, so it wouldn't catch this.
  const revoked = typeof txn.revocationDate === 'number';
  const expired = typeof txn.expiresDate === 'number' && txn.expiresDate <= deps.now().getTime();
  const inactive = revoked || expired;
  const refs: CommitRefs = {
    userRef: deps.db.doc(`users/${uid}`),
    billingRef: deps.db.doc(`users/${uid}/billingTransactions/apple_${txn.originalTransactionId}`),
    indexRef: deps.db.doc(`appleSubscriptions/${txn.originalTransactionId}`),
    uid,
  };

  const desired: DesiredState = inactive
    ? {
      tier: 'free',
      status: 'expired',
      subscriptionRenews: false,
      endsAtMs: null,
      productId: null,
      originalTransactionId: txn.originalTransactionId,
      transactionId: txn.transactionId,
      signedDate: txn.signedDate,
    }
    : {
      tier: plan.tier,
      status: 'active',
      subscriptionRenews: true,
      endsAtMs: txn.expiresDate ?? null,
      productId: txn.productId,
      originalTransactionId: txn.originalTransactionId,
      transactionId: txn.transactionId,
      signedDate: txn.signedDate,
    };

  return deps.db.runTransaction(async (tx) => {
    // Cross-account restore guard: if this Apple subscription is already bound to
    // a DIFFERENT Firebase user, refuse — a subscription belongs to its first
    // purchaser, not whoever signs in next on the same device/Apple ID.
    const indexSnap = await tx.get(refs.indexRef);
    const boundUid = (indexSnap.data() as { uid?: string } | undefined)?.uid;
    if (boundUid && boundUid !== uid) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'subscription_belongs_to_another_account',
      );
    }
    const applied = await commitAppleState(tx, refs, desired, deps.now());
    return {
      tier: applied.tier,
      status: applied.status,
      endsAt: desired.endsAtMs,
    };
  });
}

export async function appStoreServerNotificationsHandler(
  signedPayload: string,
  deps: WebhookDeps,
): Promise<void> {
  let notification: AppleNotification;
  try {
    notification = await deps.verifier.verifyNotification(signedPayload);
  } catch (error) {
    throw new AppleVerificationError(error instanceof Error ? error.message : 'verification_failed');
  }

  const txn = notification.transaction;
  if (!txn) {
    functions.logger.info('apple notification without transaction ignored', {
      type: notification.notificationType,
    });
    return;
  }

  const desired = desiredStateForNotification(notification, txn);
  if (!desired) {
    functions.logger.info('apple notification type ignored', {
      type: notification.notificationType,
      subtype: notification.subtype,
    });
    return;
  }

  // Resolve the Firebase uid from the reverse index written at grant time. The
  // appAccountToken is a one-way hash of the uid (not reversible), so the index
  // is how a server-initiated notification finds its user.
  const indexRef = deps.db.doc(`appleSubscriptions/${txn.originalTransactionId}`);
  const indexSnap = await indexRef.get();
  const uid = (indexSnap.data() as { uid?: string } | undefined)?.uid;
  if (!uid) {
    // Not yet bound — most likely a SUBSCRIBED notification that raced the verify
    // callable that writes the reverse index. Throw so the wrapper returns 5xx and
    // Apple RETRIES later (by which point the index usually exists). We must NOT
    // 200 here: the reconciliation cron only scans already-active Apple users, so
    // a never-bound subscription would otherwise be lost forever.
    functions.logger.warn('apple notification uid unresolved — asking Apple to retry', {
      originalTransactionId: txn.originalTransactionId,
      type: notification.notificationType,
    });
    throw new Error('apple_notification_uid_unresolved');
  }

  const refs: CommitRefs = {
    userRef: deps.db.doc(`users/${uid}`),
    billingRef: deps.db.doc(`users/${uid}/billingTransactions/apple_${txn.originalTransactionId}`),
    indexRef,
    uid,
  };
  await deps.db.runTransaction(async (tx) => {
    await commitAppleState(tx, refs, desired, deps.now());
  });
}

export async function reconcileAppleSubscriptionsHandler(deps: ReconcileDeps): Promise<void> {
  const active = await deps.db.collection('users')
    .where('subscriptionSource', '==', 'apple')
    .where('subscriptionStatus', '==', 'active')
    .get();

  for (const userDoc of active.docs) {
    const uid = userDoc.id;
    const originalTransactionId = (userDoc.data() as { appleOriginalTransactionId?: string })
      .appleOriginalTransactionId;
    if (!originalTransactionId) continue;

    try {
      const latestJws = await deps.api.latestTransactionJws(originalTransactionId);
      if (!latestJws) continue;
      const txn = await deps.verifier.verifyTransaction(latestJws);
      const plan = planForProduct(txn.productId);
      const revoked = typeof txn.revocationDate === 'number';
      const expired = !revoked && typeof txn.expiresDate === 'number' && txn.expiresDate <= deps.now().getTime();

      const desired: DesiredState = (revoked || expired || !plan)
        ? {
          tier: 'free',
          status: 'expired',
          subscriptionRenews: false,
          endsAtMs: null,
          productId: null,
          originalTransactionId,
          transactionId: txn.transactionId,
          signedDate: txn.signedDate,
        }
        : {
          tier: plan.tier,
          status: 'active',
          subscriptionRenews: true,
          endsAtMs: txn.expiresDate ?? null,
          productId: txn.productId,
          originalTransactionId,
          transactionId: txn.transactionId,
          signedDate: txn.signedDate,
        };

      const refs: CommitRefs = {
        userRef: deps.db.doc(`users/${uid}`),
        billingRef: deps.db.doc(`users/${uid}/billingTransactions/apple_${originalTransactionId}`),
        indexRef: deps.db.doc(`appleSubscriptions/${originalTransactionId}`),
        uid,
      };
      await deps.db.runTransaction(async (tx) => {
        await commitAppleState(tx, refs, desired, deps.now());
      });
    } catch (error) {
      functions.logger.warn('apple reconcile failed for user', {
        uid,
        reason: error instanceof Error ? error.message : String(error),
      });
    }
  }
}

// Maps an App Store Server Notification V2 to the entitlement state to write.
// Returns null for notification types we intentionally ignore.
export function desiredStateForNotification(
  notification: AppleNotification,
  txn: AppleTransaction,
): DesiredState | null {
  const plan = planForProduct(txn.productId);
  const base = {
    originalTransactionId: txn.originalTransactionId,
    transactionId: txn.transactionId,
    signedDate: notification.signedDate ?? txn.signedDate,
  };

  switch (notification.notificationType) {
    case 'SUBSCRIBED':
    case 'DID_RENEW':
    case 'OFFER_REDEEMED':
    case 'RESUBSCRIBE':
      if (!plan) return null;
      return {
        ...base,
        tier: plan.tier,
        status: 'active',
        subscriptionRenews: true,
        endsAtMs: txn.expiresDate ?? null,
        productId: txn.productId,
      };
    case 'DID_CHANGE_RENEWAL_STATUS': {
      // User toggled auto-renew. Keep access through the paid period; only flip
      // the renews flag so the UI can show "cancels on <date>".
      if (!plan) return null;
      const enabled = notification.subtype === 'AUTO_RENEW_ENABLED'
        || notification.autoRenewStatus === 1;
      return {
        ...base,
        tier: plan.tier,
        status: 'active',
        subscriptionRenews: enabled,
        endsAtMs: txn.expiresDate ?? null,
        productId: txn.productId,
      };
    }
    case 'DID_FAIL_TO_RENEW':
      // Billing retry / grace period — keep access; EXPIRED arrives later if it
      // ultimately fails. Never downgrade a paying user mid billing-retry.
      if (!plan) return null;
      return {
        ...base,
        tier: plan.tier,
        status: 'active',
        subscriptionRenews: true,
        endsAtMs: txn.expiresDate ?? null,
        productId: txn.productId,
      };
    case 'EXPIRED':
    case 'GRACE_PERIOD_EXPIRED':
    case 'REFUND':
    case 'REVOKE':
      return {
        ...base,
        tier: 'free',
        status: 'expired',
        subscriptionRenews: false,
        endsAtMs: null,
        productId: null,
      };
    default:
      return null;
  }
}

// Idempotent, ordered writer for the user doc + billing doc + reverse index. The
// signedDate monotonic guard makes duplicate and out-of-order notifications
// no-ops (a stale DID_RENEW arriving after EXPIRED won't resurrect access).
async function commitAppleState(
  tx: admin.firestore.Transaction,
  refs: CommitRefs,
  desired: DesiredState,
  now: Date,
): Promise<{ tier: BillingTier | 'free'; status: 'active' | 'expired'; applied: boolean }> {
  const billingSnap = await tx.get(refs.billingRef);
  const billing = (billingSnap.exists ? billingSnap.data() : {}) as {
    appleLastSignedDate?: number;
    appliedTier?: BillingTier | 'free';
    appliedStatus?: 'active' | 'expired';
  };

  if (
    typeof desired.signedDate === 'number' &&
    typeof billing.appleLastSignedDate === 'number' &&
    desired.signedDate <= billing.appleLastSignedDate
  ) {
    return {
      tier: billing.appliedTier ?? 'free',
      status: billing.appliedStatus ?? 'expired',
      applied: false,
    };
  }

  const nowTs = admin.firestore.Timestamp.fromDate(now);

  // A downgrade (expire/refund/revoke) must only clear the SHARED entitlement doc
  // if it still belongs to THIS Apple subscription. If the user has since moved to
  // Paystack (subscriptionSource != 'apple') or a different Apple subscription, a
  // stale Apple notification must not revoke their current paid access. Grants
  // (active) always win — an active Apple purchase is a real, current entitlement.
  let writeUserDoc = true;
  if (desired.tier === 'free') {
    const userSnap = await tx.get(refs.userRef);
    const userData = (userSnap.exists ? userSnap.data() : {}) as {
      subscriptionSource?: string;
      appleOriginalTransactionId?: string;
    };
    writeUserDoc = userData.subscriptionSource === 'apple'
      && userData.appleOriginalTransactionId === desired.originalTransactionId;
  }

  if (writeUserDoc) {
    const userUpdate: Record<string, unknown> = {
      subscriptionTier: desired.tier,
      subscriptionStatus: desired.status,
      subscriptionRenews: desired.subscriptionRenews,
      subscriptionSource: 'apple',
      appleOriginalTransactionId: desired.originalTransactionId,
      updatedAt: nowTs,
    };
    if (desired.endsAtMs != null) {
      userUpdate.subscriptionEndsAt = admin.firestore.Timestamp.fromMillis(desired.endsAtMs);
    }
    if (desired.productId) {
      userUpdate.appleProductId = desired.productId;
    }
    tx.set(refs.userRef, userUpdate, { merge: true });
  }

  tx.set(refs.billingRef, {
    source: 'apple',
    tier: desired.tier,
    productId: desired.productId,
    originalTransactionId: desired.originalTransactionId,
    appliedTransactionId: desired.transactionId,
    appliedTier: desired.tier,
    appliedStatus: desired.status,
    appleLastSignedDate: desired.signedDate ?? billing.appleLastSignedDate ?? null,
    updatedAt: nowTs,
  }, { merge: true });

  tx.set(refs.indexRef, { uid: refs.uid, updatedAt: nowTs }, { merge: true });

  return { tier: desired.tier, status: desired.status, applied: true };
}

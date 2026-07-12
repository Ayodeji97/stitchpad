import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { deleteUserFirestoreData } from './cleanup/firestore';
import { deleteUserStorageData } from './cleanup/storage';
import { clawbackReferralOnDelete } from './referral/clawback';

if (admin.apps.length === 0) {
  admin.initializeApp();
}

export const onAuthUserDeleted = functions
  .region('europe-west1')
  .auth.user()
  .onDelete(async (user) => {
    const uid = user.uid;
    functions.logger.info('cleanup starting', { uid });

    const [firestoreResult, storageResult, clawbackResult] = await Promise.allSettled([
      deleteUserFirestoreData(uid, admin.firestore()),
      deleteUserStorageData(uid, admin.storage().bucket()),
      // Reverse any unpaid referral bounty owed for this (now-deleted) user.
      clawbackReferralOnDelete(uid, admin.firestore()),
    ]);

    // Honest completion signal: the cleanup modules currently never throw
    // (they wrap their own try/catch and log internally), so both branches
    // SHOULD always be 'fulfilled'. But logging the per-branch status here
    // means a future refactor that lets a branch reject won't silently lie
    // in Cloud Logging — operators will see firestore: 'rejected' immediately.
    functions.logger.info('cleanup completed', {
      uid,
      firestore: firestoreResult.status,
      storage: storageResult.status,
      ...(firestoreResult.status === 'rejected' && {
        firestoreError: serialiseSettledError(firestoreResult.reason),
      }),
      ...(storageResult.status === 'rejected' && {
        storageError: serialiseSettledError(storageResult.reason),
      }),
      referralClawback: clawbackResult.status === 'fulfilled' ? clawbackResult.value : 'error',
      ...(clawbackResult.status === 'rejected' && {
        clawbackError: serialiseSettledError(clawbackResult.reason),
      }),
    });
  });

function serialiseSettledError(error: unknown): unknown {
  return error instanceof Error
    ? { name: error.name, message: error.message, stack: error.stack }
    : error;
}

export { smartDraftMessage } from './smart/draftMessage';
export { reconcileCustomerSlots } from './freemium/reconcileSlots';
export { sendVerificationEmail } from './auth/sendVerificationEmail';
export { sendPasswordResetEmail } from './auth/sendPasswordResetEmail';
export { processPasswordResetEmail } from './auth/processPasswordResetEmail';
export { dailyDigest, debugSendMyDigest } from './notifications/dailyDigest';
export { pruneTokenOwnership } from './notifications/pruneTokenOwnership';
export {
  initializeSubscriptionCheckout,
  paystackWebhook,
  expirePrepaidSubscriptions,
} from './billing/paystackBilling';
export { prepaidSubscriptionReminder, debugSendMyRenewalReminder } from './billing/subscriptionReminder';
export { abandonStalePendingCheckouts } from './billing/abandonStalePending';
export {
  verifyAppleTransaction,
  appStoreServerNotifications,
  reconcileAppleSubscriptions,
} from './billing/appleBilling';
export {
  initializeGiftCheckout,
  redeemGift,
  getPublicGiftProfile,
  createGiftLink,
  expireUnclaimedGifts,
  resendUnclaimedGiftEmails,
} from './billing/giftBilling';
export { getBillingConfig } from './config/getBillingConfig';
export { whatsappWebhook } from './whatsapp';
export { createMarketer } from './referral/marketerAdmin';
export { recordReferralAttribution } from './referral/recordAttribution';
export { reconcileReferrals, debugReconcileReferrals } from './referral/reconcileReferrals';
export { confirmReferralPayouts, debugConfirmReferralPayouts } from './referral/confirmReferralPayouts';
export { sweepDeletedReferralUsers, debugSweepDeletedReferralUsers } from './referral/sweepDeletedReferralUsers';
export { getReferralDashboard } from './referral/referralDashboard';
export { markReferralPaid } from './referral/markReferralPaid';
export { dailyOnboardingMetrics, debugRunOnboardingMetrics } from './metrics/dailyOnboardingMetrics';
export { grantLaunchFreeOnSignup } from './freemium/onUserCreated';

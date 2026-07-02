import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

export interface BillingConfigResponse {
  billingEnabled: boolean;
}

// Pure handler (deps-injected, matches the codebase style).
export async function getBillingConfigHandler(deps: {
  db: admin.firestore.Firestore;
}): Promise<BillingConfigResponse> {
  const snap = await deps.db.collection('config').doc('app').get();
  return { billingEnabled: snap.get('billingEnabled') === true };
}

// Unauthenticated callable in europe-west1 (mirrors getPublicGiftProfile).
// Lets the website read the SAME billing switch the app uses (config/app.billingEnabled)
// without exposing the rest of the doc. Missing doc/field => false (safe default).
export const getBillingConfig = functions
  .region('europe-west1')
  .https.onCall(async (): Promise<BillingConfigResponse> => {
    return getBillingConfigHandler({ db: admin.firestore() });
  });

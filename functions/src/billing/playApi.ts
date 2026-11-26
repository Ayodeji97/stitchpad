import { androidpublisher, auth } from '@googleapis/androidpublisher';
import type { PlayApi, PlaySubscription } from './playBilling';

// Thin Play Developer API seam (stubbed in tests). Auth uses the functions
// runtime service account via ADC; that account needs "View financial data" and
// "Manage orders and subscriptions" in Play Console -> API access.
export const PLAY_PACKAGE_NAME = 'com.danzucker.stitchpad';

export function createPlayApi(): PlayApi {
  const client = androidpublisher({
    version: 'v3',
    auth: new auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] }),
  });
  return {
    async getSubscription(purchaseToken: string): Promise<PlaySubscription> {
      const res = await client.purchases.subscriptionsv2.get({
        packageName: PLAY_PACKAGE_NAME,
        token: purchaseToken,
      });
      const d = res.data;
      return {
        subscriptionState: d.subscriptionState ?? 'SUBSCRIPTION_STATE_UNSPECIFIED',
        acknowledgementState: d.acknowledgementState ?? undefined,
        lineItems: (d.lineItems ?? []).map((item) => ({
          productId: item.productId ?? '',
          expiryTime: item.expiryTime ?? undefined,
          autoRenewingPlan: item.autoRenewingPlan
            ? { autoRenewEnabled: item.autoRenewingPlan.autoRenewEnabled ?? undefined }
            : undefined,
        })),
        externalAccountIdentifiers: d.externalAccountIdentifiers
          ? { obfuscatedExternalAccountId: d.externalAccountIdentifiers.obfuscatedExternalAccountId ?? undefined }
          : undefined,
        linkedPurchaseToken: d.linkedPurchaseToken ?? undefined,
        testPurchase: d.testPurchase ?? undefined,
      };
    },
    async acknowledge(productId: string, purchaseToken: string): Promise<void> {
      await client.purchases.subscriptions.acknowledge({
        packageName: PLAY_PACKAGE_NAME,
        subscriptionId: productId,
        token: purchaseToken,
        requestBody: {},
      });
    },
  };
}

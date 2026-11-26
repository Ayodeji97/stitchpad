// Shared "may this store subscription write the shared user doc" rule, used by
// both the Apple and Google Play commit paths. users/{uid} holds ONE shared
// entitlement (subscriptionTier/Status/Source/EndsAt) that any provider
// (Paystack, Apple, Play, launch grant) may own. A store write must never
// reduce or steal access that belongs to a different, still-active subscription.

import { LAUNCH_GRANT_SOURCE } from '../freemium/launchGrant';

export type BillingTier = 'pro' | 'atelier';

// Tier precedence for guarding the SHARED entitlement doc: free < pro < atelier.
const TIER_RANK: Record<string, number> = { free: 0, pro: 1, atelier: 2 };
export function tierRank(tier?: string | null): number {
  return (tier != null && TIER_RANK[tier]) || 0;
}

export interface SharedEntitlement {
  subscriptionTier?: string;
  subscriptionStatus?: string;
  subscriptionSource?: string;
  subscriptionEndsAt?: { toMillis(): number };
  grantSource?: string;
}

/**
 * The user doc still holds an unpaid launch grant, identified by the exact shape
 * buildLaunchGrantFields (freemium/launchGrant.ts) writes: grantSource
 * 'launch_free', subscriptionSource deleted, and no subscriptionEndsAt (the grant
 * never sets one and is only given when none exists). grantSource is sticky, so
 * the other two checks keep paid entitlements that follow a launch grant
 * blocking: Paystack sets subscriptionSource 'paystack', and a gift sets
 * subscriptionEndsAt (gifts set no source).
 */
export function isActiveLaunchGrant(current: SharedEntitlement): boolean {
  return current.grantSource === LAUNCH_GRANT_SOURCE
    && current.subscriptionSource == null
    && current.subscriptionEndsAt == null;
}

export function shouldWriteUserDoc(args: {
  desiredTier: BillingTier | 'free';
  ownsCurrentEntitlement: boolean;
  current: SharedEntitlement;
  nowMs: number;
}): boolean {
  const { desiredTier, ownsCurrentEntitlement, current, nowMs } = args;
  if (desiredTier === 'free') {
    // A downgrade (expire/refund/revoke) must only clear the SHARED entitlement
    // doc if it still belongs to THIS store subscription. If the user has since
    // moved to another provider or a different subscription of the same store,
    // a stale notification must not revoke current access.
    return ownsCurrentEntitlement;
  }
  // An active grant wins over this store/nothing/an expired sub, but must NOT
  // take over the SHARED doc while a different subscription's entitlement is
  // still active, even at an equal or lower tier. Taking ownership would
  // overwrite subscriptionSource and the store id, so a later EXPIRED/REFUND
  // would then satisfy ownsCurrentEntitlement and revoke access that still
  // belongs to the foreign (e.g. Paystack) sub. The store sub stays recorded in
  // its billing/index docs. For Google Play, the nightly reconcile iterates the
  // playSubscriptions index and applies it once the foreign entitlement lapses.
  // Apple has the same gap today (its reconcile only scans users the Apple sub
  // already owns, so a blocked Apple sub waits for its next App Store
  // notification); Apple behavior is intentionally unchanged here.
  //
  // Exception: an active launch grant is not a paid entitlement, so a paid store
  // purchase replaces it instead of waiting behind it (deliberate, Apple too).
  const foreignActive = !ownsCurrentEntitlement
    && !isActiveLaunchGrant(current)
    && current.subscriptionStatus === 'active'
    && tierRank(current.subscriptionTier) > 0
    && (current.subscriptionEndsAt == null
      || current.subscriptionEndsAt.toMillis() > nowMs);
  return !foreignActive;
}

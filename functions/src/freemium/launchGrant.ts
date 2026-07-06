import * as admin from 'firebase-admin';

/**
 * Marker written to every promo grant so the November 2026 pricing review can
 * find launch-free users precisely and leave real Apple/Paystack/gift payers
 * (and manual tester grants) alone.
 */
export const LAUNCH_GRANT_SOURCE = 'launch_free';

/** Top tier — unlocks every entitlement cap for the free-for-everyone period. */
export const LAUNCH_GRANT_TIER = 'atelier';

export interface UserSubscriptionFields {
  subscriptionTier?: string;
  subscriptionStatus?: string;
}

/**
 * True when a user should receive the launch-free grant.
 *
 * Skips anyone already on an ACTIVE paid tier — that is real Apple/Paystack
 * subscribers, gift recipients, and manual tester grants, none of which we want
 * to relabel as `launch_free`. Everyone else (free, expired, or brand-new docs
 * with no subscription fields) is granted.
 *
 * A launch grant itself writes tier=atelier + status=active, so this predicate
 * also makes the backfill idempotent: a second run sees the granted user as
 * active-paid and skips it, preserving the original grantedAt.
 */
export function shouldGrantLaunchFree(user: UserSubscriptionFields | undefined): boolean {
  const tier = user?.subscriptionTier;
  const isPaidTier = tier === 'pro' || tier === 'atelier';
  const isActive = user?.subscriptionStatus === 'active';
  return !(isPaidTier && isActive);
}

/**
 * The exact fields a launch grant writes (merge-set).
 *
 * Explicitly CLEARS (deletes) subscriptionEndsAt, subscriptionRenews,
 * subscriptionSource, subscriptionFallbackTier, subscriptionFallbackEndsAt,
 * appleOriginalTransactionId and appleLastSignedDate. merge:true does NOT
 * remove fields by omission, so a user whose real Paystack/gift/Apple
 * subscription had lapsed would otherwise keep a past subscriptionEndsAt +
 * subscriptionRenews:false (or a stale subscriptionSource/appleOriginalTransactionId)
 * and get silently reverted back to Free within 24h by the nightly
 * expirePrepaidSubscriptions / reconcileAppleSubscriptions crons, which
 * re-select docs by those leftover fields. Explicit deletes make the grant
 * genuinely invisible to every billing cron, not just to a merge-omission.
 */
export function buildLaunchGrantFields(now: Date) {
  const ts = admin.firestore.Timestamp.fromDate(now);
  const del = admin.firestore.FieldValue.delete();
  return {
    subscriptionTier: LAUNCH_GRANT_TIER,
    subscriptionStatus: 'active',
    grantSource: LAUNCH_GRANT_SOURCE,
    grantedAt: ts,
    updatedAt: ts,
    // Clear any leftover billing fields from a prior (now-lapsed) subscription.
    // merge:true does NOT remove fields by omission, so a lapsed subscriber would
    // keep a past subscriptionEndsAt + subscriptionRenews:false and get reverted to
    // Free by the nightly expire / apple-reconcile crons within 24h. Explicit
    // deletes make the grant genuinely invisible to every billing cron.
    subscriptionEndsAt: del,
    subscriptionRenews: del,
    subscriptionSource: del,
    subscriptionFallbackTier: del,
    subscriptionFallbackEndsAt: del,
    appleOriginalTransactionId: del,
    appleLastSignedDate: del,
  };
}

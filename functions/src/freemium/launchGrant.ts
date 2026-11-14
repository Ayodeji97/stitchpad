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
  /** Any end date (Timestamp/Date) — past or future. null/undefined means "no end date". */
  subscriptionEndsAt?: unknown;
  /** `launch_free` once a launch grant has ever been written to this doc. */
  grantSource?: string;
}

/**
 * True when a user should receive the launch-free grant.
 *
 * INVARIANT — the launch grant is FIRST-TIME ONLY and can never extend or remove
 * an existing end date. Re-applying it (staff departure, a replayed trigger, a
 * later backfill) must leave the user's prior access window exactly as it was.
 * Without this, a re-grant turned an EXPIRED or time-limited user (a lapsed
 * Paystack/gift/Apple payer, or a launch_free user after the trial cutoff sets
 * subscriptionEndsAt and the expiry cron lapses them) into an endless Atelier
 * user, silently defeating the paywall/trial cutoff.
 *
 * Skips:
 *  - anyone already on an ACTIVE paid tier — real Apple/Paystack subscribers,
 *    gift recipients, manual tester grants and existing launch grants. A launch
 *    grant writes tier=atelier + status=active, so this also keeps the trigger
 *    and backfill idempotent (original grantedAt preserved).
 *  - anyone who has EVER had a launch grant (`grantSource === 'launch_free'`),
 *    whatever their current status: a lapsed launch grant stays lapsed.
 *  - anyone with ANY subscriptionEndsAt (past or future): their billing history
 *    owns their access window; past stays expired, future stays as-is.
 *
 * Everyone else (brand-new docs with no subscription fields, or free users with
 * no end date and no prior launch grant) is granted. Users who hold an endless
 * launch grant today are untouched: they read as active-paid and are skipped.
 */
export function shouldGrantLaunchFree(user: UserSubscriptionFields | undefined): boolean {
  const tier = user?.subscriptionTier;
  const isPaidTier = tier === 'pro' || tier === 'atelier';
  const isActive = user?.subscriptionStatus === 'active';
  if (isPaidTier && isActive) return false;
  if (user?.grantSource === LAUNCH_GRANT_SOURCE) return false;
  if (user?.subscriptionEndsAt != null) return false;
  return true;
}

/**
 * The exact fields a launch grant writes (merge-set).
 *
 * NEVER touches subscriptionEndsAt — see the invariant on [shouldGrantLaunchFree].
 * A launch grant only ever lands on a doc with no end date (the predicate
 * guarantees it), so there is nothing to clear; and if a purchase races in
 * between the eligibility read and this write, its end date survives and the
 * expiry cron still ends access on time instead of the grant making it endless.
 *
 * Still CLEARS (deletes) subscriptionRenews, subscriptionSource,
 * subscriptionFallbackTier, subscriptionFallbackEndsAt, appleOriginalTransactionId
 * and appleLastSignedDate. merge:true does NOT remove fields by omission, and a
 * stale subscriptionSource/appleOriginalTransactionId would let the nightly
 * reconcileAppleSubscriptions cron re-select the doc and revert it to Free.
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
    // Clear leftover billing provenance so the nightly billing crons can't re-select
    // this doc. subscriptionEndsAt is deliberately NOT here: a launch grant must
    // never extend or remove an existing end date (see shouldGrantLaunchFree).
    subscriptionRenews: del,
    subscriptionSource: del,
    subscriptionFallbackTier: del,
    subscriptionFallbackEndsAt: del,
    appleOriginalTransactionId: del,
    appleLastSignedDate: del,
  };
}

/** The `isGrantEnabled` / `writeGrant` hooks a caller extends its deps with, mirroring the
 * shape `onUserCreated.ts` uses for the onCreate trigger. */
export interface LaunchGrantFlagDeps {
  isGrantEnabled: () => Promise<boolean>;
  writeGrant: (uid: string, now: Date) => Promise<void>;
}

/**
 * The one production wiring of [LaunchGrantFlagDeps]: read the `config/app` kill-switch
 * (strict `=== true`, so a missing doc/field disables the promo rather than enabling it)
 * and merge-write [buildLaunchGrantFields].
 *
 * Every production caller — the onCreate trigger, revokeStaffMember and
 * cancelStaffMembership — must use this factory. Hand-copied wirings drift: a flag
 * renamed or a truthiness check loosened in one copy silently grants (or silently stops
 * granting) Atelier on that path alone.
 */
export function productionLaunchGrantDeps(
  db: admin.firestore.Firestore,
): LaunchGrantFlagDeps {
  return {
    isGrantEnabled: async () => {
      const snap = await db.doc('config/app').get();
      return snap.get('launchFreeGrantEnabled') === true;
    },
    writeGrant: async (uid, now) => {
      await db.doc(`users/${uid}`).set(buildLaunchGrantFields(now), { merge: true });
    },
  };
}

/**
 * Best-effort launch-free grant applied when a staff member is revoked or leaves
 * (cancels) their own membership. A staffer who never seeded their own
 * `users/{uid}` doc — e.g. they skipped Workshop Setup while still staff, since
 * staff never need one — would otherwise fall back to FREE forever once demoted:
 * `grantLaunchFreeOnSignup` only fires on doc CREATE, and nothing else creates
 * that doc for them. This reads the doc directly (a revocation has no onCreate
 * snapshot to work from, unlike the trigger), applies the same
 * [shouldGrantLaunchFree] predicate so an active paid subscriber is left alone,
 * and is idempotent — a doc already granted reads as active/atelier and is
 * skipped on any later call (owner re-invites, revokes again, etc).
 *
 * It only ever mints a FIRST-TIME grant. A staffer whose own doc already has an
 * end date (past or future) or a prior, possibly lapsed, launch grant is left in
 * exactly their prior state: departure must not turn an expired or time-limited
 * account into an endless one.
 *
 * Callers must wrap this in their own try/catch: a throw here (e.g. Firestore
 * hiccup) must never fail the revocation/cancel it is attached to.
 */
export async function grantLaunchFreeOnStaffDeparture(
  db: admin.firestore.Firestore,
  hooks: LaunchGrantFlagDeps,
  staffUid: string,
  now: Date,
): Promise<void> {
  if (!(await hooks.isGrantEnabled())) {
    return;
  }
  const snap = await db.doc(`users/${staffUid}`).get();
  const data = snap.exists ? (snap.data() as UserSubscriptionFields) : undefined;
  if (!shouldGrantLaunchFree(data)) {
    return;
  }
  await hooks.writeGrant(staffUid, now);
}

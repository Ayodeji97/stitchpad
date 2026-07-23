// Referral / marketing-attribution — shared constants & types.
//
// This feature pays real money to marketers per genuinely-active referred user,
// so the qualification math lives entirely on the server (a client can never be
// trusted to self-report activity for money). There is intentionally NO Kotlin
// mirror of the QUALIFY_* / HOLD_* thresholds below: the app never computes
// qualification — it only captures a code and submits it — so these constants
// have a single source of truth here. (Contrast reconcileSlots' WELCOME_WINDOW_DAYS,
// which the client DOES recompute and therefore must mirror.)

export const REGION = 'europe-west1';

// ── Firestore collections (all Admin-SDK only — see firestore.rules) ─────────
export const MARKETERS = 'marketers';
export const REFERRAL_CODES = 'referralCodes';
export const REFERRALS = 'referrals';
export const REFERRAL_DEVICES = 'referralDevices';

// ── Qualification thresholds (tunable) ───────────────────────────────────────
// A referred user becomes payable ("qualified") after a meaningful write on at
// least QUALIFY_DISTINCT_DAYS distinct Africa/Lagos calendar days within the
// first QUALIFY_WINDOW_DAYS days after signup. App-opens never count — only real
// writes (customers, orders, measurements). Timezone is Africa/Lagos to match the
// reconcile schedule and the tailor's real day boundaries.
export const QUALIFY_DISTINCT_DAYS = 4;
export const QUALIFY_WINDOW_DAYS = 14;

// A qualified payout is held for HOLD_WINDOW_DAYS before it can be marked
// confirmed, giving fraud checks + account-deletion clawback time to fire.
export const HOLD_WINDOW_DAYS = 7;

// Lane B: a client `createdAt` day only counts toward qualification when the
// doc's server-stamped `serverCreatedAt` lands within this many Lagos-days of it.
// Larger = kinder to tailors who sync infrequently offline; smaller = stronger
// anti-backdating. Docs with no serverCreatedAt (old binaries) bypass this and
// fall through to the Lane A ratchet. See reconcileReferrals.isServerFresh.
export const ACTIVITY_FRESHNESS_DAYS = 3;

export const DAY_MS = 24 * 60 * 60 * 1000;

// Day-boundary timezone for distinct-active-day counting. MUST match the
// .timeZone(...) on the reconcileReferrals schedule.
export const ACTIVE_DAY_TIMEZONE = 'Africa/Lagos';

// ── Referral code format ─────────────────────────────────────────────────────
// Reuses the Crockford-ish CODE_ALPHABET + generateCode() from billing/giftBilling
// (no 0/O/1/I/L). Kept short because iOS users may type it by hand into the
// "Have a referral code?" field. 31^8 ≈ 8.5e11 keeps collisions negligible.
export const REFERRAL_CODE_LENGTH = 8;

// Pretty human-facing share link. NOTE: the `/r/<code>` web landing page and the
// app-side deep-link parser for it land in Slices 4–5/8 — until then this link
// has no handler. On Android the ACTUAL attributing link is the Play link below
// (Install Referrer only populates when the user routes through the Play
// listing); the landing page's job is to redirect there carrying `referrer=` and
// to show the code for iOS manual entry. The App-Link host is already verified
// (AndroidManifest App Links + iOS associated domains).
export const REFERRAL_LINK_BASE = 'https://link.getstitchpad.com/r';
export const PLAY_PACKAGE = 'com.danzucker.stitchpad';

// ── Enumerations (stored as strings; unions kept in one place) ───────────────
export type ReferrerType = 'affiliate' | 'user';
export type PayoutKind = 'cash' | 'credit';
export type MarketerStatus = 'active' | 'disabled';
export type ReferralMilestone = 'attributed' | 'activated' | 'qualified';
export type PayoutState = 'none' | 'pending' | 'confirmed' | 'paid' | 'rejected';

// Fraud flags recorded on a referral. BLOCKING flags withhold the payout (the
// milestone still advances, but no money is ever queued/released). ADVISORY flags
// are surfaced for review without punishing the referral — `missing_device_hash`
// is advisory: a missing/malformed device hash disables device-reuse detection,
// but the referral may be perfectly legitimate, so we record the fact rather than
// silently skipping it or blocking a real payout.
export type ReferralFlag = 'self_referral' | 'device_reuse' | 'velocity' | 'missing_device_hash' | 'future_dated_activity';

// Flags that withhold a payout. Everything NOT listed here is advisory-only.
const BLOCKING_FLAGS: ReadonlySet<ReferralFlag> = new Set<ReferralFlag>([
  'self_referral',
  'device_reuse',
  'velocity',
]);

/** True if any flag present should withhold the payout (advisory flags don't). */
export function hasBlockingFlag(flags: ReferralFlag[] | undefined): boolean {
  return (flags ?? []).some((f) => BLOCKING_FLAGS.has(f));
}

// Server error markers embedded in HttpsError messages so the GitLive iOS
// wrapper can recover intent when FunctionsExceptionCode is lost.
export const ERR_REFERRAL_CODE_NOT_FOUND = 'referral_code_not_found';

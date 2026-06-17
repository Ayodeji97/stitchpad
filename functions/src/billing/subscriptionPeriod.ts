/**
 * Shared prepaid-subscription period math. Pure, side-effect-free, and unit-tested
 * independently so BOTH a Paystack purchase (paystackBilling.ts) and a gift
 * (giftBilling.ts) grant entitlement with identical stacking semantics.
 *
 * Tier/cadence types are imported type-only from paystackBilling to avoid a runtime
 * import cycle (those imports are erased at compile time).
 */

import type { BillingTier, BillingCadence } from './paystackBilling';

export interface CurrentSubscription {
  subscriptionTier?: string;
  subscriptionStatus?: string;
  subscriptionEndsAt?: unknown;
  /** Queued lower-tier segment (set by a mismatched-tier gift); runs after the active one ends. */
  subscriptionFallbackTier?: string;
  subscriptionFallbackEndsAt?: unknown;
}

export interface SubscriptionGrant {
  subscriptionTier: BillingTier;
  subscriptionEndsAt: Date;
  /**
   * Queued lower-tier segment. Gifts can produce one (higher tier runs first, the
   * lower tier is paused behind it — see docs/design/gift-stacking-spec.md). null
   * means "no queued segment"; the gift caller clears the fields, the purchase
   * caller leaves any existing fallback untouched.
   */
  fallbackTier: BillingTier | null;
  fallbackEndsAt: Date | null;
}

/** A purchase keeps today's exact behaviour; a gift additionally never downgrades. */
export type SubscriptionGrantMode = 'purchase' | 'gift';

// Calendar arithmetic so a paid period lands on the anniversary date rather than
// N fixed days later: monthly Jan 15 -> Feb 15, annual respects leap years. Fixed
// 30/365-day windows short-changed monthly subscribers (~5 days/year) and were a
// day off for annual leap-year purchases. JS normalizes month-end overflow (Jan 31
// + 1 month -> Mar 3), which is the standard subscription convention.
//
// `count` is how many periods to add (1 for a normal purchase; a gift can be N
// months or N years). count <= 0 is treated as a single period defensively.
export function addPeriods(start: Date, cadence: BillingCadence, count: number): Date {
  const n = Number.isFinite(count) && count >= 1 ? Math.floor(count) : 1;
  const d = new Date(start.getTime());
  if (cadence === 'annual') {
    d.setUTCFullYear(d.getUTCFullYear() + n);
  } else {
    d.setUTCMonth(d.getUTCMonth() + n);
  }
  return d;
}

/** One calendar period — the common case (a single-period purchase). */
export function addPeriod(start: Date, cadence: BillingCadence): Date {
  return addPeriods(start, cadence, 1);
}

/** Calendar years (leap-year safe) — used for the 12-month unclaimed-gift expiry. */
export function addYears(start: Date, years: number): Date {
  const d = new Date(start.getTime());
  d.setUTCFullYear(d.getUTCFullYear() + years);
  return d;
}

export function toDate(value: unknown): Date | null {
  if (!value) return null;
  if (value instanceof Date) return value;
  if (
    typeof value === 'object' &&
    'toDate' in value &&
    typeof (value as { toDate: () => Date }).toDate === 'function'
  ) {
    return (value as { toDate: () => Date }).toDate();
  }
  return null;
}

/**
 * Single source of truth for what tier/end-date a grant produces, given the user's
 * current subscription. Branches:
 *
 *   1. Same tier, active  -> STACK on the existing end date (true early renewal).
 *      Guards: requires an ACTIVE paid plan (both fields server-owned) so a planted
 *      `subscriptionEndsAt` on a free doc is ignored; a tier SWITCH never stacks, or
 *      the buyer would ride the higher tier on cheaper leftover days.
 *   2. Free / expired / tier switch -> fresh period from `paidAt`.
 *   3. (gift only) lower-or-equal-rank gift to a user on a HIGHER active tier ->
 *      NEVER downgrade: extend the higher tier's end date by the gifted period.
 *      A higher-rank gift to a lower active tier still starts a fresh period at the
 *      higher tier (an immediate upgrade; remaining lower-tier days are forfeited).
 *
 * `mode === 'purchase'` skips branch 3 entirely, preserving the exact pre-existing
 * Paystack behaviour (a buyer's tier switch is a deliberate choice, not a gift).
 */
export function computeSubscriptionGrant(params: {
  userData: CurrentSubscription | undefined;
  tier: BillingTier;
  cadence: BillingCadence;
  paidAt: Date;
  mode: SubscriptionGrantMode;
  /** Number of periods granted (months if monthly, years if annual). Default 1; a gift may be N. */
  quantity?: number;
}): SubscriptionGrant {
  const { userData, tier, cadence, paidAt, mode } = params;
  const quantity = params.quantity ?? 1;

  if (mode === 'gift') {
    return computeGiftGrant(userData, tier, cadence, paidAt, quantity);
  }

  // ── Purchase: unchanged single-segment behaviour (no fallback). A buyer's
  // tier switch deliberately starts fresh; only gifts queue a fallback. ──
  const onActivePaidPlan =
    (userData?.subscriptionTier === 'pro' || userData?.subscriptionTier === 'atelier') &&
    userData?.subscriptionStatus === 'active';
  const currentTier: BillingTier | null = onActivePaidPlan
    ? (userData?.subscriptionTier as BillingTier)
    : null;
  const currentEndsAt = toDate(userData?.subscriptionEndsAt);

  // Same-tier active early renewal stacks; otherwise fresh from paidAt.
  const subscriptionEndsAt =
    currentTier === tier && currentEndsAt && currentEndsAt.getTime() > paidAt.getTime()
      ? addPeriods(currentEndsAt, cadence, quantity)
      : addPeriods(paidAt, cadence, quantity);
  return { subscriptionTier: tier, subscriptionEndsAt, fallbackTier: null, fallbackEndsAt: null };
}

/**
 * Resolves the recipient's current paid time into at most two future segments
 * (Atelier and/or Pro), reading both the active row and any queued fallback.
 * Past/expired segments are dropped. `proEnd` is the ABSOLUTE end of the Pro
 * segment (which, when an Atelier segment also exists, sits AFTER it).
 */
function resolveSchedule(
  userData: CurrentSubscription | undefined,
  now: Date,
): { atelierEnd: Date | null; proEnd: Date | null } {
  let atelierEnd: Date | null = null;
  let proEnd: Date | null = null;
  const consider = (tierRaw: unknown, endRaw: unknown): void => {
    const t = tierRaw === 'pro' || tierRaw === 'atelier' ? tierRaw : null;
    const e = toDate(endRaw);
    if (!t || !e || e.getTime() <= now.getTime()) return;
    if (t === 'atelier') {
      if (!atelierEnd || e.getTime() > atelierEnd.getTime()) atelierEnd = e;
    } else if (!proEnd || e.getTime() > proEnd.getTime()) {
      proEnd = e;
    }
  };
  if (userData?.subscriptionStatus === 'active') {
    consider(userData?.subscriptionTier, userData?.subscriptionEndsAt);
  }
  consider(userData?.subscriptionFallbackTier, userData?.subscriptionFallbackEndsAt);
  return { atelierEnd, proEnd };
}

/**
 * Gift grant: higher tier runs first, the lower tier is queued (paused) behind it,
 * total time = existing remaining + gifted span. Bounded to two segments because
 * there are only two paid tiers. See docs/design/gift-stacking-spec.md.
 */
function computeGiftGrant(
  userData: CurrentSubscription | undefined,
  giftTier: BillingTier,
  cadence: BillingCadence,
  paidAt: Date,
  quantity: number,
): SubscriptionGrant {
  const { atelierEnd, proEnd } = resolveSchedule(userData, paidAt);

  if (giftTier === 'atelier') {
    // Grow the Atelier segment; the Pro segment (queued after it) preserves its own
    // duration and is re-anchored after the new Atelier end.
    const newAtelierEnd = addPeriods(atelierEnd ?? paidAt, cadence, quantity);
    const proAnchor = atelierEnd ?? paidAt;
    const proDurationMs = proEnd ? proEnd.getTime() - proAnchor.getTime() : 0;
    const newProEnd = proDurationMs > 0 ? new Date(newAtelierEnd.getTime() + proDurationMs) : null;
    return {
      subscriptionTier: 'atelier',
      subscriptionEndsAt: newAtelierEnd,
      fallbackTier: newProEnd ? 'pro' : null,
      fallbackEndsAt: newProEnd,
    };
  }

  // Pro gift. If an Atelier segment exists, Pro stays queued behind it; otherwise
  // Pro is the only/active tier and just stacks.
  if (atelierEnd) {
    const newProEnd = addPeriods(proEnd ?? atelierEnd, cadence, quantity);
    return { subscriptionTier: 'atelier', subscriptionEndsAt: atelierEnd, fallbackTier: 'pro', fallbackEndsAt: newProEnd };
  }
  const newProEnd = addPeriods(proEnd ?? paidAt, cadence, quantity);
  return { subscriptionTier: 'pro', subscriptionEndsAt: newProEnd, fallbackTier: null, fallbackEndsAt: null };
}

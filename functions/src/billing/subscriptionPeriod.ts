/**
 * Shared prepaid-subscription period math. Pure, side-effect-free, and unit-tested
 * independently so BOTH a Paystack purchase (paystackBilling.ts) and a gift
 * (giftBilling.ts) grant entitlement with identical stacking semantics.
 *
 * Tier/cadence types are imported type-only from paystackBilling to avoid a runtime
 * import cycle (those imports are erased at compile time).
 */

import type { BillingTier, BillingCadence } from './paystackBilling';

const TIER_RANK: Record<BillingTier, number> = { pro: 1, atelier: 2 };

export interface CurrentSubscription {
  subscriptionTier?: string;
  subscriptionStatus?: string;
  subscriptionEndsAt?: unknown;
}

export interface SubscriptionGrant {
  subscriptionTier: BillingTier;
  subscriptionEndsAt: Date;
}

/** A purchase keeps today's exact behaviour; a gift additionally never downgrades. */
export type SubscriptionGrantMode = 'purchase' | 'gift';

// Calendar arithmetic so a paid period lands on the anniversary date rather than
// N fixed days later: monthly Jan 15 -> Feb 15, annual respects leap years. Fixed
// 30/365-day windows short-changed monthly subscribers (~5 days/year) and were a
// day off for annual leap-year purchases. JS normalizes month-end overflow (Jan 31
// + 1 month -> Mar 3), which is the standard subscription convention.
export function addPeriod(start: Date, cadence: BillingCadence): Date {
  const d = new Date(start.getTime());
  if (cadence === 'annual') {
    d.setUTCFullYear(d.getUTCFullYear() + 1);
  } else {
    d.setUTCMonth(d.getUTCMonth() + 1);
  }
  return d;
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
}): SubscriptionGrant {
  const { userData, tier, cadence, paidAt, mode } = params;

  const onActivePaidPlan =
    (userData?.subscriptionTier === 'pro' || userData?.subscriptionTier === 'atelier') &&
    userData?.subscriptionStatus === 'active';
  const currentTier: BillingTier | null = onActivePaidPlan
    ? (userData?.subscriptionTier as BillingTier)
    : null;
  const currentEndsAt = toDate(userData?.subscriptionEndsAt);

  const extendFrom = (start: Date): Date =>
    addPeriod(currentEndsAt && currentEndsAt.getTime() > start.getTime() ? currentEndsAt : start, cadence);

  // Branch 3 — gift never downgrades an active higher tier.
  if (mode === 'gift' && currentTier && TIER_RANK[tier] < TIER_RANK[currentTier]) {
    return { subscriptionTier: currentTier, subscriptionEndsAt: extendFrom(paidAt) };
  }

  // Branch 1 — same-tier active early renewal stacks; otherwise (branch 2) fresh.
  if (currentTier === tier && currentEndsAt && currentEndsAt.getTime() > paidAt.getTime()) {
    return { subscriptionTier: tier, subscriptionEndsAt: addPeriod(currentEndsAt, cadence) };
  }
  return { subscriptionTier: tier, subscriptionEndsAt: addPeriod(paidAt, cadence) };
}

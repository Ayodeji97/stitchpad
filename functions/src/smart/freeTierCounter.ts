import { FreeTierUsageDoc, SmartFeatureKey } from './types';

export const USAGE_DEFAULT_LIMIT = 5;

export interface ReconcileArgs {
  existing: FreeTierUsageDoc | null;
  now: Date;
  /** Override the default limit (5). When provided, the returned doc carries this limit. */
  limit?: number;
}

/**
 * Pure function — given the current usage doc (or null if absent), the
 * current time, and which Smart feature is consuming the slot, returns the
 * new doc state after recording one more usage.
 *
 * Handles month rollover: if existing.monthYear differs from now's
 * YYYY-MM, count resets to 1 (counting the about-to-be-recorded usage)
 * and perFeature resets to { [featureKey]: 1 }.
 *
 * Handles back-compat: docs written before perFeature was added have
 * no `perFeature` field; on the next call we initialize it with the
 * current feature key set to 1 (existing total `count` is left alone).
 *
 * Preserves the existing limit (testers can override per-user via Firestore
 * console). When initializing fresh, uses USAGE_DEFAULT_LIMIT.
 */
export function reconcileUsage(
  args: ReconcileArgs,
  featureKey: SmartFeatureKey,
): FreeTierUsageDoc {
  const { existing, now } = args;
  const currentMonthYear = formatMonthYear(now);
  // The provided limit overrides both the default and any existing doc limit.
  // This is the upgrade story: a Free user (limit=5) upgrades to Pro (limit=50);
  // their next Smart call fixes the usage-doc limit to 50.
  const resolvedLimit = args.limit ?? existing?.limit ?? USAGE_DEFAULT_LIMIT;

  if (existing === null) {
    return {
      monthYear: currentMonthYear,
      count: 1,
      limit: resolvedLimit,
      perFeature: { [featureKey]: 1 },
      bonusBalance: 0,
    };
  }

  if (existing.monthYear !== currentMonthYear) {
    return {
      monthYear: currentMonthYear,
      count: 1,
      limit: resolvedLimit,
      perFeature: { [featureKey]: 1 },
      bonusBalance: existing.bonusBalance ?? 0,
    };
  }

  const prevPerFeature = existing.perFeature ?? {};
  return {
    monthYear: existing.monthYear,
    count: existing.count + 1,
    limit: resolvedLimit,
    perFeature: {
      ...prevPerFeature,
      [featureKey]: (prevPerFeature[featureKey] ?? 0) + 1,
    },
    bonusBalance: existing.bonusBalance ?? 0,
  };
}

export function isExhausted(doc: FreeTierUsageDoc): boolean {
  return doc.count >= doc.limit;
}

// Africa/Lagos so the monthly Smart quota rolls over at midnight Lagos local,
// matching what a Nigerian tailor expects when they read "5 free drafts a
// month". Using UTC here would shift the rollover an hour earlier in local
// time. NOTE: this is independent from the welcome-window expiry — that's a
// rolling 30-day-from-signup window per user (see reconcileSlots.ts
// welcomeEndsAtMs), not a calendar-month-aligned event.
const LAGOS_TZ = 'Africa/Lagos';
const LAGOS_MONTH_YEAR_FMT = new Intl.DateTimeFormat('en-CA', {
  timeZone: LAGOS_TZ,
  year: 'numeric',
  month: '2-digit',
});

export function formatMonthYear(d: Date): string {
  const parts = LAGOS_MONTH_YEAR_FMT.formatToParts(d);
  const year = parts.find((p) => p.type === 'year')?.value ?? '0000';
  const month = parts.find((p) => p.type === 'month')?.value ?? '00';
  return `${year}-${month}`;
}

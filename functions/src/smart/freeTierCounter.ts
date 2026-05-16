import { FreeTierUsageDoc, SmartFeatureKey } from './types';

export const USAGE_DEFAULT_LIMIT = 5;

export interface ReconcileArgs {
  existing: FreeTierUsageDoc | null;
  now: Date;
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

  if (existing === null) {
    return {
      monthYear: currentMonthYear,
      count: 1,
      limit: USAGE_DEFAULT_LIMIT,
      perFeature: { [featureKey]: 1 },
    };
  }

  if (existing.monthYear !== currentMonthYear) {
    return {
      monthYear: currentMonthYear,
      count: 1,
      limit: existing.limit,
      perFeature: { [featureKey]: 1 },
    };
  }

  const prevPerFeature = existing.perFeature ?? {};
  return {
    monthYear: existing.monthYear,
    count: existing.count + 1,
    limit: existing.limit,
    perFeature: {
      ...prevPerFeature,
      [featureKey]: (prevPerFeature[featureKey] ?? 0) + 1,
    },
  };
}

export function isExhausted(doc: FreeTierUsageDoc): boolean {
  return doc.count >= doc.limit;
}

function formatMonthYear(d: Date): string {
  const year = d.getUTCFullYear();
  const month = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

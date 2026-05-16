import { FreeTierUsageDoc } from './types';

export const USAGE_DEFAULT_LIMIT = 5;

export interface ReconcileArgs {
  existing: FreeTierUsageDoc | null;
  now: Date;
}

/**
 * Pure function — given the current usage doc (or null if absent) and the
 * current time, returns the new doc state after recording one more draft.
 *
 * Handles month rollover: if existing.monthYear differs from now's
 * YYYY-MM, count resets to 1 (counting the about-to-be-recorded draft).
 *
 * Preserves the existing limit (testers can override per-user via Firestore
 * console). When initializing fresh, uses USAGE_DEFAULT_LIMIT.
 */
export function reconcileUsage(args: ReconcileArgs): FreeTierUsageDoc {
  const { existing, now } = args;
  const currentMonthYear = formatMonthYear(now);

  if (existing === null) {
    return { monthYear: currentMonthYear, count: 1, limit: USAGE_DEFAULT_LIMIT };
  }

  if (existing.monthYear !== currentMonthYear) {
    return { monthYear: currentMonthYear, count: 1, limit: existing.limit };
  }

  return { monthYear: existing.monthYear, count: existing.count + 1, limit: existing.limit };
}

export function isExhausted(doc: FreeTierUsageDoc): boolean {
  return doc.count >= doc.limit;
}

function formatMonthYear(d: Date): string {
  const year = d.getUTCFullYear();
  const month = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

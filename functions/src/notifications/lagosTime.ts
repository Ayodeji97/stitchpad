/**
 * Africa/Lagos is UTC+1 year-round (no DST), so a fixed +1h offset is exact.
 * Shifting the epoch by the offset and reading it as UTC gives the Lagos
 * calendar day without pulling in a timezone library.
 */
export const DAY_MS = 86_400_000;
export const LAGOS_OFFSET_MS = 3_600_000;

/** Whole-day index in Lagos time. Two timestamps on the same Lagos date share an index. */
export function lagosDayIndex(epochMillis: number): number {
  return Math.floor((epochMillis + LAGOS_OFFSET_MS) / DAY_MS);
}

/** Lagos calendar date as 'YYYY-MM-DD' (used as the idempotency key). */
export function lagosDateKey(epochMillis: number): string {
  return new Date(epochMillis + LAGOS_OFFSET_MS).toISOString().slice(0, 10);
}

/**
 * Lagos day of week: 0=Sunday … 6=Saturday, matching cron's numbering.
 *
 * Epoch day 0 (1970-01-01) was a Thursday, hence the +4 shift. The second
 * modulo guards pre-epoch inputs, where `%` in JS yields a negative result.
 */
export function lagosWeekday(epochMillis: number): number {
  return (((lagosDayIndex(epochMillis) + 4) % 7) + 7) % 7;
}

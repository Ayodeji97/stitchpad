import { lagosDayIndex, lagosDateKey, lagosWeekday, LAGOS_OFFSET_MS, DAY_MS } from '../../notifications/lagosTime';

describe('lagosTime', () => {
  // 2026-06-03T05:30:00Z = 2026-06-03 06:30 Lagos (UTC+1)
  const morningUtc = Date.parse('2026-06-03T05:30:00Z');
  // 2026-06-03T23:30:00Z = 2026-06-04 00:30 Lagos — crosses the day boundary
  const lateUtc = Date.parse('2026-06-03T23:30:00Z');

  it('LAGOS_OFFSET_MS is +1h, DAY_MS is 24h', () => {
    expect(LAGOS_OFFSET_MS).toBe(3_600_000);
    expect(DAY_MS).toBe(86_400_000);
  });

  it('lagosDayIndex puts a late-evening UTC time on the next Lagos day', () => {
    expect(lagosDayIndex(lateUtc)).toBe(lagosDayIndex(morningUtc) + 1);
  });

  it('lagosDateKey returns the Lagos calendar date, not the UTC date', () => {
    expect(lagosDateKey(morningUtc)).toBe('2026-06-03');
    expect(lagosDateKey(lateUtc)).toBe('2026-06-04');
  });

  describe('lagosWeekday', () => {
    // Cron numbering: 0=Sunday … 6=Saturday. These are the two days the
    // engagement push is scheduled on, so they are worth pinning by name.
    it('returns 2 for a Tuesday and 5 for a Friday', () => {
      expect(lagosWeekday(Date.parse('2026-08-18T09:00:00Z'))).toBe(2); // Tue
      expect(lagosWeekday(Date.parse('2026-08-21T09:00:00Z'))).toBe(5); // Fri
    });

    it('returns 3 for a Wednesday', () => {
      expect(lagosWeekday(Date.parse('2026-08-19T09:00:00Z'))).toBe(3);
      expect(lagosWeekday(morningUtc)).toBe(3); // 2026-06-03 was a Wednesday
    });

    // The whole reason this is Lagos-aware: 23:30 UTC Monday is already Tuesday
    // in Lagos. A naive UTC weekday would skip the Tuesday send entirely.
    it('uses the Lagos calendar day across the UTC midnight boundary', () => {
      expect(lagosWeekday(Date.parse('2026-08-17T22:00:00Z'))).toBe(1); // still Mon in Lagos
      expect(lagosWeekday(Date.parse('2026-08-17T23:30:00Z'))).toBe(2); // Tue in Lagos
    });

    it('covers a full week with no gaps or repeats', () => {
      const base = Date.parse('2026-08-16T09:00:00Z'); // a Sunday in Lagos
      const week = Array.from({ length: 7 }, (_, i) => lagosWeekday(base + i * DAY_MS));
      expect(week).toEqual([0, 1, 2, 3, 4, 5, 6]);
    });

    it('never returns a negative value for pre-epoch input', () => {
      expect(lagosWeekday(-10 * DAY_MS)).toBeGreaterThanOrEqual(0);
      expect(lagosWeekday(-10 * DAY_MS)).toBeLessThanOrEqual(6);
    });
  });
});

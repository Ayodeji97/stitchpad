import { lagosDayIndex, lagosDateKey, LAGOS_OFFSET_MS, DAY_MS } from '../../notifications/lagosTime';

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
});

import { FreeTierUsageDoc } from '../../smart/types';
import { reconcileUsage, isExhausted, USAGE_DEFAULT_LIMIT } from '../../smart/freeTierCounter';

const today = new Date('2026-05-16T10:00:00Z');

describe('reconcileUsage', () => {
  it('initializes a fresh doc when none exists', () => {
    const next = reconcileUsage({ existing: null, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 1, limit: USAGE_DEFAULT_LIMIT });
  });

  it('increments count when same month', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-05', count: 3, limit: 5 };
    const next = reconcileUsage({ existing, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 4, limit: 5 });
  });

  it('resets count to 1 on month rollover', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-04', count: 5, limit: 5 };
    const next = reconcileUsage({ existing, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 1, limit: 5 });
  });

  it('preserves a custom limit on month rollover (testers override)', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-04', count: 5, limit: 100 };
    const next = reconcileUsage({ existing, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 1, limit: 100 });
  });

  it('preserves a custom limit on same-month increment', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-05', count: 50, limit: 100 };
    const next = reconcileUsage({ existing, now: today });
    expect(next.limit).toBe(100);
  });

  it('zero-pads single-digit months in monthYear', () => {
    const earlyJan = new Date('2026-01-05T10:00:00Z');
    const next = reconcileUsage({ existing: null, now: earlyJan });
    expect(next.monthYear).toBe('2026-01');
  });
});

describe('isExhausted', () => {
  it('false when count is below limit', () => {
    expect(isExhausted({ monthYear: '2026-05', count: 4, limit: 5 })).toBe(false);
  });

  it('true when count equals limit', () => {
    expect(isExhausted({ monthYear: '2026-05', count: 5, limit: 5 })).toBe(true);
  });

  it('true when count exceeds limit (defensive)', () => {
    expect(isExhausted({ monthYear: '2026-05', count: 6, limit: 5 })).toBe(true);
  });
});

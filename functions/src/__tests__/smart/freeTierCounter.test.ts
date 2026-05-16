import { FreeTierUsageDoc, SmartFeatureKey } from '../../smart/types';
import { reconcileUsage, isExhausted, USAGE_DEFAULT_LIMIT } from '../../smart/freeTierCounter';

const today = new Date('2026-05-16T10:00:00Z');

describe('reconcileUsage', () => {
  it('initializes a fresh doc when none exists', () => {
    const next = reconcileUsage({ existing: null, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 1,
      limit: USAGE_DEFAULT_LIMIT,
      perFeature: { draft: 1 },
    });
  });

  it('increments count when same month', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-05',
      count: 3,
      limit: 5,
      perFeature: { draft: 3 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 4,
      limit: 5,
      perFeature: { draft: 4 },
    });
  });

  it('resets count to 1 on month rollover', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-04',
      count: 5,
      limit: 5,
      perFeature: { draft: 5 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 1,
      limit: 5,
      perFeature: { draft: 1 },
    });
  });

  it('preserves a custom limit on month rollover (testers override)', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-04',
      count: 5,
      limit: 100,
      perFeature: { draft: 5 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 1,
      limit: 100,
      perFeature: { draft: 1 },
    });
  });

  it('preserves a custom limit on same-month increment', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-05',
      count: 50,
      limit: 100,
      perFeature: { draft: 50 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next.limit).toBe(100);
  });

  it('zero-pads single-digit months in monthYear', () => {
    const earlyJan = new Date('2026-01-05T10:00:00Z');
    const next = reconcileUsage({ existing: null, now: earlyJan }, 'draft');
    expect(next.monthYear).toBe('2026-01');
  });
});

describe('reconcileUsage perFeature tagging', () => {
  it('initializes perFeature with featureKey=1 on a fresh doc', () => {
    const next = reconcileUsage({ existing: null, now: today }, 'postcaption');
    expect(next.perFeature).toEqual({ postcaption: 1 });
  });

  it('increments the specified feature key without touching others', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-05',
      count: 4,
      limit: 5,
      perFeature: { draft: 3, postcaption: 1 },
    };
    const next = reconcileUsage({ existing, now: today }, 'postcaption');
    expect(next.perFeature).toEqual({ draft: 3, postcaption: 2 });
    expect(next.count).toBe(5);
  });

  it('back-compat: treats missing perFeature on existing doc as empty', () => {
    // A doc written before perFeature was added — count is set but the
    // map is absent. New increment must still record the feature key.
    const existing = {
      monthYear: '2026-05',
      count: 2,
      limit: 5,
    } as FreeTierUsageDoc;
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next.perFeature).toEqual({ draft: 1 });
    expect(next.count).toBe(3);
  });

  it('resets perFeature to single-entry on month rollover', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-04',
      count: 5,
      limit: 5,
      perFeature: { draft: 3, postcaption: 2 },
    };
    const next = reconcileUsage({ existing, now: today }, 'referral_msg');
    expect(next.perFeature).toEqual({ referral_msg: 1 });
  });

  it('accepts each SmartFeatureKey value', () => {
    const keys: SmartFeatureKey[] = [
      'draft',
      'postcaption',
      'referral_msg',
      'referral_bio',
      'contentplan_regen',
    ];
    for (const key of keys) {
      const next = reconcileUsage({ existing: null, now: today }, key);
      expect(next.perFeature?.[key]).toBe(1);
    }
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

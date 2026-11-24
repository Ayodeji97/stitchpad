import {
  daysSinceDateKey, digestEmailWeekday, isPaidSubscriber, parseDigestEmailAudience,
} from '../../notifications/digestAudience';

const NOW = Date.parse('2026-11-24T06:00:00Z');
const DAY = 86_400_000;
const ts = (ms: number) => ({ toMillis: () => ms });

describe('isPaidSubscriber', () => {
  it('counts a running Paystack or Apple plan', () => {
    for (const source of ['paystack', 'apple']) {
      expect(isPaidSubscriber({
        subscriptionSource: source, subscriptionStatus: 'active', subscriptionEndsAt: ts(NOW + DAY),
      }, NOW)).toBe(true);
    }
  });

  it('accepts a Date or epoch millis end date too', () => {
    const base = { subscriptionSource: 'paystack', subscriptionStatus: 'active' };
    expect(isPaidSubscriber({ ...base, subscriptionEndsAt: new Date(NOW + DAY) }, NOW)).toBe(true);
    expect(isPaidSubscriber({ ...base, subscriptionEndsAt: NOW + DAY }, NOW)).toBe(true);
  });

  it('does not count a launch-grant Atelier user with no end date', () => {
    expect(isPaidSubscriber({
      subscriptionTier: 'atelier', subscriptionStatus: 'active', grantSource: 'launch_free',
    }, NOW)).toBe(false);
  });

  it('does not count an expired Paystack plan', () => {
    expect(isPaidSubscriber({
      subscriptionSource: 'paystack', subscriptionStatus: 'active', subscriptionEndsAt: ts(NOW - DAY),
    }, NOW)).toBe(false);
  });

  it('does not count a non-active status or a missing doc', () => {
    expect(isPaidSubscriber({
      subscriptionSource: 'apple', subscriptionStatus: 'expired', subscriptionEndsAt: ts(NOW + DAY),
    }, NOW)).toBe(false);
    expect(isPaidSubscriber(undefined, NOW)).toBe(false);
  });
});

describe('parseDigestEmailAudience', () => {
  it('defaults to paid for missing or unknown values', () => {
    expect(parseDigestEmailAudience(undefined)).toBe('paid');
    expect(parseDigestEmailAudience('everyone')).toBe('paid');
    expect(parseDigestEmailAudience('none')).toBe('none');
    expect(parseDigestEmailAudience('all')).toBe('all');
  });
});

describe('digestEmailWeekday', () => {
  it('is stable and within 0..6', () => {
    expect(digestEmailWeekday('abc')).toBe(digestEmailWeekday('abc'));
    for (let i = 0; i < 200; i++) {
      const d = digestEmailWeekday(`uid-${i}`);
      expect(d).toBeGreaterThanOrEqual(0);
      expect(d).toBeLessThan(7);
    }
  });

  it('spreads uids across every day of the week', () => {
    const counts = new Array(7).fill(0);
    for (let i = 0; i < 700; i++) counts[digestEmailWeekday(`user${i}xYz`)]++;
    for (const c of counts) expect(c).toBeGreaterThan(60);
  });
});

describe('daysSinceDateKey', () => {
  it('counts whole Lagos days', () => {
    expect(daysSinceDateKey('2026-11-24', NOW)).toBe(0);
    expect(daysSinceDateKey('2026-11-18', NOW)).toBe(6);
    expect(daysSinceDateKey(null, NOW)).toBeNull();
    expect(daysSinceDateKey('garbage', NOW)).toBeNull();
  });
});

import { digestEmailAudience, digestEmailWeekday, hasPaidDigestAccess, isWeeklyDigestDue } from '../../notifications/digestEmailPolicy';

const NOW = Date.parse('2026-11-25T06:00:00Z'); // Wednesday, u1's assigned Lagos day.
const DAY = 86_400_000;

describe('paid weekly digest policy', () => {
  it.each(['paystack', 'apple'])('requires live, active %s payment even with an old launch grant', (subscriptionSource) => {
    const paid = { subscriptionSource, subscriptionStatus: 'active', subscriptionEndsAt: NOW + 1, grantSource: 'launch_free' };
    expect(hasPaidDigestAccess(paid, NOW)).toBe(true);
    expect(hasPaidDigestAccess({ ...paid, subscriptionEndsAt: NOW }, NOW)).toBe(false);
    expect(hasPaidDigestAccess({ ...paid, subscriptionEndsAt: NOW - DAY }, NOW)).toBe(false);
    expect(hasPaidDigestAccess({ ...paid, subscriptionStatus: 'expired' }, NOW)).toBe(false);
  });

  it.each([{}, { subscriptionTier: 'atelier', grantSource: 'launch_free' },
    { subscriptionSource: 'gift', subscriptionStatus: 'active', subscriptionEndsAt: NOW + DAY },
    { subscriptionSource: 'paystack', subscriptionStatus: 'active', subscriptionEndsAt: '9999999999999' },
    { subscriptionSource: 'apple', subscriptionStatus: 'active', subscriptionEndsAt: Infinity },
  ])('does not infer payment from a tier, grant or invalid metadata: %j', (data) => {
    expect(hasPaidDigestAccess(data, NOW)).toBe(false);
  });

  it('defaults missing/invalid audience to paid and only permits explicit overrides', () => {
    for (const value of [undefined, null, true, {}, 'ALL', 'invalid', 'paid']) expect(digestEmailAudience(value)).toBe('paid');
    expect(digestEmailAudience('none')).toBe('none');
    expect(digestEmailAudience('all')).toBe('all');
  });

  it('assigns a stable day and spreads owners across all seven weekdays', () => {
    expect(digestEmailWeekday('u1')).toBe(3);
    expect(digestEmailWeekday('u8')).toBe(3);
    expect(new Set(Array.from({ length: 1000 }, (_, i) => digestEmailWeekday(`owner-${i}`))).size).toBe(7);
  });

  it('checks Lagos midnight, not the UTC weekday', () => {
    expect(isWeeklyDigestDue('u1', null, Date.parse('2026-11-24T22:59:59.999Z'))).toBe(false);
    expect(isWeeklyDigestDue('u1', null, Date.parse('2026-11-24T23:00:00Z'))).toBe(true);
  });

  it('keeps the six-day guard for reruns, manual stamps and future dates', () => {
    for (const last of ['2026-11-25', '2026-11-24', '2026-11-20', '2026-11-26', 'invalid', '2026-02-30']) {
      expect(isWeeklyDigestDue('u1', last, NOW)).toBe(false);
    }
    expect(isWeeklyDigestDue('u1', '2026-11-19', NOW)).toBe(true);
    expect(isWeeklyDigestDue('u1', '2026-11-18', NOW)).toBe(true);
    expect(isWeeklyDigestDue('u1', null, NOW + DAY)).toBe(false);
  });
});

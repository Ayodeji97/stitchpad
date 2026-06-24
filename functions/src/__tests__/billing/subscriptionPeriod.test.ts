import * as admin from 'firebase-admin';
import {
  addPeriod,
  addPeriods,
  addYears,
  computeSubscriptionGrant,
} from '../../billing/subscriptionPeriod';

function ts(iso: string): admin.firestore.Timestamp {
  return admin.firestore.Timestamp.fromDate(new Date(iso));
}

const paidAt = new Date('2026-06-01T10:00:00Z');

describe('computeSubscriptionGrant — purchase mode (existing behaviour)', () => {
  it('grants a fresh period from paidAt for a brand-new user', () => {
    const grant = computeSubscriptionGrant({
      userData: undefined, tier: 'pro', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionTier).toBe('pro');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('stacks a same-tier early renewal on the existing end date', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-06-20T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-20T00:00:00.000Z');
  });

  it('starts a fresh period on a tier switch instead of stacking', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-06-20T00:00:00Z') },
      tier: 'atelier', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('ignores a planted end date on a non-paid (free) user', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'free', subscriptionStatus: 'active', subscriptionEndsAt: ts('2050-01-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('does NOT apply the gift never-downgrade rule in purchase mode', () => {
    // A buyer deliberately switching from Atelier to Pro is honoured (fresh Pro period).
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-12-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionTier).toBe('pro');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('PRESERVES remaining Pro time when upgrading Pro -> Atelier (queue-behind)', () => {
    // Fixed dates so the expected ms are exact (this is money — no approximation).
    const upgradePaidAt = new Date('2026-05-01T00:00:00Z');
    const grant = computeSubscriptionGrant({
      userData: {
        subscriptionTier: 'pro',
        subscriptionStatus: 'active',
        // 92 days of Pro remaining (2026-05-01 -> 2026-08-01).
        subscriptionEndsAt: ts('2026-08-01T00:00:00Z'),
      },
      tier: 'atelier', cadence: 'monthly', paidAt: upgradePaidAt, mode: 'purchase',
    });
    // Atelier runs now for one calendar month from paidAt: setUTCMonth(+1) -> 2026-06-01.
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-06-01T00:00:00.000Z');
    expect(grant.subscriptionEndsAt.getTime()).toBe(1780272000000);
    // The 92 days of Pro are queued behind the Atelier window: atelier-end + 92d.
    expect(grant.fallbackTier).toBe('pro');
    expect(grant.fallbackEndsAt?.toISOString()).toBe('2026-09-01T00:00:00.000Z');
    expect(grant.fallbackEndsAt?.getTime()).toBe(1788220800000);
    // The preserved Pro duration equals exactly the original remaining time.
    const preservedMs = grant.fallbackEndsAt!.getTime() - grant.subscriptionEndsAt.getTime();
    expect(preservedMs).toBe(new Date('2026-08-01T00:00:00Z').getTime() - upgradePaidAt.getTime());
  });

  it('downgrade Atelier -> Pro starts a fresh Pro period, no fallback', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-12-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionTier).toBe('pro');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
    expect(grant.fallbackTier).toBeNull();
    expect(grant.fallbackEndsAt).toBeNull();
  });

  it('expired plan starts a fresh period regardless of tier, no fallback', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'pro', subscriptionStatus: 'expired', subscriptionEndsAt: ts('2026-08-01T00:00:00Z') },
      tier: 'atelier', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
    expect(grant.fallbackTier).toBeNull();
    expect(grant.fallbackEndsAt).toBeNull();
  });
});

describe('computeSubscriptionGrant — gift mode', () => {
  it('grants a fresh period from paidAt for a free recipient', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'free', subscriptionStatus: 'active' },
      tier: 'pro', cadence: 'annual', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('pro');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2027-06-01T10:00:00.000Z');
  });

  it('stacks a same-tier gift on the active end date', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-06-20T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('pro');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-20T00:00:00.000Z');
  });

  it('case D — Pro gift to an active Atelier user: Atelier keeps running, Pro queued after', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-12-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-12-01T00:00:00.000Z'); // unchanged
    expect(grant.fallbackTier).toBe('pro');
    expect(grant.fallbackEndsAt?.toISOString()).toBe('2027-01-01T00:00:00.000Z'); // Atelier end + 1 month
  });

  it('treats a lapsed higher tier as gone — the gift becomes the gifted tier', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-01-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('pro');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
    expect(grant.fallbackTier).toBeNull();
  });

  it('case C — Atelier gift to an active Pro user: upgrade now, Pro paused + queued after', () => {
    const proEnd = ts('2026-10-01T10:00:00Z'); // 4 months remaining
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: proEnd },
      tier: 'atelier', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z'); // Atelier 1 month now
    expect(grant.fallbackTier).toBe('pro');
    // Pro's remaining duration is preserved (paused), re-anchored after the Atelier window.
    const proDurationAfter = grant.fallbackEndsAt!.getTime() - grant.subscriptionEndsAt.getTime();
    expect(proDurationAfter).toBe(proEnd.toDate().getTime() - paidAt.getTime());
  });

  it('case E — extends the matching segment when a fallback is already queued', () => {
    const grant = computeSubscriptionGrant({
      userData: {
        subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-08-01T10:00:00Z'),
        subscriptionFallbackTier: 'pro', subscriptionFallbackEndsAt: ts('2026-10-01T10:00:00Z'),
      },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-08-01T10:00:00.000Z'); // Atelier unchanged
    expect(grant.fallbackTier).toBe('pro');
    expect(grant.fallbackEndsAt?.toISOString()).toBe('2026-11-01T10:00:00.000Z'); // Pro segment + 1 month
  });
});

describe('addYears', () => {
  it('adds calendar years for the 12-month unclaimed expiry', () => {
    expect(addYears(new Date('2026-06-01T10:00:00Z'), 1).toISOString()).toBe('2027-06-01T10:00:00.000Z');
  });

  it('is leap-year safe (Feb 29 -> Mar 1)', () => {
    expect(addYears(new Date('2028-02-29T00:00:00Z'), 1).toISOString()).toBe('2029-03-01T00:00:00.000Z');
  });
});

describe('addPeriod (re-exported)', () => {
  it('adds one calendar month', () => {
    expect(addPeriod(new Date('2027-03-15T10:00:00Z'), 'monthly').toISOString()).toBe('2027-04-15T10:00:00.000Z');
  });
});

describe('addPeriods (multi-period)', () => {
  it('adds N calendar months', () => {
    expect(addPeriods(new Date('2026-06-01T10:00:00Z'), 'monthly', 3).toISOString()).toBe('2026-09-01T10:00:00.000Z');
  });
  it('adds N calendar years', () => {
    expect(addPeriods(new Date('2026-06-01T10:00:00Z'), 'annual', 2).toISOString()).toBe('2028-06-01T10:00:00.000Z');
  });
  it('clamps a non-positive count to a single period', () => {
    expect(addPeriods(new Date('2026-06-01T10:00:00Z'), 'monthly', 0).toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });
});

describe('computeSubscriptionGrant — quantity', () => {
  it('grants N months for a free recipient (gift of 3 monthly)', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'free', subscriptionStatus: 'active' },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift', quantity: 3,
    });
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-09-01T10:00:00.000Z');
  });
  it('stacks N years onto an active same-tier end date', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-12-01T00:00:00Z') },
      tier: 'atelier', cadence: 'annual', paidAt, mode: 'gift', quantity: 2,
    });
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2028-12-01T00:00:00.000Z');
  });
  it('defaults to 1 period when quantity is omitted (purchase path unchanged)', () => {
    const grant = computeSubscriptionGrant({
      userData: undefined, tier: 'pro', cadence: 'monthly', paidAt, mode: 'purchase',
    });
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });
});

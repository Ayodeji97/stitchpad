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

  it('never downgrades: a Pro gift to an active Atelier user extends Atelier', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-12-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift',
    });
    // Stays Atelier; one gifted month added to the existing Atelier end date.
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2027-01-01T00:00:00.000Z');
  });

  it('never downgrades but extends from paidAt when the higher tier already lapsed in time', () => {
    // Active flag set but end date already in the past — extend from paidAt, keep tier.
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'atelier', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-01-01T00:00:00Z') },
      tier: 'pro', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('upgrades: an Atelier gift to an active Pro user starts a fresh Atelier period', () => {
    const grant = computeSubscriptionGrant({
      userData: { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: ts('2026-12-01T00:00:00Z') },
      tier: 'atelier', cadence: 'monthly', paidAt, mode: 'gift',
    });
    expect(grant.subscriptionTier).toBe('atelier');
    expect(grant.subscriptionEndsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
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

import { shouldWriteUserDoc, tierRank } from '../../billing/storeEntitlement';

const NOW = new Date('2026-11-24T12:00:00Z').getTime();
const DAY = 24 * 60 * 60 * 1000;
const at = (ms: number) => ({ toMillis: () => ms });

describe('shouldWriteUserDoc', () => {
  it('downgrade_onlyWhenOwned', () => {
    expect(shouldWriteUserDoc({ desiredTier: 'free', ownsCurrentEntitlement: true, current: {}, nowMs: NOW })).toBe(true);
    expect(shouldWriteUserDoc({ desiredTier: 'free', ownsCurrentEntitlement: false, current: {}, nowMs: NOW })).toBe(false);
  });

  it('foreignActive_blocksTakeover', () => {
    expect(shouldWriteUserDoc({
      desiredTier: 'atelier',
      ownsCurrentEntitlement: false,
      current: {
        subscriptionSource: 'paystack', subscriptionStatus: 'active', subscriptionTier: 'pro',
        subscriptionEndsAt: at(NOW + DAY),
      },
      nowMs: NOW,
    })).toBe(false);
  });

  it('foreignExpired_allowsTakeover', () => {
    expect(shouldWriteUserDoc({
      desiredTier: 'atelier',
      ownsCurrentEntitlement: false,
      current: {
        subscriptionSource: 'paystack', subscriptionStatus: 'active', subscriptionTier: 'pro',
        subscriptionEndsAt: at(NOW - DAY),
      },
      nowMs: NOW,
    })).toBe(true);
  });

  it('foreignNoEndDate_blocksTakeover', () => {
    expect(shouldWriteUserDoc({
      desiredTier: 'pro',
      ownsCurrentEntitlement: false,
      current: { subscriptionSource: 'launch_free', subscriptionStatus: 'active', subscriptionTier: 'pro' },
      nowMs: NOW,
    })).toBe(false);
  });

  it('ownedGrant_writes', () => {
    expect(shouldWriteUserDoc({
      desiredTier: 'pro',
      ownsCurrentEntitlement: true,
      current: {
        subscriptionSource: 'apple', subscriptionStatus: 'active', subscriptionTier: 'atelier',
        subscriptionEndsAt: at(NOW + DAY),
      },
      nowMs: NOW,
    })).toBe(true);
  });

  it('freeForeign_allowsTakeover', () => {
    expect(shouldWriteUserDoc({
      desiredTier: 'pro', ownsCurrentEntitlement: false,
      current: { subscriptionStatus: 'active', subscriptionTier: 'free' }, nowMs: NOW,
    })).toBe(true);
  });
});

describe('tierRank', () => {
  it('orders free < pro < atelier, unknown is 0', () => {
    expect(tierRank('free')).toBe(0);
    expect(tierRank('pro')).toBe(1);
    expect(tierRank('atelier')).toBe(2);
    expect(tierRank(undefined)).toBe(0);
    expect(tierRank('weird')).toBe(0);
  });
});

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
    // An endless entitlement that is NOT a launch grant (e.g. a manual tester grant).
    expect(shouldWriteUserDoc({
      desiredTier: 'pro',
      ownsCurrentEntitlement: false,
      current: { subscriptionStatus: 'active', subscriptionTier: 'pro' },
      nowMs: NOW,
    })).toBe(false);
  });

  it('activeLaunchGrant_doesNotBlockPaidStorePurchase', () => {
    // Exact shape buildLaunchGrantFields writes: grantSource launch_free, no
    // subscriptionSource, no subscriptionEndsAt. It is not a paid entitlement.
    expect(shouldWriteUserDoc({
      desiredTier: 'pro',
      ownsCurrentEntitlement: false,
      current: { grantSource: 'launch_free', subscriptionStatus: 'active', subscriptionTier: 'atelier' },
      nowMs: NOW,
    })).toBe(true);
  });

  it('paystackAfterLaunchGrant_stillBlocks', () => {
    // grantSource is sticky; a later Paystack purchase sets subscriptionSource.
    expect(shouldWriteUserDoc({
      desiredTier: 'atelier',
      ownsCurrentEntitlement: false,
      current: {
        grantSource: 'launch_free', subscriptionSource: 'paystack', subscriptionStatus: 'active',
        subscriptionTier: 'pro', subscriptionEndsAt: at(NOW + DAY),
      },
      nowMs: NOW,
    })).toBe(false);
  });

  it('giftAfterLaunchGrant_stillBlocks', () => {
    // Gifts keep grantSource and set no subscriptionSource, but always an end date.
    expect(shouldWriteUserDoc({
      desiredTier: 'atelier',
      ownsCurrentEntitlement: false,
      current: {
        grantSource: 'launch_free', subscriptionStatus: 'active', subscriptionTier: 'pro',
        subscriptionEndsAt: at(NOW + DAY),
      },
      nowMs: NOW,
    })).toBe(false);
  });

  it('launchGrant_downgradeStillRequiresOwnership', () => {
    expect(shouldWriteUserDoc({
      desiredTier: 'free',
      ownsCurrentEntitlement: false,
      current: { grantSource: 'launch_free', subscriptionStatus: 'active', subscriptionTier: 'atelier' },
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

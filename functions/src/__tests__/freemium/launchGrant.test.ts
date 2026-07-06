import {
  shouldGrantLaunchFree,
  buildLaunchGrantFields,
  LAUNCH_GRANT_SOURCE,
} from '../../freemium/launchGrant';

describe('shouldGrantLaunchFree', () => {
  it('grants a brand-new doc with no subscription fields', () => {
    expect(shouldGrantLaunchFree(undefined)).toBe(true);
    expect(shouldGrantLaunchFree({})).toBe(true);
  });

  it('grants an active free user', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'free', subscriptionStatus: 'active' }),
    ).toBe(true);
  });

  it('grants an expired former subscriber', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'pro', subscriptionStatus: 'expired' }),
    ).toBe(true);
  });

  it('skips an active paid subscriber (real payer / tester / gift)', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'pro', subscriptionStatus: 'active' }),
    ).toBe(false);
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'atelier', subscriptionStatus: 'active' }),
    ).toBe(false);
  });
});

describe('buildLaunchGrantFields', () => {
  it('sets tier=atelier + active + tag, and omits expiry/renew/source', () => {
    const fields = buildLaunchGrantFields(new Date('2026-07-06T00:00:00Z'));
    expect(fields.subscriptionTier).toBe('atelier');
    expect(fields.subscriptionStatus).toBe('active');
    expect(fields.grantSource).toBe(LAUNCH_GRANT_SOURCE);
    expect(fields).not.toHaveProperty('subscriptionEndsAt');
    expect(fields).not.toHaveProperty('subscriptionRenews');
    expect(fields).not.toHaveProperty('subscriptionSource');
  });
});

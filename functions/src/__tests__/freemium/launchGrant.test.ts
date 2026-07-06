import * as admin from 'firebase-admin';
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
  it('sets tier=atelier + active + tag, and deletes stale billing fields', () => {
    const fields = buildLaunchGrantFields(new Date('2026-07-06T00:00:00Z'));
    expect(fields.subscriptionTier).toBe('atelier');
    expect(fields.subscriptionStatus).toBe('active');
    expect(fields.grantSource).toBe(LAUNCH_GRANT_SOURCE);

    const deleteSentinel = admin.firestore.FieldValue.delete();
    const staleFields = [
      'subscriptionEndsAt',
      'subscriptionRenews',
      'subscriptionSource',
      'subscriptionFallbackTier',
      'subscriptionFallbackEndsAt',
      'appleOriginalTransactionId',
      'appleLastSignedDate',
    ] as const;

    for (const field of staleFields) {
      expect(deleteSentinel.isEqual(fields[field])).toBe(true);
    }
  });
});

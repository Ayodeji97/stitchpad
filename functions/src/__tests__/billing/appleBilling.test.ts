import * as functions from 'firebase-functions/v1';
import {
  AppleNotification,
  AppleTransaction,
  AppleVerificationError,
  AppleVerifier,
  appleAppAccountToken,
  appStoreServerNotificationsHandler,
  desiredStateForNotification,
  planForProduct,
  verifyAppleTransactionHandler,
} from '../../billing/appleBilling';

const PRO_MONTHLY = 'com.danzucker.stitchpad.pro.monthly';
const ATELIER_MONTHLY = 'com.danzucker.stitchpad.atelier.monthly';

// In-memory Firestore double that also drives runTransaction, mirroring the
// paystackBilling tests. A single store backs both plain .doc().get()/.set()
// and the transactional tx.get()/tx.set() used by the Apple handlers.
function fakeDb(initial: Record<string, any> = {}) {
  const store = new Map(Object.entries(initial));
  const doc = (path: string) => ({
    path,
    get: jest.fn(async () => ({ exists: store.has(path), data: () => store.get(path) })),
    set: jest.fn(async (data: any, options?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, options?.merge ? { ...prev, ...data } : data);
    }),
  });
  const tx = {
    get: jest.fn(async (ref: any) => ({ exists: store.has(ref.path), data: () => store.get(ref.path) })),
    set: jest.fn((ref: any, data: any, options?: { merge?: boolean }) => {
      const prev = store.get(ref.path) ?? {};
      store.set(ref.path, options?.merge ? { ...prev, ...data } : data);
    }),
  };
  const db = {
    doc,
    runTransaction: jest.fn(async (fn: any) => fn(tx)),
    collection: jest.fn(),
  };
  return { store, db };
}

function fakeVerifier(overrides: Partial<AppleVerifier> = {}): AppleVerifier {
  return {
    verifyTransaction: jest.fn(async () => { throw new Error('not stubbed'); }),
    verifyNotification: jest.fn(async () => { throw new Error('not stubbed'); }),
    ...overrides,
  };
}

function txnFor(uid: string, overrides: Partial<AppleTransaction> = {}): AppleTransaction {
  return {
    transactionId: 'txn-1',
    originalTransactionId: 'orig-1',
    productId: PRO_MONTHLY,
    expiresDate: new Date('2026-07-01T10:00:00Z').getTime(),
    appAccountToken: appleAppAccountToken(uid),
    signedDate: new Date('2026-06-01T10:00:00Z').getTime(),
    ...overrides,
  };
}

const authCtx = (uid: string) => ({ auth: { uid } } as unknown as functions.https.CallableContext);

describe('verifyAppleTransactionHandler', () => {
  it('rejects unauthenticated callers', async () => {
    const { db } = fakeDb();
    await expect(verifyAppleTransactionHandler(
      { signedTransactionJws: 'jws' },
      {} as functions.https.CallableContext,
      { db: db as never, verifier: fakeVerifier(), now: () => new Date() },
    )).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('rejects a missing transaction payload', async () => {
    const { db } = fakeDb();
    await expect(verifyAppleTransactionHandler(
      {},
      authCtx('uid-1'),
      { db: db as never, verifier: fakeVerifier(), now: () => new Date() },
    )).rejects.toMatchObject({ code: 'invalid-argument' });
  });

  it('rejects an unknown product id', async () => {
    const { db } = fakeDb();
    const verifier = fakeVerifier({
      verifyTransaction: jest.fn(async () => txnFor('uid-1', { productId: 'com.danzucker.stitchpad.unknown' })),
    });
    await expect(verifyAppleTransactionHandler(
      { signedTransactionJws: 'jws' },
      authCtx('uid-1'),
      { db: db as never, verifier, now: () => new Date() },
    )).rejects.toMatchObject({ message: 'invalid_plan' });
  });

  it('rejects when appAccountToken does not match the caller (account-hop)', async () => {
    const { db } = fakeDb();
    // Transaction carries a different user's account token.
    const verifier = fakeVerifier({
      verifyTransaction: jest.fn(async () => txnFor('someone-else')),
    });
    await expect(verifyAppleTransactionHandler(
      { signedTransactionJws: 'jws' },
      authCtx('uid-1'),
      { db: db as never, verifier, now: () => new Date() },
    )).rejects.toMatchObject({ message: 'account_mismatch' });
  });

  it('grants the tier, marks renews=true and source=apple, and is idempotent', async () => {
    const { db, store } = fakeDb();
    const verifier = fakeVerifier({ verifyTransaction: jest.fn(async () => txnFor('uid-1')) });
    const deps = { db: db as never, verifier, now: () => new Date('2026-06-01T10:01:00Z') };

    const result = await verifyAppleTransactionHandler({ signedTransactionJws: 'jws' }, authCtx('uid-1'), deps);
    // Re-running the same transaction (signedDate guard) is a no-op, not a double-apply.
    await verifyAppleTransactionHandler({ signedTransactionJws: 'jws' }, authCtx('uid-1'), deps);

    expect(result.tier).toBe('pro');
    expect(store.get('users/uid-1')).toMatchObject({
      subscriptionTier: 'pro',
      subscriptionStatus: 'active',
      subscriptionRenews: true,
      subscriptionSource: 'apple',
      appleOriginalTransactionId: 'orig-1',
      appleProductId: PRO_MONTHLY,
    });
    expect((store.get('users/uid-1').subscriptionEndsAt.toDate() as Date).toISOString())
      .toBe('2026-07-01T10:00:00.000Z');
    // Reverse index binds the subscription to this uid.
    expect(store.get('appleSubscriptions/orig-1')).toMatchObject({ uid: 'uid-1' });
  });

  it('does not grant paid access for an already-expired transaction (replay guard)', async () => {
    const { db, store } = fakeDb();
    const verifier = fakeVerifier({
      verifyTransaction: jest.fn(async () => txnFor('uid-1', {
        expiresDate: new Date('2026-05-01T10:00:00Z').getTime(), // already past at `now`
      })),
    });
    const result = await verifyAppleTransactionHandler(
      { signedTransactionJws: 'jws' },
      authCtx('uid-1'),
      { db: db as never, verifier, now: () => new Date('2026-06-01T10:00:00Z') },
    );
    expect(result).toMatchObject({ tier: 'free', status: 'expired' });
    // The replay granted no paid access — the shared entitlement doc was never
    // given a paid tier (there was nothing to downgrade; the guard wrote nothing).
    expect(store.get('users/uid-1')?.subscriptionTier).not.toBe('pro');
  });

  it('refuses to grant a subscription already bound to another account', async () => {
    const { db } = fakeDb({ 'appleSubscriptions/orig-1': { uid: 'first-owner' } });
    const verifier = fakeVerifier({ verifyTransaction: jest.fn(async () => txnFor('uid-2')) });
    await expect(verifyAppleTransactionHandler(
      { signedTransactionJws: 'jws' },
      authCtx('uid-2'),
      { db: db as never, verifier, now: () => new Date() },
    )).rejects.toMatchObject({ message: 'subscription_belongs_to_another_account' });
  });

  it('maps a verification failure to invalid-argument', async () => {
    const { db } = fakeDb();
    const verifier = fakeVerifier({ verifyTransaction: jest.fn(async () => { throw new Error('bad sig'); }) });
    await expect(verifyAppleTransactionHandler(
      { signedTransactionJws: 'jws' },
      authCtx('uid-1'),
      { db: db as never, verifier, now: () => new Date() },
    )).rejects.toMatchObject({ code: 'invalid-argument', message: 'apple_verification_failed' });
  });
});

describe('appStoreServerNotificationsHandler', () => {
  function notification(type: string, txn: AppleTransaction, extra: Partial<AppleNotification> = {}): AppleNotification {
    return { notificationType: type, signedDate: txn.signedDate, transaction: txn, ...extra };
  }

  it('throws AppleVerificationError on signature failure (→ 400, not retried)', async () => {
    const { db } = fakeDb();
    const verifier = fakeVerifier({ verifyNotification: jest.fn(async () => { throw new Error('bad sig'); }) });
    await expect(appStoreServerNotificationsHandler('payload', {
      db: db as never, verifier, now: () => new Date(),
    })).rejects.toBeInstanceOf(AppleVerificationError);
  });

  it('throws (→ 5xx, Apple retries) when the uid cannot be resolved yet', async () => {
    const { db, store } = fakeDb(); // no reverse index entry
    const verifier = fakeVerifier({
      verifyNotification: jest.fn(async () => notification('DID_RENEW', txnFor('uid-1'))),
    });
    // A non-verification throw makes the wrapper return 5xx so Apple retries once
    // the verify callable has written the reverse index. It must NOT be an
    // AppleVerificationError (that would 400 and drop the notification).
    await expect(appStoreServerNotificationsHandler('payload', {
      db: db as never, verifier, now: () => new Date(),
    })).rejects.not.toBeInstanceOf(AppleVerificationError);
    expect(store.get('users/uid-1')).toBeUndefined();
  });

  it('extends the period on DID_RENEW', async () => {
    const { db, store } = fakeDb({ 'appleSubscriptions/orig-1': { uid: 'uid-1' } });
    const renewedTxn = txnFor('uid-1', {
      transactionId: 'txn-2',
      expiresDate: new Date('2026-08-01T10:00:00Z').getTime(),
      signedDate: new Date('2026-07-01T10:00:00Z').getTime(),
    });
    const verifier = fakeVerifier({ verifyNotification: jest.fn(async () => notification('DID_RENEW', renewedTxn)) });
    await appStoreServerNotificationsHandler('payload', {
      db: db as never, verifier, now: () => new Date('2026-07-01T10:01:00Z'),
    });
    expect(store.get('users/uid-1')).toMatchObject({ subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionRenews: true });
    expect((store.get('users/uid-1').subscriptionEndsAt.toDate() as Date).toISOString()).toBe('2026-08-01T10:00:00.000Z');
  });

  it('downgrades to free on EXPIRED', async () => {
    const { db, store } = fakeDb({
      'appleSubscriptions/orig-1': { uid: 'uid-1' },
      'users/uid-1': {
        subscriptionTier: 'pro',
        subscriptionStatus: 'active',
        subscriptionRenews: true,
        subscriptionSource: 'apple',
        appleOriginalTransactionId: 'orig-1',
      },
    });
    const verifier = fakeVerifier({
      verifyNotification: jest.fn(async () => notification('EXPIRED', txnFor('uid-1', {
        signedDate: new Date('2026-07-02T00:00:00Z').getTime(),
      }))),
    });
    await appStoreServerNotificationsHandler('payload', {
      db: db as never, verifier, now: () => new Date('2026-07-02T00:01:00Z'),
    });
    expect(store.get('users/uid-1')).toMatchObject({ subscriptionTier: 'free', subscriptionStatus: 'expired', subscriptionRenews: false });
  });

  it('downgrades to free on REFUND', async () => {
    const { db, store } = fakeDb({
      'appleSubscriptions/orig-1': { uid: 'uid-1' },
      'users/uid-1': {
        subscriptionTier: 'atelier',
        subscriptionStatus: 'active',
        subscriptionRenews: true,
        subscriptionSource: 'apple',
        appleOriginalTransactionId: 'orig-1',
      },
    });
    const verifier = fakeVerifier({
      verifyNotification: jest.fn(async () => notification('REFUND', txnFor('uid-1', {
        productId: ATELIER_MONTHLY,
        signedDate: new Date('2026-06-15T00:00:00Z').getTime(),
      }))),
    });
    await appStoreServerNotificationsHandler('payload', {
      db: db as never, verifier, now: () => new Date('2026-06-15T00:01:00Z'),
    });
    expect(store.get('users/uid-1')).toMatchObject({ subscriptionTier: 'free', subscriptionStatus: 'expired' });
  });

  it('does not downgrade when the user has since switched to Paystack', async () => {
    // User had an Apple sub, cancelled, then bought a Paystack plan. A delayed
    // Apple EXPIRED for the OLD sub must NOT revoke the active Paystack grant.
    const { db, store } = fakeDb({
      'appleSubscriptions/orig-1': { uid: 'uid-1' },
      'users/uid-1': {
        subscriptionTier: 'pro',
        subscriptionStatus: 'active',
        subscriptionSource: 'paystack',
      },
    });
    const verifier = fakeVerifier({
      verifyNotification: jest.fn(async () => notification('EXPIRED', txnFor('uid-1', {
        signedDate: new Date('2026-07-02T00:00:00Z').getTime(),
      }))),
    });
    await appStoreServerNotificationsHandler('payload', {
      db: db as never, verifier, now: () => new Date('2026-07-02T00:01:00Z'),
    });
    // Paystack entitlement is preserved.
    expect(store.get('users/uid-1')).toMatchObject({
      subscriptionTier: 'pro',
      subscriptionStatus: 'active',
      subscriptionSource: 'paystack',
    });
  });

  it('ignores a stale, out-of-order notification (older signedDate)', async () => {
    const { db, store } = fakeDb({
      'appleSubscriptions/orig-1': { uid: 'uid-1' },
      'users/uid-1': {
        subscriptionTier: 'pro',
        subscriptionStatus: 'active',
        subscriptionSource: 'apple',
        appleOriginalTransactionId: 'orig-1',
      },
    });
    const verifier = fakeVerifier({
      verifyNotification: jest.fn()
        // Newer EXPIRED applied first…
        .mockResolvedValueOnce(notification('EXPIRED', txnFor('uid-1', {
          signedDate: new Date('2026-07-10T00:00:00Z').getTime(),
        })))
        // …then a stale DID_RENEW arrives late and must NOT resurrect access.
        .mockResolvedValueOnce(notification('DID_RENEW', txnFor('uid-1', {
          expiresDate: new Date('2026-08-01T00:00:00Z').getTime(),
          signedDate: new Date('2026-07-05T00:00:00Z').getTime(),
        }))),
    });
    const deps = { db: db as never, verifier, now: () => new Date('2026-07-10T00:01:00Z') };
    await appStoreServerNotificationsHandler('payload', deps);
    await appStoreServerNotificationsHandler('payload', deps);
    expect(store.get('users/uid-1')).toMatchObject({ subscriptionTier: 'free', subscriptionStatus: 'expired' });
  });
});

describe('desiredStateForNotification', () => {
  it('flips renews=false on auto-renew disabled but keeps access', () => {
    const txn = txnFor('uid-1');
    const desired = desiredStateForNotification(
      { notificationType: 'DID_CHANGE_RENEWAL_STATUS', subtype: 'AUTO_RENEW_DISABLED', transaction: txn },
      txn,
    );
    expect(desired).toMatchObject({ tier: 'pro', status: 'active', subscriptionRenews: false });
  });

  it('keeps access during a grace-period billing retry (DID_FAIL_TO_RENEW)', () => {
    const txn = txnFor('uid-1');
    const desired = desiredStateForNotification(
      { notificationType: 'DID_FAIL_TO_RENEW', subtype: 'GRACE_PERIOD', transaction: txn },
      txn,
    );
    expect(desired).toMatchObject({ tier: 'pro', status: 'active' });
  });

  it('ignores unrelated notification types', () => {
    const txn = txnFor('uid-1');
    expect(desiredStateForNotification({ notificationType: 'TEST', transaction: txn }, txn)).toBeNull();
  });
});

describe('apple billing helpers', () => {
  it('maps product ids to plans', () => {
    expect(planForProduct(PRO_MONTHLY)).toEqual({ tier: 'pro', cadence: 'monthly' });
    expect(planForProduct('com.danzucker.stitchpad.atelier.annual')).toEqual({ tier: 'atelier', cadence: 'annual' });
    expect(planForProduct('nope')).toBeNull();
  });

  it('derives a stable UUID-shaped appAccountToken from a uid', () => {
    const token = appleAppAccountToken('uid-1');
    expect(token).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    // Deterministic — same uid always yields the same token (client/server must agree).
    expect(appleAppAccountToken('uid-1')).toBe(token);
    expect(appleAppAccountToken('uid-2')).not.toBe(token);
  });
});

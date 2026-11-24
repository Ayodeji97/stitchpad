import * as functions from 'firebase-functions/v1';
import {
  PlayApi,
  PlaySubscription,
  commitPlayState,
  desiredStateForPlay,
  playAccountId,
  playTokenHash,
  verifyPlayPurchaseHandler,
} from '../../billing/playBilling';

const PRO_MONTHLY = 'com.danzucker.stitchpad.pro.monthly';
const ATELIER_MONTHLY = 'com.danzucker.stitchpad.atelier.monthly';
const NOW = new Date('2026-11-24T12:00:00Z');
const DAY = 24 * 60 * 60 * 1000;
const iso = (ms: number) => new Date(ms).toISOString();
const ts = (ms: number) => ({ toMillis: () => ms });

// In-memory Firestore double (same pattern as appleBilling.test.ts), plus a
// minimal collection().where().where().get() for the reconcile query.
function fakeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const doc = (path: string) => ({
    path,
    id: path.split('/').pop(),
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
  const collection = jest.fn((name: string) => {
    const filters: [string, unknown][] = [];
    const query: any = {
      where: jest.fn((field: string, _op: string, value: unknown) => {
        filters.push([field, value]);
        return query;
      }),
      get: jest.fn(async () => {
        const docs = [...store.entries()]
          .filter(([path]) => path.startsWith(`${name}/`) && path.split('/').length === 2)
          .filter(([, data]) => filters.every(([f, v]) => data?.[f] === v))
          .map(([path, data]) => ({ id: path.split('/')[1], data: () => data }));
        return { docs };
      }),
    };
    return query;
  });
  const db = { doc, runTransaction: jest.fn(async (fn: any) => fn(tx)), collection };
  return { store, db, tx };
}

function fakeApi(sub: PlaySubscription | Error, overrides: Partial<PlayApi> = {}): PlayApi {
  return {
    getSubscription: jest.fn(async () => {
      if (sub instanceof Error) throw sub;
      return sub;
    }),
    acknowledge: jest.fn(async () => undefined),
    ...overrides,
  };
}

function subFor(uid: string, overrides: Partial<PlaySubscription> = {}): PlaySubscription {
  return {
    subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    lineItems: [{ productId: PRO_MONTHLY, expiryTime: iso(NOW.getTime() + 30 * DAY), autoRenewingPlan: { autoRenewEnabled: true } }],
    externalAccountIdentifiers: { obfuscatedExternalAccountId: playAccountId(uid) },
    ...overrides,
  };
}

const authCtx = (uid: string) => ({ auth: { uid } } as unknown as functions.https.CallableContext);
const deps = (db: any, api: PlayApi) => ({ db: db as never, api, now: () => NOW });

const ORIGINAL_ENV = process.env.PLAY_ALLOW_TEST_PURCHASES;
afterEach(() => {
  if (ORIGINAL_ENV === undefined) delete process.env.PLAY_ALLOW_TEST_PURCHASES;
  else process.env.PLAY_ALLOW_TEST_PURCHASES = ORIGINAL_ENV;
});

describe('desiredStateForPlay', () => {
  const nowMs = NOW.getTime();
  const expiry = nowMs + 10 * DAY;
  const base = (state: string, extra: Partial<PlaySubscription> = {}): PlaySubscription => ({
    subscriptionState: state,
    lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(expiry), autoRenewingPlan: { autoRenewEnabled: true } }],
    ...extra,
  });

  it('mapper_active', () => {
    expect(desiredStateForPlay(base('SUBSCRIPTION_STATE_ACTIVE'), nowMs)).toEqual({
      tier: 'atelier', status: 'active', subscriptionRenews: true, endsAtMs: expiry, productId: ATELIER_MONTHLY,
    });
  });

  it('mapper_active_autoRenewMissing_defaultsTrue', () => {
    const sub = base('SUBSCRIPTION_STATE_ACTIVE', { lineItems: [{ productId: PRO_MONTHLY, expiryTime: iso(expiry) }] });
    expect(desiredStateForPlay(sub, nowMs)).toMatchObject({ tier: 'pro', status: 'active', subscriptionRenews: true });
  });

  it('mapper_gracePeriod', () => {
    expect(desiredStateForPlay(base('SUBSCRIPTION_STATE_IN_GRACE_PERIOD'), nowMs)).toMatchObject({
      tier: 'atelier', status: 'active', subscriptionRenews: true, endsAtMs: expiry,
    });
  });

  it('mapper_canceledBeforeExpiry_keepsAccessNoRenew', () => {
    expect(desiredStateForPlay(base('SUBSCRIPTION_STATE_CANCELED'), nowMs)).toEqual({
      tier: 'atelier', status: 'active', subscriptionRenews: false, endsAtMs: expiry, productId: ATELIER_MONTHLY,
    });
  });

  it.each([
    ['SUBSCRIPTION_STATE_CANCELED', nowMs - DAY],
    ['SUBSCRIPTION_STATE_ON_HOLD', expiry],
    ['SUBSCRIPTION_STATE_PAUSED', expiry],
    ['SUBSCRIPTION_STATE_EXPIRED', nowMs - DAY],
    ['SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED', expiry],
  ])('mapper_%s_isExpired', (state, exp) => {
    const sub = base(state, { lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(exp) }] });
    expect(desiredStateForPlay(sub, nowMs)).toMatchObject({
      tier: 'free', status: 'expired', subscriptionRenews: false, endsAtMs: null,
    });
  });

  it('mapper_pending', () => {
    expect(desiredStateForPlay(base('SUBSCRIPTION_STATE_PENDING'), nowMs)).toMatchObject({
      tier: 'free', status: 'pending', subscriptionRenews: false, endsAtMs: null,
    });
  });

  it('mapper_unknownProduct_isExpired', () => {
    const sub = base('SUBSCRIPTION_STATE_ACTIVE', { lineItems: [{ productId: 'com.other.thing', expiryTime: iso(expiry) }] });
    expect(desiredStateForPlay(sub, nowMs)).toMatchObject({
      tier: 'free', status: 'expired', subscriptionRenews: false, endsAtMs: null,
    });
  });
});

describe('playAccountId / playTokenHash', () => {
  it('is lowercase sha256 hex', () => {
    expect(playAccountId('abc')).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
    expect(playTokenHash('abc')).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});

describe('verifyPlayPurchaseHandler', () => {
  it('verify_unauthenticated', async () => {
    const { db } = fakeDb();
    await expect(verifyPlayPurchaseHandler(
      { purchaseToken: 'tok' }, {} as functions.https.CallableContext, deps(db, fakeApi(subFor('u1'))),
    )).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('verify_missingToken', async () => {
    const { db } = fakeDb();
    await expect(verifyPlayPurchaseHandler({}, authCtx('u1'), deps(db, fakeApi(subFor('u1')))))
      .rejects.toMatchObject({ code: 'invalid-argument', message: 'missing_purchase_token' });
  });

  it('verify_accountMismatch', async () => {
    const { db, store } = fakeDb();
    await expect(verifyPlayPurchaseHandler(
      { purchaseToken: 'tok' }, authCtx('u1'), deps(db, fakeApi(subFor('other'))),
    )).rejects.toMatchObject({ code: 'failed-precondition', message: 'account_mismatch' });
    await expect(verifyPlayPurchaseHandler(
      { purchaseToken: 'tok' }, authCtx('u1'),
      deps(db, fakeApi(subFor('u1', { externalAccountIdentifiers: undefined }))),
    )).rejects.toMatchObject({ code: 'failed-precondition', message: 'account_mismatch' });
    expect(store.size).toBe(0);
  });

  it('verify_testPurchaseRefused', async () => {
    delete process.env.PLAY_ALLOW_TEST_PURCHASES;
    const { db, store } = fakeDb();
    const api = fakeApi(subFor('u1', { testPurchase: {} }));
    await expect(verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api)))
      .rejects.toMatchObject({ code: 'failed-precondition', message: 'test_purchase_not_allowed' });
    expect(store.size).toBe(0);

    process.env.PLAY_ALLOW_TEST_PURCHASES = 'true';
    const result = await verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api));
    expect(result.tier).toBe('pro');
    expect(store.get('users/u1')?.subscriptionTier).toBe('pro');
  });

  it('verify_active_grantsAndAcknowledges', async () => {
    const { db, store } = fakeDb();
    const sub = subFor('u1');
    const api = fakeApi(sub);
    const result = await verifyPlayPurchaseHandler({ purchaseToken: 'tok-a' }, authCtx('u1'), deps(db, api));
    const expiry = Date.parse(sub.lineItems[0].expiryTime!);
    expect(result).toEqual({ tier: 'pro', status: 'active', endsAt: expiry });

    const user = store.get('users/u1');
    expect(user).toMatchObject({
      subscriptionTier: 'pro',
      subscriptionStatus: 'active',
      subscriptionRenews: true,
      subscriptionSource: 'play',
      playPurchaseToken: 'tok-a',
      playPurchaseTokenHash: playTokenHash('tok-a'),
      playProductId: PRO_MONTHLY,
    });
    expect(user.subscriptionEndsAt.toMillis()).toBe(expiry);
    expect(store.get(`playSubscriptions/${playTokenHash('tok-a')}`)).toMatchObject({ uid: 'u1' });
    expect(store.get(`users/u1/billingTransactions/play_${playTokenHash('tok-a')}`)).toMatchObject({
      source: 'play', tier: 'pro', productId: PRO_MONTHLY, appliedStatus: 'active', playLastFetchedAt: NOW.getTime(),
    });
    expect(api.getSubscription).toHaveBeenCalledWith('tok-a');
    expect(api.acknowledge).toHaveBeenCalledTimes(1);
    expect(api.acknowledge).toHaveBeenCalledWith(PRO_MONTHLY, 'tok-a');
  });

  it('verify_alreadyAcknowledged_noAckCall', async () => {
    const { db } = fakeDb();
    const api = fakeApi(subFor('u1', { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' }));
    await verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api));
    expect(api.acknowledge).not.toHaveBeenCalled();
  });

  it('verify_ackFailure_isNotThrown', async () => {
    const { db, store } = fakeDb();
    const api = fakeApi(subFor('u1'), { acknowledge: jest.fn(async () => { throw new Error('boom'); }) });
    await expect(verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api)))
      .resolves.toMatchObject({ tier: 'pro' });
    expect(store.get('users/u1')?.subscriptionTier).toBe('pro');
  });

  it('verify_pending_noUserWrite', async () => {
    const { db, store } = fakeDb({ 'users/u1': { subscriptionTier: 'free', subscriptionStatus: 'expired' } });
    const api = fakeApi(subFor('u1', { subscriptionState: 'SUBSCRIPTION_STATE_PENDING' }));
    const result = await verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api));
    expect(result.status).toBe('pending');
    expect(store.get('users/u1')).toEqual({ subscriptionTier: 'free', subscriptionStatus: 'expired' });
    expect(store.get(`playSubscriptions/${playTokenHash('tok')}`)).toMatchObject({ uid: 'u1' });
    expect(api.acknowledge).not.toHaveBeenCalled();
  });

  it('verify_tokenBoundToOtherUid', async () => {
    const { db, store } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'someone-else' } });
    await expect(verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, fakeApi(subFor('u1')))))
      .rejects.toMatchObject({ code: 'failed-precondition', message: 'subscription_belongs_to_another_account' });
    expect(store.has('users/u1')).toBe(false);
  });

  it('activePaystack_isNotOverwrittenByPlay', async () => {
    const paystackUser = {
      subscriptionSource: 'paystack', subscriptionStatus: 'active', subscriptionTier: 'pro',
      subscriptionEndsAt: ts(NOW.getTime() + DAY),
    };
    const { db, store } = fakeDb({ 'users/u1': paystackUser });
    const api = fakeApi(subFor('u1', {
      lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(NOW.getTime() + 30 * DAY) }],
    }));
    await verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api));
    expect(store.get('users/u1')).toBe(paystackUser);
    expect(store.get(`users/u1/billingTransactions/play_${playTokenHash('tok')}`)).toMatchObject({ tier: 'atelier' });
    expect(store.get(`playSubscriptions/${playTokenHash('tok')}`)).toMatchObject({ uid: 'u1' });
  });

  it('upgrade_linkedToken_takesOverOwnedPlayEntitlement', async () => {
    // Pro on token A, then Atelier bought with replacement: Play returns token B
    // with linkedPurchaseToken = A. B must take over, not be blocked as "foreign".
    const { db, store } = fakeDb({
      'users/u1': {
        subscriptionSource: 'play', subscriptionStatus: 'active', subscriptionTier: 'pro',
        subscriptionEndsAt: ts(NOW.getTime() + 20 * DAY),
        playPurchaseToken: 'tok-a', playPurchaseTokenHash: playTokenHash('tok-a'),
      },
    });
    const api = fakeApi(subFor('u1', {
      linkedPurchaseToken: 'tok-a',
      lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(NOW.getTime() + 30 * DAY) }],
    }));
    await verifyPlayPurchaseHandler({ purchaseToken: 'tok-b' }, authCtx('u1'), deps(db, api));
    expect(store.get('users/u1')).toMatchObject({
      subscriptionTier: 'atelier', playPurchaseToken: 'tok-b', playPurchaseTokenHash: playTokenHash('tok-b'),
    });
  });

  it('apiFailure_mapsToUnavailable', async () => {
    const { db } = fakeDb();
    await expect(verifyPlayPurchaseHandler(
      { purchaseToken: 'tok' }, authCtx('u1'), deps(db, fakeApi(new Error('500 from Google'))),
    )).rejects.toMatchObject({ code: 'unavailable', message: 'play_unavailable' });
  });
});

describe('commitPlayState', () => {
  const refsFor = (db: any, uid: string, token: string) => ({
    userRef: db.doc(`users/${uid}`),
    billingRef: db.doc(`users/${uid}/billingTransactions/play_${playTokenHash(token)}`),
    indexRef: db.doc(`playSubscriptions/${playTokenHash(token)}`),
    uid,
    purchaseToken: token,
  });
  const expired = { tier: 'free' as const, status: 'expired' as const, subscriptionRenews: false, endsAtMs: null, productId: null };

  it('staleOldTokenExpiry_doesNotRevokeNewToken', async () => {
    const owned = {
      subscriptionSource: 'play', subscriptionStatus: 'active', subscriptionTier: 'atelier',
      subscriptionEndsAt: ts(NOW.getTime() + 20 * DAY),
      playPurchaseToken: 'tok-b', playPurchaseTokenHash: playTokenHash('tok-b'),
    };
    const { db, store, tx } = fakeDb({ 'users/u1': owned });
    await commitPlayState(tx as never, refsFor(db, 'u1', 'tok-a'), expired, playTokenHash('tok-a'), NOW.getTime(), NOW);
    expect(store.get('users/u1')).toBe(owned);
  });

  it('olderFetch_isIgnored', async () => {
    const billingPath = `users/u1/billingTransactions/play_${playTokenHash('tok')}`;
    const { db, store, tx } = fakeDb({
      [billingPath]: { playLastFetchedAt: 2000, appliedStatus: 'active' },
    });
    const result = await commitPlayState(tx as never, refsFor(db, 'u1', 'tok'), expired, playTokenHash('tok'), 1000, NOW);
    expect(result).toEqual({ applied: false });
    expect(tx.set).not.toHaveBeenCalled();
    expect(store.get(billingPath)).toEqual({ playLastFetchedAt: 2000, appliedStatus: 'active' });
  });
});

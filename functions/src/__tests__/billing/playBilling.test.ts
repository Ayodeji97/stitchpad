import * as functions from 'firebase-functions/v1';
import {
  PlayApi,
  PlaySubscription,
  commitPlayState,
  desiredStateForPlay,
  playAccountId,
  playRtdnHandler,
  playTokenHash,
  reconcilePlaySubscriptionsHandler,
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
    const filters: [string, string, unknown][] = [];
    const query: any = {
      where: jest.fn((field: string, op: string, value: unknown) => {
        filters.push([field, op, value]);
        return query;
      }),
      get: jest.fn(async () => {
        const docs = [...store.entries()]
          .filter(([path]) => path.startsWith(`${name}/`) && path.split('/').length === 2)
          .filter(([, data]) => filters.every(([f, op, v]) =>
            (op === 'in' ? (v as unknown[]).includes(data?.[f]) : data?.[f] === v)))
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
    expect(result).toEqual({ tier: 'pro', status: 'active', endsAt: expiry, blocked: false });

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
    expect(store.get(`playSubscriptions/${playTokenHash('tok-a')}`)).toMatchObject({
      uid: 'u1', purchaseToken: 'tok-a', appliedStatus: 'active',
    });
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
    expect(result.blocked).toBe(false);
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
    const result = await verifyPlayPurchaseHandler({ purchaseToken: 'tok' }, authCtx('u1'), deps(db, api));
    // Saved but not applied: the client tells the tailor it starts when the current plan ends.
    expect(result).toMatchObject({ tier: 'atelier', status: 'active', blocked: true });
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

  const ownedPro = (token: string) => ({
    subscriptionSource: 'play', subscriptionStatus: 'active', subscriptionTier: 'pro',
    subscriptionEndsAt: ts(NOW.getTime() + 20 * DAY),
    playPurchaseToken: token, playPurchaseTokenHash: playTokenHash(token),
  });

  it('linkedToken_downgrade_doesNotRevokeOwnedToken', async () => {
    // Pro on token O; an upgrade N (linkedPurchaseToken = O) stays pending and is
    // cancelled. N's expiry must not take ownership and revoke O's Pro.
    const owned = ownedPro('tok-o');
    const { db, store, tx } = fakeDb({ 'users/u1': owned });
    await commitPlayState(tx as never, refsFor(db, 'u1', 'tok-n'), expired, playTokenHash('tok-n'),
      NOW.getTime(), NOW, 'tok-o');
    expect(store.get('users/u1')).toBe(owned);
    expect(store.get('users/u1').playPurchaseToken).toBe('tok-o');
  });

  it('olderFetch_isIgnored', async () => {
    const billingPath = `users/u1/billingTransactions/play_${playTokenHash('tok')}`;
    const { db, store, tx } = fakeDb({
      [billingPath]: { playLastFetchedAt: 2000, appliedStatus: 'active' },
    });
    const result = await commitPlayState(tx as never, refsFor(db, 'u1', 'tok'), expired, playTokenHash('tok'), 1000, NOW);
    expect(result).toEqual({ applied: false, userDocWritten: false });
    expect(tx.set).not.toHaveBeenCalled();
    expect(store.get(billingPath)).toEqual({ playLastFetchedAt: 2000, appliedStatus: 'active' });
  });
});

describe('playRtdnHandler', () => {
  const ownedUser = (token: string, endsMs: number) => ({
    subscriptionSource: 'play', subscriptionStatus: 'active', subscriptionTier: 'pro',
    subscriptionEndsAt: ts(endsMs), playPurchaseToken: token, playPurchaseTokenHash: playTokenHash(token),
  });
  const msg = (token: string, type = 2) => ({
    version: '1.0', packageName: 'com.danzucker.stitchpad',
    subscriptionNotification: { version: '1.0', notificationType: type, purchaseToken: token, subscriptionId: PRO_MONTHLY },
  });

  it('rtdn_renewal_extendsEndsAt', async () => {
    const later = NOW.getTime() + 60 * DAY;
    const { db, store } = fakeDb({
      'users/u1': ownedUser('tok', NOW.getTime() + DAY),
      [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' },
    });
    const api = fakeApi(subFor('u1', {
      acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
      lineItems: [{ productId: PRO_MONTHLY, expiryTime: iso(later), autoRenewingPlan: { autoRenewEnabled: true } }],
    }));
    await playRtdnHandler(msg('tok', 2), deps(db, api));
    expect(api.getSubscription).toHaveBeenCalledWith('tok');
    expect(store.get('users/u1').subscriptionEndsAt.toMillis()).toBe(later);
    expect(store.get('users/u1').subscriptionTier).toBe('pro');
  });

  it('rtdn_expired_downgradesOwner', async () => {
    const { db, store } = fakeDb({
      'users/u1': ownedUser('tok', NOW.getTime() - DAY),
      [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' },
    });
    const api = fakeApi(subFor('u1', {
      subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED',
      lineItems: [{ productId: PRO_MONTHLY, expiryTime: iso(NOW.getTime() - DAY) }],
    }));
    await playRtdnHandler(msg('tok', 13), deps(db, api));
    expect(store.get('users/u1')).toMatchObject({ subscriptionTier: 'free', subscriptionStatus: 'expired' });
  });

  it('rtdn_pendingCompleted_grants', async () => {
    const { db, store } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' } });
    const api = fakeApi(subFor('u1'));
    await playRtdnHandler(msg('tok', 4), deps(db, api));
    expect(store.get('users/u1')).toMatchObject({ subscriptionTier: 'pro', subscriptionSource: 'play' });
    expect(api.acknowledge).toHaveBeenCalledWith(PRO_MONTHLY, 'tok');
  });

  it('rtdn_unknownToken_noWrite', async () => {
    const { db, store, tx } = fakeDb();
    const api = fakeApi(subFor('u1'));
    await expect(playRtdnHandler(msg('tok'), deps(db, api))).resolves.toBeUndefined();
    expect(store.size).toBe(0);
    expect(tx.set).not.toHaveBeenCalled();
  });

  it('rtdn_accountMismatch_noWrite', async () => {
    const { db, store } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' } });
    await playRtdnHandler(msg('tok'), deps(db, fakeApi(subFor('other'))));
    expect(store.has('users/u1')).toBe(false);
  });

  it('rtdn_testNotification_ignored', async () => {
    const { db } = fakeDb();
    const api = fakeApi(subFor('u1'));
    await expect(playRtdnHandler({ version: '1.0', testNotification: { version: '1.0' } }, deps(db, api)))
      .resolves.toBeUndefined();
    expect(api.getSubscription).not.toHaveBeenCalled();
  });

  it.each([[null], ['garbage'], [42], [{}], [{ subscriptionNotification: {} }], [{ subscriptionNotification: { purchaseToken: 7 } }]])(
    'rtdn_garbage_ignored %p',
    async (payload) => {
      const { db } = fakeDb();
      const api = fakeApi(subFor('u1'));
      await expect(playRtdnHandler(payload, deps(db, api))).resolves.toBeUndefined();
      expect(api.getSubscription).not.toHaveBeenCalled();
    },
  );

  it('rtdn_apiFailure_throws', async () => {
    const { db } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' } });
    await expect(playRtdnHandler(msg('tok'), deps(db, fakeApi(new Error('503')))))
      .rejects.toThrow('503');
  });

  it('rtdn_cancelledPendingUpgrade_keepsOwnedGrant', async () => {
    const owned = ownedUser('tok-o', NOW.getTime() + 20 * DAY);
    const { db, store } = fakeDb({
      'users/u1': owned,
      [`playSubscriptions/${playTokenHash('tok-n')}`]: { uid: 'u1' },
    });
    const api = fakeApi(subFor('u1', {
      subscriptionState: 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED',
      linkedPurchaseToken: 'tok-o',
      lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(NOW.getTime() + 30 * DAY) }],
    }));
    await playRtdnHandler(msg('tok-n', 3), deps(db, api));
    expect(store.get('users/u1')).toBe(owned);
  });

  it('rtdn_refusedTestPurchase_linkedToOwned_keepsOwnedGrant', async () => {
    delete process.env.PLAY_ALLOW_TEST_PURCHASES;
    const owned = ownedUser('tok-o', NOW.getTime() + 20 * DAY);
    const { db, store } = fakeDb({
      'users/u1': owned,
      [`playSubscriptions/${playTokenHash('tok-n')}`]: { uid: 'u1' },
    });
    const api = fakeApi(subFor('u1', {
      testPurchase: {},
      linkedPurchaseToken: 'tok-o',
      lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(NOW.getTime() + 30 * DAY) }],
    }));
    await playRtdnHandler(msg('tok-n'), deps(db, api));
    expect(store.get('users/u1')).toBe(owned);
  });

  it.each([400, 404, 410])('rtdn_permanentApiError_%s_resolvesWithoutWrite', async (status) => {
    const { db, store } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' } });
    const err = Object.assign(new Error(`Request failed with status code ${status}`), { status });
    await expect(playRtdnHandler(msg('tok'), deps(db, fakeApi(err)))).resolves.toBeUndefined();
    expect(store.has('users/u1')).toBe(false);
  });

  it('rtdn_permanentApiError_viaResponseStatus_resolves', async () => {
    const { db } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' } });
    const err = Object.assign(new Error('gone'), { response: { status: 410 } });
    await expect(playRtdnHandler(msg('tok'), deps(db, fakeApi(err)))).resolves.toBeUndefined();
  });

  it.each([429, 500, 503])('rtdn_transientApiError_%s_throws', async (status) => {
    const { db } = fakeDb({ [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' } });
    const err = Object.assign(new Error(`status ${status}`), { status });
    await expect(playRtdnHandler(msg('tok'), deps(db, fakeApi(err)))).rejects.toThrow(`status ${status}`);
  });

  it('rtdn_testPurchase_flagOff_healsToFree', async () => {
    delete process.env.PLAY_ALLOW_TEST_PURCHASES;
    const { db, store } = fakeDb({
      'users/u1': ownedUser('tok', NOW.getTime() + DAY),
      [`playSubscriptions/${playTokenHash('tok')}`]: { uid: 'u1' },
    });
    await playRtdnHandler(msg('tok'), deps(db, fakeApi(subFor('u1', { testPurchase: {} }))));
    expect(store.get('users/u1')).toMatchObject({ subscriptionTier: 'free', subscriptionStatus: 'expired' });
  });
});

describe('reconcilePlaySubscriptionsHandler', () => {
  const user = (token: string, extra: Record<string, unknown> = {}) => ({
    subscriptionSource: 'play', subscriptionStatus: 'active', subscriptionTier: 'pro',
    subscriptionEndsAt: ts(NOW.getTime() + DAY), playPurchaseToken: token, playPurchaseTokenHash: playTokenHash(token),
    ...extra,
  });
  const index = (uid: string, token: string, appliedStatus: string) => ({
    [`playSubscriptions/${playTokenHash(token)}`]: { uid, purchaseToken: token, appliedStatus },
  });

  it('reconcile_iteratesLivePlayRecords', async () => {
    const { db, store } = fakeDb({
      'users/u1': user('tok-1'),
      'users/u2': user('tok-2'),
      'users/u3': user('tok-3'),
      'users/u4': user('tok-4', { subscriptionStatus: 'expired' }),
      ...index('u1', 'tok-1', 'active'),
      ...index('u2', 'tok-2', 'active'),
      ...index('u3', 'tok-3', 'pending'),
      ...index('u4', 'tok-4', 'expired'),
    });
    const getSubscription = jest.fn(async (token: string) => {
      if (token === 'tok-1') throw new Error('boom');
      if (token === 'tok-2') {
        return subFor('u2', {
          subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED',
          lineItems: [{ productId: PRO_MONTHLY, expiryTime: iso(NOW.getTime() - DAY) }],
        });
      }
      return subFor('u3', { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' });
    });
    const api = fakeApi(subFor('x'), { getSubscription });
    await reconcilePlaySubscriptionsHandler(deps(db, api));

    // Only live records (active / pending); one failing token does not stop the loop.
    expect(getSubscription.mock.calls.map((c) => c[0]).sort()).toEqual(['tok-1', 'tok-2', 'tok-3']);
    expect(db.collection).toHaveBeenCalledWith('playSubscriptions');
    expect(store.get('users/u1').subscriptionTier).toBe('pro');
    expect(store.get('users/u2')).toMatchObject({ subscriptionTier: 'free', subscriptionStatus: 'expired' });
    expect(store.get('users/u3')).toMatchObject({ subscriptionTier: 'pro', subscriptionStatus: 'active' });
    expect(store.get(`playSubscriptions/${playTokenHash('tok-2')}`).appliedStatus).toBe('expired');
  });

  it('reconcile_appliesPlayOnceBlockingPaystackLapses', async () => {
    // Paystack Pro active -> Play Atelier verify is blocked (saved, not applied).
    const { db, store } = fakeDb({
      'users/u1': {
        subscriptionSource: 'paystack', subscriptionStatus: 'active', subscriptionTier: 'pro',
        subscriptionEndsAt: ts(NOW.getTime() + DAY),
      },
    });
    const sub = subFor('u1', {
      acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
      lineItems: [{ productId: ATELIER_MONTHLY, expiryTime: iso(NOW.getTime() + 30 * DAY) }],
    });
    const api = fakeApi(sub);
    const verified = await verifyPlayPurchaseHandler({ purchaseToken: 'tok-play' }, authCtx('u1'), deps(db, api));
    expect(verified.blocked).toBe(true);
    expect(store.get('users/u1').subscriptionSource).toBe('paystack');

    // Two days later the Paystack period has ended; the nightly reconcile applies Play.
    const later = new Date(NOW.getTime() + 2 * DAY);
    await reconcilePlaySubscriptionsHandler({ db: db as never, api, now: () => later });
    expect(store.get('users/u1')).toMatchObject({
      subscriptionSource: 'play', subscriptionTier: 'atelier', subscriptionStatus: 'active',
      playPurchaseToken: 'tok-play',
    });
  });

  it('reconcile_appliesPlayOnceLaunchGrantIsGone', async () => {
    // Launch grant (active, no end date) blocks; once it is gone Play applies.
    const { db, store } = fakeDb({
      'users/u1': { subscriptionStatus: 'active', subscriptionTier: 'atelier', grantSource: 'launch_free' },
    });
    const api = fakeApi(subFor('u1', { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' }));
    const verified = await verifyPlayPurchaseHandler({ purchaseToken: 'tok-play' }, authCtx('u1'), deps(db, api));
    expect(verified.blocked).toBe(true);

    store.set('users/u1', { subscriptionStatus: 'expired', subscriptionTier: 'free', grantSource: 'launch_free' });
    await reconcilePlaySubscriptionsHandler(deps(db, api));
    expect(store.get('users/u1')).toMatchObject({ subscriptionSource: 'play', subscriptionTier: 'pro' });
  });

  it('reconcile_skipsDeletedUser', async () => {
    const { db, store } = fakeDb({ ...index('gone', 'tok-1', 'active') });
    const api = fakeApi(subFor('gone'));
    await reconcilePlaySubscriptionsHandler(deps(db, api));
    expect(store.has('users/gone')).toBe(false);
  });

  it('reconcile_acknowledgesPending', async () => {
    const { db } = fakeDb({ 'users/u1': user('tok'), ...index('u1', 'tok', 'active') });
    const api = fakeApi(subFor('u1'));
    await reconcilePlaySubscriptionsHandler(deps(db, api));
    expect(api.acknowledge).toHaveBeenCalledWith(PRO_MONTHLY, 'tok');
  });
});

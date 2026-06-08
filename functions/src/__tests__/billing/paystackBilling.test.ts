import * as crypto from 'crypto';
import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';
import {
  buildReference,
  expirePrepaidSubscriptionsHandler,
  initializeSubscriptionCheckoutHandler,
  isValidPaystackSignature,
  paystackWebhookHandler,
  priceFor,
} from '../../billing/paystackBilling';

function docStore(initial: Record<string, any> = {}) {
  const store = new Map(Object.entries(initial));
  const doc = (path: string) => ({
    path,
    get: jest.fn(async () => ({
      exists: store.has(path),
      data: () => store.get(path),
    })),
    set: jest.fn(async (data: any, options?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, options?.merge ? { ...prev, ...data } : data);
    }),
  });
  return { store, doc };
}

describe('initializeSubscriptionCheckoutHandler', () => {
  it('rejects unauthenticated callers', async () => {
    const docs = docStore();
    await expect(initializeSubscriptionCheckoutHandler(
      { tier: 'pro', cadence: 'monthly' },
      {} as functions.https.CallableContext,
      {
        db: { doc: docs.doc } as never,
        paystack: { initializeTransaction: jest.fn() },
        now: () => new Date('2026-06-01T10:00:00Z'),
        randomId: () => 'abc',
      },
    )).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('rejects Free or invalid plans', async () => {
    const docs = docStore();
    await expect(initializeSubscriptionCheckoutHandler(
      { tier: 'free', cadence: 'monthly' },
      { auth: { uid: 'uid-1', token: { email: 'ada@example.com' } } } as never,
      {
        db: { doc: docs.doc } as never,
        paystack: { initializeTransaction: jest.fn() },
        now: () => new Date('2026-06-01T10:00:00Z'),
        randomId: () => 'abc',
      },
    )).rejects.toMatchObject({ code: 'invalid-argument' });
  });

  it('writes a pending billing transaction with the expected amount', async () => {
    const docs = docStore();
    const paystack = {
      initializeTransaction: jest.fn(async (request: any) => ({
        authorization_url: 'https://checkout.paystack.com/xyz',
        access_code: 'access',
        reference: request.reference,
      })),
    };

    const result = await initializeSubscriptionCheckoutHandler(
      { tier: 'pro', cadence: 'monthly' },
      { auth: { uid: 'uid-12345678', token: { email: 'ada@example.com' } } } as never,
      {
        db: { doc: docs.doc } as never,
        paystack,
        now: () => new Date('2026-06-01T10:00:00Z'),
        randomId: () => 'abc123',
        callbackUrl: 'https://getstitchpad.com/paystack/callback',
      },
    );

    expect(result.authorizationUrl).toBe('https://checkout.paystack.com/xyz');
    expect(paystack.initializeTransaction).toHaveBeenCalledWith(expect.objectContaining({
      amount: 200_000,
      email: 'ada@example.com',
      currency: 'NGN',
      metadata: expect.objectContaining({
        uid: 'uid-12345678',
        tier: 'pro',
        cadence: 'monthly',
      }),
    }));
    expect(docs.store.get(`users/uid-12345678/billingTransactions/${result.reference}`))
      .toMatchObject({
        tier: 'pro',
        cadence: 'monthly',
        amountKobo: 200_000,
        currency: 'NGN',
        status: 'pending',
        authorizationUrl: 'https://checkout.paystack.com/xyz',
      });
  });
});

describe('paystackWebhookHandler', () => {
  function signed(event: any, secret = 'secret') {
    const raw = Buffer.from(JSON.stringify(event));
    return {
      event,
      raw,
      signature: crypto.createHmac('sha512', secret).update(raw).digest('hex'),
    };
  }

  function dbWithTransaction(reference: string, billing: Record<string, any>, user: Record<string, any> = {}) {
    const docs = docStore({
      [`users/uid-1/billingTransactions/${reference}`]: billing,
      'users/uid-1': user,
    });
    const tx = {
      get: jest.fn(async (ref: any) => ({
        exists: docs.store.has(ref.path),
        data: () => docs.store.get(ref.path),
      })),
      set: jest.fn((ref: any, data: any, options?: { merge?: boolean }) => {
        const prev = docs.store.get(ref.path) ?? {};
        docs.store.set(ref.path, options?.merge ? { ...prev, ...data } : data);
      }),
    };
    return {
      store: docs.store,
      db: {
        doc: docs.doc,
        runTransaction: jest.fn(async (fn: any) => fn(tx)),
      },
    };
  }

  it('rejects invalid signatures', async () => {
    await expect(paystackWebhookHandler(
      { event: 'charge.success' },
      'bad',
      Buffer.from('{}'),
      {
        db: {} as never,
        secretKey: 'secret',
        now: () => new Date('2026-06-01T10:00:00Z'),
      },
    )).rejects.toThrow('invalid_signature');
  });

  it('applies a paid Pro monthly period once', async () => {
    const reference = 'stp_uid1_1_abc';
    const { db, store } = dbWithTransaction(reference, {
      tier: 'pro',
      cadence: 'monthly',
      amountKobo: 200_000,
      status: 'pending',
    });
    const payload = signed({
      event: 'charge.success',
      data: {
        reference,
        amount: 200_000,
        currency: 'NGN',
        status: 'success',
        paid_at: '2026-06-01T10:00:00Z',
        metadata: {
          uid: 'uid-1',
          tier: 'pro',
          cadence: 'monthly',
          purpose: 'stitchpad_subscription',
        },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });
    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:02:00Z'),
    });

    expect(store.get('users/uid-1')).toMatchObject({
      subscriptionTier: 'pro',
      subscriptionStatus: 'active',
      subscriptionRenews: false,
    });
    expect(store.get(`users/uid-1/billingTransactions/${reference}`)).toMatchObject({
      status: 'paid',
      failureReason: null,
    });
  });

  it('stacks an early renewal on top of an active paid subscription', async () => {
    const reference = 'stp_uid1_1_renew';
    const currentEnd = admin.firestore.Timestamp.fromDate(new Date('2026-06-20T00:00:00Z'));
    const { db, store } = dbWithTransaction(
      reference,
      { tier: 'pro', cadence: 'monthly', amountKobo: 200_000, status: 'pending' },
      { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: currentEnd },
    );
    const payload = signed({
      event: 'charge.success',
      data: {
        reference,
        amount: 200_000,
        currency: 'NGN',
        status: 'success',
        paid_at: '2026-06-01T10:00:00Z',
        metadata: { uid: 'uid-1', tier: 'pro', cadence: 'monthly', purpose: 'stitchpad_subscription' },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });

    // Period stacks from the existing end date (2026-06-20 + 30d), not the payment time.
    const endsAt = store.get('users/uid-1').subscriptionEndsAt.toDate() as Date;
    expect(endsAt.toISOString()).toBe('2026-07-20T00:00:00.000Z');
  });

  it('starts a fresh period on a tier switch instead of stacking', async () => {
    const reference = 'stp_uid1_1_switch';
    // Active Pro user with time remaining buys Atelier — must NOT inherit the
    // leftover Pro days at Atelier level; the Atelier period starts from payment.
    const currentEnd = admin.firestore.Timestamp.fromDate(new Date('2026-06-20T00:00:00Z'));
    const { db, store } = dbWithTransaction(
      reference,
      { tier: 'atelier', cadence: 'monthly', amountKobo: 400_000, status: 'pending' },
      { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: currentEnd },
    );
    const payload = signed({
      event: 'charge.success',
      data: {
        reference,
        amount: 400_000,
        currency: 'NGN',
        status: 'success',
        paid_at: '2026-06-01T10:00:00Z',
        metadata: { uid: 'uid-1', tier: 'atelier', cadence: 'monthly', purpose: 'stitchpad_subscription' },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });

    const user = store.get('users/uid-1');
    expect(user.subscriptionTier).toBe('atelier');
    // Fresh period from payment time (2026-07-01), not stacked onto 2026-06-20.
    expect((user.subscriptionEndsAt.toDate() as Date).toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('ignores a client-planted subscriptionEndsAt on a non-paid user', async () => {
    const reference = 'stp_uid1_1_plant';
    // Attacker planted a far-future end date at user-doc creation while still on free.
    const planted = admin.firestore.Timestamp.fromDate(new Date('2050-01-01T00:00:00Z'));
    const { db, store } = dbWithTransaction(
      reference,
      { tier: 'pro', cadence: 'monthly', amountKobo: 200_000, status: 'pending' },
      { subscriptionTier: 'free', subscriptionStatus: 'active', subscriptionEndsAt: planted },
    );
    const payload = signed({
      event: 'charge.success',
      data: {
        reference,
        amount: 200_000,
        currency: 'NGN',
        status: 'success',
        paid_at: '2026-06-01T10:00:00Z',
        metadata: { uid: 'uid-1', tier: 'pro', cadence: 'monthly', purpose: 'stitchpad_subscription' },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });

    // A fresh period starts from the payment time (2026-07-01), NOT the planted 2050 date.
    const endsAt = store.get('users/uid-1').subscriptionEndsAt.toDate() as Date;
    expect(endsAt.toISOString()).toBe('2026-07-01T10:00:00.000Z');
  });

  it('does not upgrade on amount mismatch', async () => {
    const reference = 'stp_uid1_1_bad';
    const { db, store } = dbWithTransaction(reference, {
      tier: 'pro',
      cadence: 'monthly',
      amountKobo: 200_000,
      status: 'pending',
    });
    const payload = signed({
      event: 'charge.success',
      data: {
        reference,
        amount: 100,
        currency: 'NGN',
        status: 'success',
        metadata: {
          uid: 'uid-1',
          tier: 'pro',
          cadence: 'monthly',
          purpose: 'stitchpad_subscription',
        },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });

    expect(store.get('users/uid-1')).toEqual({});
    expect(store.get(`users/uid-1/billingTransactions/${reference}`)).toMatchObject({
      status: 'failed',
      failureReason: 'paystack_payload_mismatch',
    });
  });
});

describe('expirePrepaidSubscriptionsHandler', () => {
  it('downgrades expired prepaid users and leaves active paid users alone', async () => {
    const writes: string[] = [];
    const docs = [
      { ref: { path: 'users/expired' } },
    ];
    const query: any = {
      where: jest.fn(() => query),
      get: jest.fn(async () => ({ docs })),
    };
    const batch = {
      set: jest.fn((ref: any, data: any) => writes.push(`${ref.path}:${data.subscriptionTier}:${data.subscriptionStatus}`)),
      commit: jest.fn(),
    };
    const db = {
      collection: jest.fn(() => query),
      batch: jest.fn(() => batch),
    };

    await expirePrepaidSubscriptionsHandler({
      db: db as never,
      now: () => new Date('2026-07-01T00:00:00Z'),
    });

    expect(query.where).toHaveBeenCalledWith('subscriptionTier', 'in', ['pro', 'atelier']);
    expect(query.where).toHaveBeenCalledWith('subscriptionRenews', '==', false);
    expect(writes).toEqual(['users/expired:free:expired']);
  });
});

describe('billing helpers', () => {
  it('prices supported prepaid plans', () => {
    expect(priceFor('pro', 'monthly')).toBe(200_000);
    expect(priceFor('pro', 'annual')).toBe(2_000_000);
    expect(priceFor('atelier', 'monthly')).toBe(400_000);
    expect(priceFor('atelier', 'annual')).toBe(4_000_000);
  });

  it('builds Paystack-safe references', () => {
    expect(buildReference('uid-with-symbols!@#', new Date('2026-06-01T00:00:00Z'), 'abc'))
      .toMatch(/^stp_hsymbols_\d+_abc$/);
  });

  it('validates Paystack signatures', () => {
    const raw = Buffer.from('{"event":"charge.success"}');
    const signature = crypto.createHmac('sha512', 'secret').update(raw).digest('hex');
    expect(isValidPaystackSignature(raw, signature, 'secret')).toBe(true);
  });
});

import * as crypto from 'crypto';
import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';
import {
  addPeriod,
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
      // Provenance marked so a stale Apple downgrade can't clobber this grant.
      subscriptionSource: 'paystack',
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

  it('persists fallback Pro when an active Pro user upgrades to Atelier (upgrade-preserve fix)', async () => {
    const reference = 'stp_uid1_1_upgrade';
    // Active Pro with 2 months remaining (paid to 2026-08-01).
    const proEnd = new Date('2026-08-01T00:00:00Z');
    const currentEnd = admin.firestore.Timestamp.fromDate(proEnd);
    const paidAt = new Date('2026-06-01T10:00:00Z');
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
        paid_at: paidAt.toISOString(),
        metadata: { uid: 'uid-1', tier: 'atelier', cadence: 'monthly', purpose: 'stitchpad_subscription' },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });

    const user = store.get('users/uid-1');
    // Active tier is Atelier (the upgrade just purchased).
    expect(user.subscriptionTier).toBe('atelier');
    // The queued Pro fallback must be persisted — this is what the fix restores.
    // (Before this fix, subscriptionFallbackTier was not written at all.)
    expect(user.subscriptionFallbackTier).toBe('pro');
    // The fallback end date must be non-null. The grant re-anchors the remaining Pro
    // time relative to the new Atelier end (newAtelierEnd = paidAt + 1 month =
    // 2026-07-01T10:00:00Z; proRemaining = proEnd - paidAt; newProEnd = newAtelierEnd
    // + proRemaining = 2026-08-31T00:00:00Z). We verify it is in the future and
    // strictly after the Atelier end — the exact date is an impl detail of
    // computeSubscriptionGrant and is already covered by subscriptionPeriod.test.ts.
    expect(user.subscriptionFallbackEndsAt).not.toBeNull();
    const atelierEndsAt = (user.subscriptionEndsAt as admin.firestore.Timestamp).toDate();
    const fallbackEndsAt = (user.subscriptionFallbackEndsAt as admin.firestore.Timestamp).toDate();
    expect(fallbackEndsAt.getTime()).toBeGreaterThan(atelierEndsAt.getTime());
  });

  it('clears stale fallback fields on a non-upgrade purchase (free -> Pro)', async () => {
    // Covers the case where a user previously had a gift-sourced fallback sitting
    // in their doc, then buys Pro outright — the Paystack write must null out the
    // stale fields so they aren't accidentally promoted later.
    const reference = 'stp_uid1_1_fresh';
    const staleEnd = admin.firestore.Timestamp.fromDate(new Date('2027-01-01T00:00:00Z'));
    const { db, store } = dbWithTransaction(
      reference,
      { tier: 'pro', cadence: 'monthly', amountKobo: 200_000, status: 'pending' },
      {
        subscriptionTier: 'free',
        subscriptionStatus: 'active',
        subscriptionFallbackTier: 'pro',
        subscriptionFallbackEndsAt: staleEnd,
      },
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

    const user = store.get('users/uid-1');
    expect(user.subscriptionTier).toBe('pro');
    // A free→pro purchase produces no fallback — stale fields must be explicitly null.
    expect(user.subscriptionFallbackTier).toBeNull();
    expect(user.subscriptionFallbackEndsAt).toBeNull();
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

  it('ignores a charge.success for an unknown reference (no billing doc)', async () => {
    // A valid HMAC over a reference we never created (e.g. a stale/forged replay)
    // must not upgrade anyone or crash — the handler returns without writing.
    const { db, store } = dbWithTransaction('stp_uid1_1_known', {
      tier: 'pro',
      cadence: 'monthly',
      amountKobo: 200_000,
      status: 'pending',
    });
    const payload = signed({
      event: 'charge.success',
      data: {
        reference: 'stp_uid1_1_GHOST',
        amount: 200_000,
        currency: 'NGN',
        status: 'success',
        metadata: { uid: 'uid-1', tier: 'pro', cadence: 'monthly', purpose: 'stitchpad_subscription' },
      },
    });

    await paystackWebhookHandler(payload.event, payload.signature, payload.raw, {
      db: db as never,
      secretKey: 'secret',
      now: () => new Date('2026-06-01T10:01:00Z'),
    });

    expect(store.get('users/uid-1')).toEqual({});
    expect(store.get('users/uid-1/billingTransactions/stp_uid1_1_GHOST')).toBeUndefined();
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
    // The endsAt filter is what scopes the sweep to ACTUALLY-expired users; without
    // it, every prepaid subscriber would be downgraded on each run.
    expect(query.where).toHaveBeenCalledWith(
      'subscriptionEndsAt',
      '<=',
      admin.firestore.Timestamp.fromDate(new Date('2026-07-01T00:00:00Z')),
    );
    expect(writes).toEqual(['users/expired:free:expired']);
  });

  it('promotes a queued fallback to active instead of dropping to Free', async () => {
    const writes: string[] = [];
    const docs = [
      {
        ref: { path: 'users/queued' },
        data: () => ({
          subscriptionFallbackTier: 'pro',
          subscriptionFallbackEndsAt: admin.firestore.Timestamp.fromDate(new Date('2027-01-01T00:00:00Z')),
        }),
      },
    ];
    const query: any = { where: jest.fn(() => query), get: jest.fn(async () => ({ docs })) };
    const batch = {
      set: jest.fn((ref: any, data: any) =>
        writes.push(`${ref.path}:${data.subscriptionTier}:${data.subscriptionStatus}:${data.subscriptionFallbackTier}`)),
      commit: jest.fn(),
    };
    const db = { collection: jest.fn(() => query), batch: jest.fn(() => batch) };

    await expirePrepaidSubscriptionsHandler({ db: db as never, now: () => new Date('2026-07-01T00:00:00Z') });

    // Active (higher) segment expired → promote Pro to active, clear the fallback slot.
    expect(writes).toEqual(['users/queued:pro:active:null']);
  });

  it('unwinds a Paystack upgrade: expired Atelier with queued Pro -> Pro active, end = fallback end', async () => {
    // The exact shape the upgrade-preserve fix writes: active Atelier ended, Pro queued.
    const fallbackEnd = new Date('2026-09-01T00:00:00Z');
    const captured: Record<string, unknown>[] = [];
    const docs = [
      {
        ref: { path: 'users/upgraded' },
        data: () => ({
          subscriptionTier: 'atelier',
          subscriptionStatus: 'active',
          subscriptionRenews: false,
          subscriptionEndsAt: admin.firestore.Timestamp.fromDate(new Date('2026-06-01T00:00:00Z')), // <= now
          subscriptionFallbackTier: 'pro',
          subscriptionFallbackEndsAt: admin.firestore.Timestamp.fromDate(fallbackEnd), // future
        }),
      },
    ];
    const query: any = { where: jest.fn(() => query), get: jest.fn(async () => ({ docs })) };
    const batch = {
      set: jest.fn((_ref: any, data: any) => captured.push(data)),
      commit: jest.fn(),
    };
    const db = { collection: jest.fn(() => query), batch: jest.fn(() => batch) };

    await expirePrepaidSubscriptionsHandler({ db: db as never, now: () => new Date('2026-07-01T00:00:00Z') });

    expect(captured).toHaveLength(1);
    const written = captured[0] as any;
    expect(written.subscriptionTier).toBe('pro');
    expect(written.subscriptionStatus).toBe('active');
    expect((written.subscriptionEndsAt as admin.firestore.Timestamp).toDate().getTime()).toBe(fallbackEnd.getTime());
    expect(written.subscriptionFallbackTier).toBeNull();
    expect(written.subscriptionFallbackEndsAt).toBeNull();
  });

  it('drops to Free when the queued fallback has also expired', async () => {
    const writes: string[] = [];
    const docs = [
      {
        ref: { path: 'users/both-expired' },
        data: () => ({
          subscriptionFallbackTier: 'pro',
          subscriptionFallbackEndsAt: admin.firestore.Timestamp.fromDate(new Date('2026-02-01T00:00:00Z')),
        }),
      },
    ];
    const query: any = { where: jest.fn(() => query), get: jest.fn(async () => ({ docs })) };
    const batch = {
      set: jest.fn((ref: any, data: any) => writes.push(`${ref.path}:${data.subscriptionTier}:${data.subscriptionStatus}`)),
      commit: jest.fn(),
    };
    const db = { collection: jest.fn(() => query), batch: jest.fn(() => batch) };

    await expirePrepaidSubscriptionsHandler({ db: db as never, now: () => new Date('2026-07-01T00:00:00Z') });

    expect(writes).toEqual(['users/both-expired:free:expired']);
  });
});

describe('addPeriod (calendar arithmetic)', () => {
  it('adds one calendar month, landing on the anniversary day', () => {
    expect(addPeriod(new Date('2027-03-15T10:00:00Z'), 'monthly').toISOString())
      .toBe('2027-04-15T10:00:00.000Z');
  });

  it('adds one calendar year, landing on the anniversary day', () => {
    expect(addPeriod(new Date('2027-03-15T10:00:00Z'), 'annual').toISOString())
      .toBe('2028-03-15T10:00:00.000Z');
  });

  it('respects leap years for annual (Feb 29 -> Mar 1 the next non-leap year)', () => {
    expect(addPeriod(new Date('2028-02-29T00:00:00Z'), 'annual').toISOString())
      .toBe('2029-03-01T00:00:00.000Z');
  });

  it('normalizes month-end overflow (Jan 31 + 1 month -> Mar 3 in a non-leap year)', () => {
    expect(addPeriod(new Date('2027-01-31T00:00:00Z'), 'monthly').toISOString())
      .toBe('2027-03-03T00:00:00.000Z');
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

  it('rejects a signature of the wrong length without throwing', () => {
    // Node's timingSafeEqual throws on length mismatch, so the length guard must
    // short-circuit to false rather than letting the comparison blow up.
    const raw = Buffer.from('{"event":"charge.success"}');
    expect(isValidPaystackSignature(raw, 'too-short', 'secret')).toBe(false);
    expect(isValidPaystackSignature(raw, undefined, 'secret')).toBe(false);
  });
});

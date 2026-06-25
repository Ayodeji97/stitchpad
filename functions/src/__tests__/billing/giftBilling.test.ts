import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';
import {
  initializeGiftCheckoutHandler,
  applyGiftWebhook,
  redeemGiftHandler,
  getPublicGiftProfileHandler,
  createGiftLinkHandler,
  expireUnclaimedGiftsHandler,
  deliverClaimEmail,
  resendUnclaimedGiftEmailsHandler,
} from '../../billing/giftBilling';
import { ResendError } from '../../email/resendClient';

function ms(v: any): number {
  if (v == null) return NaN;
  if (typeof v.toMillis === 'function') return v.toMillis();
  if (v instanceof Date) return v.getTime();
  return v as number;
}

function matchFilter(actual: any, op: string, val: any): boolean {
  if (op === '==') return actual === val;
  if (op === '<=') return ms(actual) <= ms(val);
  if (op === '>') return ms(actual) > ms(val);
  return false;
}

function makeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));

  const docRef = (path: string): any => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
    set: async (data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, opts?.merge ? { ...prev, ...data } : data);
    },
    create: async (data: any) => {
      if (store.has(path)) { const e: any = new Error('exists'); e.code = 6; throw e; }
      store.set(path, data);
    },
    collection: (name: string) => collectionRef(`${path}/${name}`),
  });

  const collectionRef = (basePath: string): any => {
    const filters: { field: string; op: string; val: any }[] = [];
    const ref: any = {
      doc: (id: string) => docRef(`${basePath}/${id}`),
      where: (field: string, op: string, val: any) => { filters.push({ field, op, val }); return ref; },
      get: async () => {
        const docs: any[] = [];
        for (const [path, data] of store) {
          const isDirectChild = path.startsWith(`${basePath}/`) && !path.slice(basePath.length + 1).includes('/');
          if (isDirectChild && filters.every((f) => matchFilter(data[f.field], f.op, f.val))) {
            docs.push({ id: path.slice(basePath.length + 1), ref: docRef(path), data: () => data });
          }
        }
        return { docs };
      },
    };
    return ref;
  };

  const tx = {
    get: async (ref: any) => ({ exists: store.has(ref.path), data: () => store.get(ref.path) }),
    set: (ref: any, data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(ref.path) ?? {};
      store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
    },
  };

  const db: any = {
    doc: (path: string) => docRef(path),
    collection: (name: string) => collectionRef(name),
    runTransaction: async (fn: any) => fn(tx),
    batch: () => {
      const ops: any[] = [];
      return {
        set: (ref: any, data: any, opts?: { merge?: boolean }) => ops.push({ ref, data, opts }),
        commit: async () => {
          for (const o of ops) {
            const prev = store.get(o.ref.path) ?? {};
            store.set(o.ref.path, o.opts?.merge ? { ...prev, ...o.data } : o.data);
          }
        },
      };
    },
  };
  return { store, db };
}

const NOW = new Date('2026-06-01T10:00:00Z');
const okPaystack = {
  initializeTransaction: jest.fn(async (req: any) => ({
    authorization_url: 'https://checkout.paystack.com/xyz',
    access_code: 'access',
    reference: req.reference,
  })),
};

describe('initializeGiftCheckoutHandler', () => {
  beforeEach(() => okPaystack.initializeTransaction.mockClear());

  it('creates a public gift keyed by a bearer code and returns the code', async () => {
    const { store, db } = makeDb();
    const result = await initializeGiftCheckoutHandler(
      { recipientEmail: 'ada@example.com', tier: 'pro', cadence: 'monthly', gifterName: 'Bola', gifterEmail: 'bola@example.com', note: 'Enjoy!' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'CODE123', randomId: () => 'r1' },
    );
    expect(result.code).toBe('CODE123');
    expect(result.authorizationUrl).toBe('https://checkout.paystack.com/xyz');
    expect(store.get('gifts/CODE123')).toMatchObject({
      status: 'pending', flow: 'public', tier: 'pro', cadence: 'monthly',
      amountKobo: 200_000, code: 'CODE123', recipientEmail: 'ada@example.com',
      gifterName: 'Bola', note: 'Enjoy!', targetUid: null,
    });
    expect(okPaystack.initializeTransaction).toHaveBeenCalledWith(expect.objectContaining({
      amount: 200_000, email: 'bola@example.com',
      metadata: expect.objectContaining({ giftId: 'CODE123', purpose: 'stitchpad_gift' }),
    }));
  });

  it('resolves a gift_me token to a target uid and stores no code', async () => {
    const { store, db } = makeDb({ 'giftLinkTokens/TOK': { uid: 'tailor-1' } });
    const result = await initializeGiftCheckoutHandler(
      { token: 'TOK', tier: 'atelier', cadence: 'annual', gifterEmail: 'fan@example.com' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'X', randomId: () => 'r2' },
    );
    expect(result.code).toBeUndefined();
    const giftId = `gift_${NOW.getTime()}_r2`;
    expect(store.get(`gifts/${giftId}`)).toMatchObject({
      flow: 'gift_me', targetUid: 'tailor-1', tier: 'atelier', cadence: 'annual', amountKobo: 4_000_000, code: null,
    });
  });

  it('multiplies the amount by quantity and stores it', async () => {
    const { store, db } = makeDb();
    const result = await initializeGiftCheckoutHandler(
      { recipientEmail: 'a@b.com', tier: 'pro', cadence: 'monthly', quantity: 3, gifterEmail: 'g@h.com' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'C3', randomId: () => 'r' },
    );
    expect(result.code).toBe('C3');
    expect(store.get('gifts/C3')).toMatchObject({ quantity: 3, amountKobo: 600_000 });
    expect(okPaystack.initializeTransaction).toHaveBeenCalledWith(expect.objectContaining({ amount: 600_000 }));
  });

  it('defaults quantity to 1 when omitted', async () => {
    const { store, db } = makeDb();
    await initializeGiftCheckoutHandler(
      { recipientEmail: 'a@b.com', tier: 'pro', cadence: 'monthly', gifterEmail: 'g@h.com' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'C1', randomId: () => 'r' },
    );
    expect(store.get('gifts/C1')).toMatchObject({ quantity: 1, amountKobo: 200_000 });
  });

  it('rejects an out-of-range or non-integer quantity', async () => {
    const { db } = makeDb();
    for (const q of [0, 13, 2.5, 'x']) {
      await expect(initializeGiftCheckoutHandler(
        { recipientEmail: 'a@b.com', tier: 'pro', cadence: 'monthly', quantity: q, gifterEmail: 'g@h.com' },
        { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'Q', randomId: () => 'r' },
      )).rejects.toMatchObject({ code: 'invalid-argument', message: 'invalid_quantity' });
    }
  });

  it('rejects an unknown gift_me token', async () => {
    const { db } = makeDb();
    await expect(initializeGiftCheckoutHandler(
      { token: 'NOPE', tier: 'pro', cadence: 'monthly', gifterEmail: 'x@y.com' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'X', randomId: () => 'r' },
    )).rejects.toMatchObject({ code: 'invalid-argument', message: 'invalid_gift_link' });
  });

  it('rejects an invalid plan', async () => {
    const { db } = makeDb();
    await expect(initializeGiftCheckoutHandler(
      { recipientEmail: 'a@b.com', tier: 'free', cadence: 'monthly' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'X', randomId: () => 'r' },
    )).rejects.toMatchObject({ code: 'invalid-argument' });
  });

  it('rejects when no recipient is provided', async () => {
    const { db } = makeDb();
    await expect(initializeGiftCheckoutHandler(
      { tier: 'pro', cadence: 'monthly', gifterEmail: 'x@y.com' },
      { db, paystack: okPaystack, now: () => NOW, randomCode: () => 'X', randomId: () => 'r' },
    )).rejects.toMatchObject({ code: 'invalid-argument', message: 'missing_recipient' });
  });

  it('marks the gift failed and throws when Paystack init fails', async () => {
    const { store, db } = makeDb();
    const failing = { initializeTransaction: jest.fn(async () => { throw new Error('boom'); }) };
    await expect(initializeGiftCheckoutHandler(
      { recipientEmail: 'a@b.com', tier: 'pro', cadence: 'monthly' },
      { db, paystack: failing, now: () => NOW, randomCode: () => 'CODE9', randomId: () => 'r' },
    )).rejects.toMatchObject({ code: 'unavailable' });
    expect(store.get('gifts/CODE9')).toMatchObject({ status: 'failed' });
  });
});

describe('applyGiftWebhook', () => {
  function giftChargeData(giftId: string, amount = 200_000) {
    return {
      reference: 'gft_1_r', amount, currency: 'NGN', status: 'success',
      paid_at: '2026-06-01T10:00:00Z',
      metadata: { giftId, tier: 'pro', cadence: 'monthly', purpose: 'stitchpad_gift' },
    };
  }

  it('auto-applies a gift_me gift to the target user with a notification + email', async () => {
    const sendEmail = jest.fn(async () => {});
    const { store, db } = makeDb({
      'gifts/gift_1': {
        status: 'pending', flow: 'gift_me', tier: 'pro', cadence: 'monthly',
        amountKobo: 200_000, code: null, targetUid: 'tailor-1', gifterName: 'Bola',
      },
      'users/tailor-1': { subscriptionTier: 'free', subscriptionStatus: 'active' },
    });

    await applyGiftWebhook(giftChargeData('gift_1'), {
      db, now: () => NOW, sendEmail, lookupUserEmail: async () => 'tailor@example.com',
    });

    expect(store.get('users/tailor-1')).toMatchObject({
      subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionRenews: false,
    });
    expect(store.get('gifts/gift_1')).toMatchObject({ status: 'claimed', claimedByUid: 'tailor-1' });
    expect(store.get('users/tailor-1/notifications/gift_1__GIFT_RECEIVED')).toMatchObject({
      type: 'GIFT_RECEIVED', tier: 'pro', isRead: false,
    });
    expect(sendEmail).toHaveBeenCalledWith(expect.objectContaining({ to: 'tailor@example.com' }));
  });

  it('queues a fallback when gifting a lower tier to an active higher tier (case D)', async () => {
    const { store, db } = makeDb({
      'gifts/gift_d': {
        status: 'pending', flow: 'gift_me', tier: 'pro', cadence: 'monthly',
        amountKobo: 200_000, targetUid: 'tailor-1', gifterName: 'Bola',
      },
      'users/tailor-1': {
        subscriptionTier: 'atelier', subscriptionStatus: 'active',
        subscriptionEndsAt: admin.firestore.Timestamp.fromDate(new Date('2026-12-01T00:00:00Z')),
      },
    });
    await applyGiftWebhook(giftChargeData('gift_d'), {
      db, now: () => NOW, sendEmail: jest.fn(async () => {}), lookupUserEmail: async () => 't@e.com',
    });
    const user = store.get('users/tailor-1');
    expect(user.subscriptionTier).toBe('atelier'); // higher tier keeps running
    expect((user.subscriptionEndsAt.toDate() as Date).toISOString()).toBe('2026-12-01T00:00:00.000Z');
    expect(user.subscriptionFallbackTier).toBe('pro'); // gifted Pro queued behind
    expect((user.subscriptionFallbackEndsAt.toDate() as Date).toISOString()).toBe('2027-01-01T00:00:00.000Z');
    // Notification names the GIFTED tier, not the active one.
    expect(store.get('users/tailor-1/notifications/gift_d__GIFT_RECEIVED').tier).toBe('pro');
  });

  it('applies N periods for a multi-quantity gift_me gift', async () => {
    const { store, db } = makeDb({
      'gifts/gift_q': {
        status: 'pending', flow: 'gift_me', tier: 'pro', cadence: 'monthly',
        quantity: 3, amountKobo: 600_000, targetUid: 'tailor-1',
      },
      'users/tailor-1': { subscriptionTier: 'free', subscriptionStatus: 'active' },
    });
    await applyGiftWebhook(
      { ...giftChargeData('gift_q', 600_000) },
      { db, now: () => NOW, sendEmail: jest.fn(async () => {}), lookupUserEmail: async () => 't@e.com' },
    );
    const endsAt = store.get('users/tailor-1').subscriptionEndsAt.toDate() as Date;
    expect(endsAt.toISOString()).toBe('2026-09-01T10:00:00.000Z'); // paidAt + 3 months
  });

  it('is idempotent — a second delivery does not re-apply', async () => {
    const { store, db } = makeDb({
      'gifts/gift_1': {
        status: 'pending', flow: 'gift_me', tier: 'pro', cadence: 'monthly',
        amountKobo: 200_000, targetUid: 'tailor-1',
      },
      'users/tailor-1': { subscriptionTier: 'free', subscriptionStatus: 'active' },
    });
    const deps = { db, now: () => NOW, sendEmail: jest.fn(async () => {}), lookupUserEmail: async () => 't@e.com' };
    await applyGiftWebhook(giftChargeData('gift_1'), deps);
    const firstEnd = store.get('users/tailor-1').subscriptionEndsAt;
    await applyGiftWebhook(giftChargeData('gift_1'), deps);
    expect(store.get('users/tailor-1').subscriptionEndsAt).toBe(firstEnd);
  });

  it('marks a public gift paid and emails the recipient a claim link', async () => {
    const sendEmail = jest.fn(async () => {});
    const { store, db } = makeDb({
      'gifts/CODE123': {
        status: 'pending', flow: 'public', tier: 'pro', cadence: 'monthly',
        amountKobo: 200_000, code: 'CODE123', recipientEmail: 'ada@example.com', gifterName: 'Bola', note: 'Hi',
      },
    });
    await applyGiftWebhook(giftChargeData('CODE123'), { db, now: () => NOW, sendEmail });
    expect(store.get('gifts/CODE123')).toMatchObject({ status: 'paid' });
    expect(sendEmail).toHaveBeenCalledWith(expect.objectContaining({ to: 'ada@example.com' }));
    // Successful send clears the delivery marker so the sweep won't re-send.
    expect(store.get('gifts/CODE123').needsClaimEmail).toBe(false);
  });

  it('leaves needsClaimEmail set when the first send fails (sweep will retry)', async () => {
    const sendEmail = jest.fn(async () => { throw new ResendError('Resend down', 503); });
    const { store, db } = makeDb({
      'gifts/CODE503': {
        status: 'pending', flow: 'public', tier: 'pro', cadence: 'monthly',
        amountKobo: 200_000, code: 'CODE503', recipientEmail: 'ada@example.com',
      },
    });
    await applyGiftWebhook(giftChargeData('CODE503'), { db, now: () => NOW, sendEmail });
    expect(store.get('gifts/CODE503')).toMatchObject({ status: 'paid', needsClaimEmail: true, claimEmailAttempts: 1 });
  });

  it('fails the gift on amount mismatch without touching the user', async () => {
    const { store, db } = makeDb({
      'gifts/gift_1': { status: 'pending', flow: 'gift_me', tier: 'pro', cadence: 'monthly', amountKobo: 200_000, targetUid: 'tailor-1' },
      'users/tailor-1': { subscriptionTier: 'free' },
    });
    await applyGiftWebhook(giftChargeData('gift_1', 100), { db, now: () => NOW });
    expect(store.get('gifts/gift_1')).toMatchObject({ status: 'failed', failureReason: 'paystack_payload_mismatch' });
    expect(store.get('users/tailor-1')).toEqual({ subscriptionTier: 'free' });
  });

  it('ignores a charge for an unknown gift', async () => {
    const { store, db } = makeDb();
    await applyGiftWebhook(giftChargeData('ghost'), { db, now: () => NOW });
    expect(store.get('gifts/ghost')).toBeUndefined();
  });
});

describe('redeemGiftHandler', () => {
  const ctx = (uid?: string, email?: string, emailVerified = true) => ({
    auth: uid ? { uid, token: email ? { email, email_verified: emailVerified } : undefined } : undefined,
  } as functions.https.CallableContext);

  function paidGift(extra: Record<string, any> = {}) {
    return {
      'gifts/CODE': {
        status: 'paid', flow: 'public', tier: 'pro', cadence: 'monthly', amountKobo: 200_000,
        expiresAt: admin.firestore.Timestamp.fromDate(new Date('2027-06-01T10:00:00Z')),
        ...extra,
      },
    };
  }

  it('rejects unauthenticated callers', async () => {
    const { db } = makeDb(paidGift());
    await expect(redeemGiftHandler({ code: 'CODE' }, ctx(), { db, now: () => NOW }))
      .rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('applies a paid gift to the caller and marks it claimed', async () => {
    const { store, db } = makeDb(paidGift());
    const res = await redeemGiftHandler({ code: 'CODE' }, ctx('ada'), { db, now: () => NOW });
    expect(res).toEqual({ tier: 'pro', cadence: 'monthly' });
    expect(store.get('users/ada')).toMatchObject({ subscriptionTier: 'pro', subscriptionStatus: 'active' });
    expect(store.get('gifts/CODE')).toMatchObject({ status: 'claimed', claimedByUid: 'ada' });
  });

  it('rejects an unknown code', async () => {
    const { db } = makeDb();
    await expect(redeemGiftHandler({ code: 'NOPE' }, ctx('ada'), { db, now: () => NOW }))
      .rejects.toMatchObject({ code: 'not-found', message: 'gift_not_found' });
  });

  it('rejects a gift already claimed by someone else', async () => {
    const { db } = makeDb(paidGift({ status: 'claimed', claimedByUid: 'other' }));
    await expect(redeemGiftHandler({ code: 'CODE' }, ctx('ada'), { db, now: () => NOW }))
      .rejects.toMatchObject({ message: 'gift_already_claimed' });
  });

  it('treats re-redeem by the same caller as success (double-tap)', async () => {
    const { db } = makeDb(paidGift({ status: 'claimed', claimedByUid: 'ada' }));
    const res = await redeemGiftHandler({ code: 'CODE' }, ctx('ada'), { db, now: () => NOW });
    expect(res).toEqual({ tier: 'pro', cadence: 'monthly' });
  });

  it('rejects an expired-status gift', async () => {
    const { db } = makeDb(paidGift({ status: 'expired' }));
    await expect(redeemGiftHandler({ code: 'CODE' }, ctx('ada'), { db, now: () => NOW }))
      .rejects.toMatchObject({ message: 'gift_expired' });
  });

  it('rejects a paid gift past its expiry date', async () => {
    const { db } = makeDb(paidGift({ expiresAt: admin.firestore.Timestamp.fromDate(new Date('2026-01-01T00:00:00Z')) }));
    await expect(redeemGiftHandler({ code: 'CODE' }, ctx('ada'), { db, now: () => NOW }))
      .rejects.toMatchObject({ message: 'gift_expired' });
  });

  it('rejects a not-yet-paid gift', async () => {
    const { db } = makeDb(paidGift({ status: 'pending' }));
    await expect(redeemGiftHandler({ code: 'CODE' }, ctx('ada'), { db, now: () => NOW }))
      .rejects.toMatchObject({ message: 'gift_not_payable' });
  });

  it('applies a public gift when requireRecipientMatch and the caller email matches (case-insensitive)', async () => {
    const { store, db } = makeDb(paidGift({ recipientEmail: 'Ada@X.com' }));
    const res = await redeemGiftHandler(
      { code: 'CODE', requireRecipientMatch: true },
      ctx('ada', ' ada@x.com '),
      { db, now: () => NOW },
    );
    expect(res).toEqual({ tier: 'pro', cadence: 'monthly' });
    expect(store.get('users/ada')).toMatchObject({ subscriptionTier: 'pro', subscriptionStatus: 'active' });
    expect(store.get('gifts/CODE')).toMatchObject({ status: 'claimed', claimedByUid: 'ada' });
  });

  it('rejects a public gift when requireRecipientMatch and the caller email differs, applying nothing', async () => {
    const { store, db } = makeDb(paidGift({ recipientEmail: 'ada@x.com' }));
    await expect(redeemGiftHandler(
      { code: 'CODE', requireRecipientMatch: true },
      ctx('zoe', 'zoe@other.com'),
      { db, now: () => NOW },
    )).rejects.toMatchObject({ code: 'permission-denied', message: 'recipient_email_mismatch' });
    expect(store.get('users/zoe')).toBeUndefined();
    // Gift untouched: still paid, never claimed.
    expect(store.get('gifts/CODE')).toMatchObject({ status: 'paid' });
    expect(store.get('gifts/CODE').claimedByUid).toBeUndefined();
  });

  it('rejects when requireRecipientMatch and the email matches but is unverified, applying nothing', async () => {
    const { store, db } = makeDb(paidGift({ recipientEmail: 'ada@x.com' }));
    await expect(redeemGiftHandler(
      { code: 'CODE', requireRecipientMatch: true },
      ctx('ada', 'ada@x.com', false),
      { db, now: () => NOW },
    )).rejects.toMatchObject({ code: 'permission-denied', message: 'recipient_email_unverified' });
    expect(store.get('users/ada')).toBeUndefined();
    expect(store.get('gifts/CODE')).toMatchObject({ status: 'paid' });
  });

  it('rejects when requireRecipientMatch but the caller has no email on the token', async () => {
    const { store, db } = makeDb(paidGift({ recipientEmail: 'ada@x.com' }));
    await expect(redeemGiftHandler(
      { code: 'CODE', requireRecipientMatch: true },
      ctx('ada'),
      { db, now: () => NOW },
    )).rejects.toMatchObject({ code: 'permission-denied', message: 'recipient_email_mismatch' });
    expect(store.get('users/ada')).toBeUndefined();
  });

  it('stays bearer when requireRecipientMatch is absent (any signed-in caller can redeem)', async () => {
    const { store, db } = makeDb(paidGift({ recipientEmail: 'ada@x.com' }));
    const res = await redeemGiftHandler(
      { code: 'CODE' },
      ctx('zoe', 'zoe@other.com'),
      { db, now: () => NOW },
    );
    expect(res).toEqual({ tier: 'pro', cadence: 'monthly' });
    expect(store.get('users/zoe')).toMatchObject({ subscriptionTier: 'pro' });
  });

  it('stays bearer when requireRecipientMatch but the gift has no recipientEmail (gift_me)', async () => {
    const { store, db } = makeDb(paidGift({ flow: 'gift_me', recipientEmail: null }));
    const res = await redeemGiftHandler(
      { code: 'CODE', requireRecipientMatch: true },
      ctx('zoe', 'zoe@other.com'),
      { db, now: () => NOW },
    );
    expect(res).toEqual({ tier: 'pro', cadence: 'monthly' });
    expect(store.get('users/zoe')).toMatchObject({ subscriptionTier: 'pro' });
  });
});

describe('getPublicGiftProfileHandler', () => {
  it('returns whitelisted brand fields for a valid token', async () => {
    const { db } = makeDb({
      'giftLinkTokens/TOK': { uid: 'tailor-1' },
      'users/tailor-1': { businessName: 'Adire House', displayName: 'Bola', businessLogoUrl: 'https://logo', email: 'secret@x.com' },
    });
    const res = await getPublicGiftProfileHandler({ token: 'TOK' }, { db });
    expect(res).toEqual({ businessName: 'Adire House', displayName: 'Bola', logoUrl: 'https://logo' });
  });

  it('rejects an unknown token', async () => {
    const { db } = makeDb();
    await expect(getPublicGiftProfileHandler({ token: 'NOPE' }, { db }))
      .rejects.toMatchObject({ code: 'not-found' });
  });
});

describe('createGiftLinkHandler', () => {
  const ctx = (uid?: string) => ({ auth: uid ? { uid } : undefined } as functions.https.CallableContext);

  it('mints a token + reverse index and is idempotent', async () => {
    const { store, db } = makeDb({ 'users/tailor-1': {} });
    const first = await createGiftLinkHandler(ctx('tailor-1'), { db, now: () => NOW, randomToken: () => 'NEWTOK' });
    expect(first).toEqual({ token: 'NEWTOK', url: 'https://getstitchpad.com/gift/NEWTOK' });
    expect(store.get('users/tailor-1').giftLinkToken).toBe('NEWTOK');
    expect(store.get('giftLinkTokens/NEWTOK')).toMatchObject({ uid: 'tailor-1' });

    const second = await createGiftLinkHandler(ctx('tailor-1'), { db, now: () => NOW, randomToken: () => 'SHOULD_NOT_USE' });
    expect(second.token).toBe('NEWTOK');
  });

  it('rejects unauthenticated callers', async () => {
    const { db } = makeDb();
    await expect(createGiftLinkHandler(ctx(), { db, now: () => NOW, randomToken: () => 'X' }))
      .rejects.toMatchObject({ code: 'unauthenticated' });
  });
});

describe('expireUnclaimedGiftsHandler', () => {
  it('expires paid gifts past their expiry and leaves others alone', async () => {
    const past = admin.firestore.Timestamp.fromDate(new Date('2026-01-01T00:00:00Z'));
    const future = admin.firestore.Timestamp.fromDate(new Date('2027-01-01T00:00:00Z'));
    const { store, db } = makeDb({
      'gifts/old': { status: 'paid', expiresAt: past },
      'gifts/fresh': { status: 'paid', expiresAt: future },
      'gifts/claimed': { status: 'claimed', expiresAt: past },
    });
    await expireUnclaimedGiftsHandler({ db, now: () => new Date('2026-06-01T00:00:00Z') });
    expect(store.get('gifts/old').status).toBe('expired');
    expect(store.get('gifts/fresh').status).toBe('paid');
    expect(store.get('gifts/claimed').status).toBe('claimed');
  });
});

describe('claim email retry (deliverClaimEmail + resendUnclaimedGiftEmailsHandler)', () => {
  const baseGift = {
    status: 'paid', flow: 'public', tier: 'pro', cadence: 'monthly',
    code: 'ABC234', recipientEmail: 'ada@example.com', needsClaimEmail: true, claimEmailAttempts: 0,
  };

  it('clears the marker on a successful send', async () => {
    const { store, db } = makeDb({ 'gifts/ABC234': { ...baseGift } });
    const sendEmail = jest.fn(async () => {});
    await deliverClaimEmail(db, 'ABC234', store.get('gifts/ABC234'), sendEmail);
    expect(sendEmail).toHaveBeenCalledWith(expect.objectContaining({ to: 'ada@example.com' }));
    expect(store.get('gifts/ABC234').needsClaimEmail).toBe(false);
  });

  it('keeps retrying on a transient (5xx) failure', async () => {
    const { store, db } = makeDb({ 'gifts/ABC234': { ...baseGift, claimEmailAttempts: 2 } });
    const sendEmail = jest.fn(async () => { throw new ResendError('busy', 503); });
    await deliverClaimEmail(db, 'ABC234', store.get('gifts/ABC234'), sendEmail);
    expect(store.get('gifts/ABC234')).toMatchObject({ needsClaimEmail: true, claimEmailAttempts: 3 });
  });

  it('gives up on a permanent (4xx) failure — bad address, do not retry', async () => {
    const { store, db } = makeDb({ 'gifts/ABC234': { ...baseGift } });
    const sendEmail = jest.fn(async () => { throw new ResendError('invalid to address', 422); });
    await deliverClaimEmail(db, 'ABC234', store.get('gifts/ABC234'), sendEmail);
    expect(store.get('gifts/ABC234')).toMatchObject({ needsClaimEmail: false, claimEmailAttempts: 1 });
  });

  it('gives up after MAX attempts on transient failures', async () => {
    const { store, db } = makeDb({ 'gifts/ABC234': { ...baseGift, claimEmailAttempts: 4 } });
    const sendEmail = jest.fn(async () => { throw new ResendError('busy', 500); });
    await deliverClaimEmail(db, 'ABC234', store.get('gifts/ABC234'), sendEmail);
    expect(store.get('gifts/ABC234')).toMatchObject({ needsClaimEmail: false, claimEmailAttempts: 5 });
  });

  it('stops trying when there is no recipient email', async () => {
    const { store, db } = makeDb({ 'gifts/ABC234': { ...baseGift, recipientEmail: null } });
    const sendEmail = jest.fn(async () => {});
    await deliverClaimEmail(db, 'ABC234', store.get('gifts/ABC234'), sendEmail);
    expect(sendEmail).not.toHaveBeenCalled();
    expect(store.get('gifts/ABC234').needsClaimEmail).toBe(false);
  });

  it('sweep re-sends every gift flagged needsClaimEmail', async () => {
    const { store, db } = makeDb({
      'gifts/G1': { ...baseGift, code: 'G1', recipientEmail: 'a@x.com' },
      'gifts/G2': { ...baseGift, code: 'G2', recipientEmail: 'b@x.com' },
      'gifts/done': { ...baseGift, code: 'done', needsClaimEmail: false },
    });
    const sendEmail = jest.fn(async () => {});
    await resendUnclaimedGiftEmailsHandler({ db, sendEmail });
    expect(sendEmail).toHaveBeenCalledTimes(2); // G1 + G2, not the already-done one
    expect(store.get('gifts/G1').needsClaimEmail).toBe(false);
    expect(store.get('gifts/G2').needsClaimEmail).toBe(false);
  });
});

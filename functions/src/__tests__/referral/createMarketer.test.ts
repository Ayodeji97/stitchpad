import * as functions from 'firebase-functions/v1';
import { createMarketerHandler } from '../../referral/marketerAdmin';

// Minimal in-memory Firestore double: doc get/set + a single-shot transaction
// with read-before-write, enough for createMarketerHandler's mint.
function makeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const docRef = (path: string): any => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
    set: async (data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, opts?.merge ? { ...prev, ...data } : data);
    },
  });
  const tx = {
    get: async (ref: any) => ({ exists: store.has(ref.path), data: () => store.get(ref.path) }),
    set: (ref: any, data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(ref.path) ?? {};
      store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
    },
  };
  const db: any = {
    doc: (path: string) => docRef(path),
    runTransaction: async (fn: any) => fn(tx),
  };
  return { store, db };
}

const NOW = new Date('2026-07-01T09:00:00Z');
const ADMIN = { auth: { uid: 'admin1', token: { admin: true } } } as unknown as functions.https.CallableContext;

function deps(over: Partial<{ randomCode: () => string; randomId: () => string }> = {}) {
  return {
    db: makeDb().db,
    now: () => NOW,
    randomCode: over.randomCode ?? (() => 'ABCD1234'),
    randomId: over.randomId ?? (() => 'r1'),
  };
}

const AFFILIATE = {
  name: 'Ada Marketing',
  email: 'Ada@Example.com',
  payoutRatePerUser: 200_000,
  bankName: 'GTBank',
  bankAccountName: 'Ada M',
  bankAccountNumber: '0123456789',
};

describe('createMarketerHandler — auth gate', () => {
  it('rejects a caller without the admin claim', async () => {
    const noClaim = { auth: { uid: 'u1', token: {} } } as unknown as functions.https.CallableContext;
    await expect(createMarketerHandler(AFFILIATE, noClaim, deps()))
      .rejects.toMatchObject({ code: 'permission-denied', message: 'admin_only' });
  });

  it('rejects an unauthenticated caller', async () => {
    const anon = {} as functions.https.CallableContext;
    await expect(createMarketerHandler(AFFILIATE, anon, deps()))
      .rejects.toMatchObject({ code: 'permission-denied' });
  });
});

describe('createMarketerHandler — validation', () => {
  it.each([
    ['missing_name', { ...AFFILIATE, name: '  ' }],
    ['missing_or_invalid_email', { ...AFFILIATE, email: 'not-an-email' }],
    ['invalid_payout_rate', { ...AFFILIATE, payoutRatePerUser: 0 }],
    ['invalid_payout_rate', { ...AFFILIATE, payoutRatePerUser: 2.5 }],
    ['missing_bank_details', { ...AFFILIATE, bankAccountNumber: '123' }],
    ['invalid_type', { ...AFFILIATE, type: 'partner' }],
  ])('rejects with %s', async (message, data) => {
    await expect(createMarketerHandler(data as any, ADMIN, deps()))
      .rejects.toMatchObject({ code: 'invalid-argument', message });
  });
});

describe('createMarketerHandler — affiliate happy path', () => {
  it('mints a code, writes both docs, and returns the links', async () => {
    const { store, db } = makeDb();
    const result = await createMarketerHandler(AFFILIATE, ADMIN, {
      db, now: () => NOW, randomCode: () => 'ABCD1234', randomId: () => 'r1',
    });

    const marketerId = `mkt_${NOW.getTime()}_r1`;
    expect(result).toEqual({
      marketerId,
      code: 'ABCD1234',
      url: 'https://link.getstitchpad.com/r/ABCD1234',
      playUrl: 'https://play.google.com/store/apps/details?id=com.danzucker.stitchpad&referrer=ref%3DABCD1234',
    });
    expect(store.get(`marketers/${marketerId}`)).toMatchObject({
      name: 'Ada Marketing',
      email: 'ada@example.com', // normalized lowercase
      type: 'affiliate',
      referrerUid: null,
      code: 'ABCD1234',
      payoutRatePerUser: 200_000,
      payoutKind: 'cash',
      bankName: 'GTBank',
      bankAccountNumber: '0123456789',
      status: 'active',
      installs: 0, qualified: 0, pendingAmount: 0, paidAmount: 0,
    });
    expect(store.get('referralCodes/ABCD1234')).toMatchObject({ marketerId });
  });

  it('retries on a code collision and uses the next free code', async () => {
    const { store, db } = makeDb({ 'referralCodes/DUP12345': { marketerId: 'someone-else' } });
    let calls = 0;
    const result = await createMarketerHandler(AFFILIATE, ADMIN, {
      db, now: () => NOW,
      randomCode: () => (calls++ === 0 ? 'DUP12345' : 'FRESH678'),
      randomId: () => 'r1',
    });
    expect(result.code).toBe('FRESH678');
    expect(calls).toBe(2);
    expect(store.get('referralCodes/DUP12345')).toEqual({ marketerId: 'someone-else' }); // untouched
    expect(store.get('referralCodes/FRESH678')).toMatchObject({ marketerId: result.marketerId });
  });
});

describe('createMarketerHandler — user referrer (phase 2 shape)', () => {
  it('requires referrerUid for type=user', async () => {
    await expect(createMarketerHandler(
      { name: 'Bola', email: 'b@x.com', payoutRatePerUser: 100_000, type: 'user', payoutKind: 'credit' },
      ADMIN, deps(),
    )).rejects.toMatchObject({ code: 'invalid-argument', message: 'missing_referrer_uid' });
  });

  it('stores referrerUid and needs no bank details when type=user', async () => {
    const { store, db } = makeDb();
    const result = await createMarketerHandler(
      { name: 'Bola', email: 'b@x.com', payoutRatePerUser: 100_000, type: 'user', payoutKind: 'credit', referrerUid: 'tailor-9' },
      ADMIN, { db, now: () => NOW, randomCode: () => 'USERCODE', randomId: () => 'r2' },
    );
    expect(store.get(`marketers/${result.marketerId}`)).toMatchObject({
      type: 'user', referrerUid: 'tailor-9', payoutKind: 'credit', bankName: null,
    });
  });

  it('rejects an affiliate that asks for credit payout', async () => {
    await expect(createMarketerHandler(
      { ...AFFILIATE, payoutKind: 'credit' }, ADMIN, deps(),
    )).rejects.toMatchObject({ code: 'invalid-argument', message: 'affiliate_requires_cash' });
  });

  it.each([['cash'], [undefined]])('rejects a user referrer with %s payout (no bank details collected)', async (kind) => {
    await expect(createMarketerHandler(
      { name: 'Bola', email: 'b@x.com', payoutRatePerUser: 100_000, type: 'user', payoutKind: kind, referrerUid: 'tailor-9' },
      ADMIN, deps(),
    )).rejects.toMatchObject({ code: 'invalid-argument', message: 'user_requires_credit' });
  });
});

import * as functions from 'firebase-functions/v1';
import { markReferralPaidHandler } from '../../referral/markReferralPaid';

// Fake Firestore (enforces read-before-write in transactions).
function makeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const docRef = (path: string): any => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
  });
  const makeQuery = (path: string, filters: any[]): any => ({
    where: (field: string, op: string, val: any) => makeQuery(path, [...filters, { field, op, val }]),
    get: async () => {
      const prefix = `${path}/`;
      const docs = [...store.entries()]
        .filter(([k]) => k.startsWith(prefix) && !k.slice(prefix.length).includes('/'))
        .filter(([, d]) => filters.every((f) => (f.op === '==' ? d[f.field] === f.val : true)))
        .map(([k, d]) => ({ id: k.slice(prefix.length), ref: docRef(k), data: () => d }));
      return { docs, size: docs.length };
    },
  });
  const db: any = {
    doc: (path: string) => docRef(path),
    collection: (path: string) => makeQuery(path, []),
    runTransaction: async (fn: any) => {
      let wrote = false;
      const tx = {
        get: async (ref: any) => {
          if (wrote) throw new Error('read after write');
          return { exists: store.has(ref.path), data: () => store.get(ref.path) };
        },
        set: (ref: any, data: any, opts?: { merge?: boolean }) => {
          wrote = true;
          const prev = store.get(ref.path) ?? {};
          store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
        },
      };
      return fn(tx);
    },
  };
  return { store, db };
}

const deps = (db: any) => ({ db, now: () => new Date('2026-07-12T10:00:00Z') });
const ctx = (admin?: boolean): functions.https.CallableContext =>
  ({ auth: { uid: 'a', token: admin ? { admin: true } : {} } } as unknown as functions.https.CallableContext);

describe('markReferralPaidHandler', () => {
  it('rejects a non-admin caller', async () => {
    await expect(markReferralPaidHandler({ marketerId: 'm1' }, ctx(false), deps(makeDb({}))))
      .rejects.toMatchObject({ code: 'permission-denied' });
  });

  it('rejects an invalid marketerId (path-injection guard)', async () => {
    for (const bad of ['', 'a/b', '../x', 'has space']) {
      await expect(markReferralPaidHandler({ marketerId: bad }, ctx(true), deps(makeDb({}))))
        .rejects.toMatchObject({ code: 'invalid-argument', message: 'invalid_marketer_id' });
    }
  });

  it('marks a marketer’s confirmed payouts paid and moves the money', async () => {
    const { store, db } = makeDb({
      'marketers/m1': { confirmedAmount: 1_500_000, paidAmount: 0 },
      'referrals/a': { payoutState: 'confirmed', marketerId: 'm1', payoutAmount: 1_000_000 },
      'referrals/b': { payoutState: 'confirmed', marketerId: 'm1', payoutAmount: 500_000 },
      'referrals/c': { payoutState: 'confirmed', marketerId: 'm2', payoutAmount: 999 }, // other marketer
      'referrals/d': { payoutState: 'pending', marketerId: 'm1', payoutAmount: 700 },    // not confirmed
    });
    const res = await markReferralPaidHandler({ marketerId: 'm1' }, ctx(true), deps(db));
    expect(res).toEqual({ marketerId: 'm1', paidCount: 2, paidAmount: 1_500_000 });
    expect(store.get('referrals/a').payoutState).toBe('paid');
    expect(store.get('referrals/b').payoutState).toBe('paid');
    expect(store.get('referrals/a').paidAt).toBeDefined();
    expect(store.get('marketers/m1')).toMatchObject({ confirmedAmount: 0, paidAmount: 1_500_000 });
    // Untouched: other marketer + the non-confirmed one.
    expect(store.get('referrals/c').payoutState).toBe('confirmed');
    expect(store.get('referrals/d').payoutState).toBe('pending');
  });

  it('is idempotent — a second call pays nothing more', async () => {
    const { store, db } = makeDb({
      'marketers/m1': { confirmedAmount: 500_000, paidAmount: 0 },
      'referrals/a': { payoutState: 'confirmed', marketerId: 'm1', payoutAmount: 500_000 },
    });
    await markReferralPaidHandler({ marketerId: 'm1' }, ctx(true), deps(db));
    const res2 = await markReferralPaidHandler({ marketerId: 'm1' }, ctx(true), deps(db));
    expect(res2).toEqual({ marketerId: 'm1', paidCount: 0, paidAmount: 0 });
    expect(store.get('marketers/m1')).toMatchObject({ confirmedAmount: 0, paidAmount: 500_000 });
  });

  it('returns zero when the marketer has nothing confirmed', async () => {
    const { db } = makeDb({ 'marketers/m1': { confirmedAmount: 0, paidAmount: 0 } });
    const res = await markReferralPaidHandler({ marketerId: 'm1' }, ctx(true), deps(db));
    expect(res).toEqual({ marketerId: 'm1', paidCount: 0, paidAmount: 0 });
  });
});

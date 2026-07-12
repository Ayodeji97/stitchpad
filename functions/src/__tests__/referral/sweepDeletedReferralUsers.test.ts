import { sweepDeletedReferralUsersHandler } from '../../referral/sweepDeletedReferralUsers';
import { REJECT_ACCOUNT_DELETED } from '../../referral/clawback';

// Fake Firestore (enforces read-before-write in transactions, matching prod).
function matches(v: any, op: string, val: any): boolean {
  if (op === '==') return v === val;
  if (op === 'in') return Array.isArray(val) && val.includes(v);
  return false;
}
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
        .filter(([, data]) => filters.every((f) => matches(data[f.field], f.op, f.val)))
        .map(([k, data]) => ({ id: k.slice(prefix.length), ref: docRef(k), data: () => data }));
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

const deps = (db: any) => ({ db, now: () => new Date('2026-07-12T04:15:00Z') });

describe('sweepDeletedReferralUsersHandler', () => {
  it('claws back a confirmed payout whose user was deleted', async () => {
    const { store, db } = makeDb({
      'marketers/m1': { pendingAmount: 0, confirmedAmount: 1_000_000 },
      'referrals/gone': { payoutState: 'confirmed', marketerId: 'm1', payoutAmount: 1_000_000 },
      // no users/gone doc → account deleted
    });
    const res = await sweepDeletedReferralUsersHandler(deps(db));
    expect(res).toEqual({ scanned: 1, clawedBack: 1, failed: 0 });
    expect(store.get('referrals/gone')).toMatchObject({
      payoutState: 'rejected',
      payoutRejectedReason: REJECT_ACCOUNT_DELETED,
    });
    expect(store.get('marketers/m1').confirmedAmount).toBe(0);
  });

  it('leaves an owed payout whose user still exists', async () => {
    const { store, db } = makeDb({
      'marketers/m1': { pendingAmount: 500_000, confirmedAmount: 0 },
      'referrals/live': { payoutState: 'pending', marketerId: 'm1', payoutAmount: 500_000 },
      'users/live': { businessName: 'Ada Styles' },
    });
    const res = await sweepDeletedReferralUsersHandler(deps(db));
    expect(res).toEqual({ scanned: 1, clawedBack: 0, failed: 0 });
    expect(store.get('referrals/live').payoutState).toBe('pending');
    expect(store.get('marketers/m1').pendingAmount).toBe(500_000);
  });

  it('ignores paid / rejected referrals (not owed)', async () => {
    const { db } = makeDb({
      'referrals/paid': { payoutState: 'paid', marketerId: 'm1' },   // user also gone
      'referrals/rej': { payoutState: 'rejected', marketerId: 'm1' },
    });
    const res = await sweepDeletedReferralUsersHandler(deps(db));
    // The `in ['pending','confirmed']` query excludes both — never scanned.
    expect(res).toEqual({ scanned: 0, clawedBack: 0, failed: 0 });
  });

  it('is idempotent — a second run finds nothing still owed', async () => {
    const { db } = makeDb({
      'marketers/m1': { pendingAmount: 500_000, confirmedAmount: 0 },
      'referrals/gone': { payoutState: 'pending', marketerId: 'm1', payoutAmount: 500_000 },
    });
    await sweepDeletedReferralUsersHandler(deps(db));
    const res2 = await sweepDeletedReferralUsersHandler(deps(db));
    expect(res2).toEqual({ scanned: 0, clawedBack: 0, failed: 0 });
  });
});

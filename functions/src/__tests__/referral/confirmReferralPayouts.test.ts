import * as admin from 'firebase-admin';
import {
  confirmReferralPayoutsHandler,
  decideHoldRelease,
  REJECT_FLAGGED_DURING_HOLD,
} from '../../referral/confirmReferralPayouts';
import { REJECT_ACCOUNT_DELETED } from '../../referral/clawback';
import { DAY_MS } from '../../referral/referralConstants';

// Fake Firestore that enforces Firestore's read-before-write rule inside a
// transaction (so a read-after-write can't pass green) + a chainable
// collection().where() query layer.
function cmp(a: any): number {
  if (a && typeof a.toMillis === 'function') return a.toMillis();
  if (a instanceof Date) return a.getTime();
  return a;
}
function matches(v: any, op: string, val: any): boolean {
  switch (op) {
    case '==': return v === val;
    case '<=': return cmp(v) <= cmp(val);
    default: return false;
  }
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
      const docs: any[] = [];
      for (const [key, data] of store.entries()) {
        if (!key.startsWith(prefix)) continue;
        if (key.slice(prefix.length).includes('/')) continue;
        if (filters.every((f) => matches(data[f.field], f.op, f.val))) {
          docs.push({ id: key.slice(prefix.length), ref: docRef(key), data: () => data });
        }
      }
      return { docs, size: docs.length, empty: docs.length === 0 };
    },
  });
  const db: any = {
    doc: (path: string) => docRef(path),
    collection: (path: string) => makeQuery(path, []),
    runTransaction: async (fn: any) => {
      let hasWritten = false;
      const tx = {
        get: async (ref: any) => {
          if (hasWritten) throw new Error('Firestore transactions require all reads before writes');
          return { exists: store.has(ref.path), data: () => store.get(ref.path) };
        },
        set: (ref: any, data: any, opts?: { merge?: boolean }) => {
          hasWritten = true;
          const prev = store.get(ref.path) ?? {};
          store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
        },
      };
      return fn(tx);
    },
  };
  return { store, db };
}

const ts = (ms: number) => admin.firestore.Timestamp.fromMillis(ms);
const NOW = Date.UTC(2026, 6, 20, 4, 0, 0);
const deps = (db: any) => ({ db, now: () => new Date(NOW) });

function seed(referral: Record<string, any> = {}, marketer: Record<string, any> = {}, seedUser = true) {
  const init: Record<string, any> = {
    'marketers/m1': { pendingAmount: 500_000, confirmedAmount: 0, ...marketer },
    'referrals/u1': {
      payoutState: 'pending',
      flags: [],
      marketerId: 'm1',
      payoutAmount: 500_000,
      holdEndsAt: ts(NOW - DAY_MS), // hold already expired
      ...referral,
    },
  };
  // A qualified/pending referral is always activated, so the user doc exists —
  // unless the account was deleted (the backstop case below).
  if (seedUser) init['users/u1'] = { businessName: 'Ada Styles' };
  return makeDb(init);
}

describe('decideHoldRelease', () => {
  it('confirms a clean pending payout whose user still exists', () => {
    expect(decideHoldRelease('pending', false, true)).toEqual({ action: 'confirm' });
  });
  it('rejects a flagged pending payout', () => {
    expect(decideHoldRelease('pending', true, true)).toEqual({ action: 'reject', reason: REJECT_FLAGGED_DURING_HOLD });
  });
  it('rejects (clawback backstop) when the referred user no longer exists', () => {
    expect(decideHoldRelease('pending', false, false)).toEqual({ action: 'reject', reason: REJECT_ACCOUNT_DELETED });
  });
  it('ignores payouts not in pending', () => {
    expect(decideHoldRelease('confirmed', false, true)).toBeNull();
    expect(decideHoldRelease('none', false, true)).toBeNull();
    expect(decideHoldRelease('paid', true, true)).toBeNull();
  });
});

describe('confirmReferralPayoutsHandler', () => {
  it('confirms an expired-hold clean payout and moves the money', async () => {
    const { store, db } = seed();
    const res = await confirmReferralPayoutsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, confirmed: 1, rejected: 0, failed: 0 });
    expect(store.get('referrals/u1').payoutState).toBe('confirmed');
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 0, confirmedAmount: 500_000 });
  });

  it('rejects a flagged payout at hold-release and reverses the pending amount', async () => {
    const { store, db } = seed({ flags: ['device_reuse'] });
    const res = await confirmReferralPayoutsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, confirmed: 0, rejected: 1, failed: 0 });
    expect(store.get('referrals/u1')).toMatchObject({
      payoutState: 'rejected',
      payoutRejectedReason: REJECT_FLAGGED_DURING_HOLD,
    });
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 0, confirmedAmount: 0 });
  });

  it('confirms a payout flagged only with the advisory missing_device_hash', async () => {
    const { store, db } = seed({ flags: ['missing_device_hash'] });
    const res = await confirmReferralPayoutsHandler(deps(db));
    // Advisory flag is not a "flagged during hold" rejection — the money releases.
    expect(res).toEqual({ scanned: 1, confirmed: 1, rejected: 0, failed: 0 });
    expect(store.get('referrals/u1').payoutState).toBe('confirmed');
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 0, confirmedAmount: 500_000 });
  });

  it('backstops a missed clawback — rejects when the referred user is gone', async () => {
    const { store, db } = seed({}, {}, false); // no users/u1
    const res = await confirmReferralPayoutsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, confirmed: 0, rejected: 1, failed: 0 });
    expect(store.get('referrals/u1')).toMatchObject({
      payoutState: 'rejected',
      payoutRejectedReason: REJECT_ACCOUNT_DELETED,
    });
    expect(store.get('marketers/m1').confirmedAmount).toBe(0);
  });

  it('rejects a malformed pending payout with no marketerId (never re-scanned)', async () => {
    const { store, db } = seed({ marketerId: undefined });
    const res = await confirmReferralPayoutsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, confirmed: 0, rejected: 1, failed: 0 });
    expect(store.get('referrals/u1')).toMatchObject({
      payoutState: 'rejected',
      payoutRejectedReason: 'missing_marketer',
    });
  });

  it('never writes a negative marketer balance (clamped at 0)', async () => {
    // pendingAmount smaller than the payout (e.g. a prior manual edit).
    const { store, db } = seed({}, { pendingAmount: 100_000 });
    await confirmReferralPayoutsHandler(deps(db));
    expect(store.get('marketers/m1').pendingAmount).toBe(0);
    expect(store.get('marketers/m1').confirmedAmount).toBe(500_000);
  });

  it('leaves a payout whose hold has not yet expired', async () => {
    const { store, db } = seed({ holdEndsAt: ts(NOW + DAY_MS) });
    const res = await confirmReferralPayoutsHandler(deps(db));
    expect(res).toEqual({ scanned: 0, confirmed: 0, rejected: 0, failed: 0 });
    expect(store.get('referrals/u1').payoutState).toBe('pending');
  });

  it('is idempotent — a second run finds nothing still pending', async () => {
    const { store, db } = seed();
    await confirmReferralPayoutsHandler(deps(db));
    const res2 = await confirmReferralPayoutsHandler(deps(db));
    expect(res2).toEqual({ scanned: 0, confirmed: 0, rejected: 0, failed: 0 });
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 0, confirmedAmount: 500_000 });
  });
});

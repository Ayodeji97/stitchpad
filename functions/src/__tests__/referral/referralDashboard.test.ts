import * as functions from 'firebase-functions/v1';
import { getReferralDashboardHandler } from '../../referral/referralDashboard';

function makeDb(marketers: Record<string, any>) {
  const store = new Map<string, any>(Object.entries(marketers));
  const db: any = {
    collection: (path: string) => ({
      get: async () => {
        const prefix = `${path}/`;
        const docs = [...store.entries()]
          .filter(([k]) => k.startsWith(prefix) && !k.slice(prefix.length).includes('/'))
          .map(([k, data]) => ({ id: k.slice(prefix.length), data: () => data }));
        return { docs, size: docs.length };
      },
    }),
  };
  return db;
}

const NOW = new Date('2026-07-12T09:00:00Z');
const deps = (db: any) => ({ db, now: () => NOW });
const ctx = (admin?: boolean): functions.https.CallableContext =>
  ({ auth: { uid: 'u', token: admin ? { admin: true } : {} } } as unknown as functions.https.CallableContext);

function marketer(over: Record<string, any> = {}) {
  return {
    name: 'Ada', email: 'ada@x.co', phone: null, code: 'ABCD1234', type: 'affiliate',
    payoutKind: 'cash', status: 'active', bankName: 'GTB', bankAccountName: 'Ada A',
    bankAccountNumber: '0123456789', payoutRatePerUser: 500_000,
    installs: 10, activated: 6, qualified: 3,
    pendingAmount: 500_000, confirmedAmount: 1_000_000, paidAmount: 0, ...over,
  };
}

describe('getReferralDashboardHandler', () => {
  it('rejects a non-admin caller', async () => {
    await expect(getReferralDashboardHandler(ctx(false), deps(makeDb({}))))
      .rejects.toMatchObject({ code: 'permission-denied', message: 'admin_only' });
  });

  it('rejects an unauthenticated caller', async () => {
    await expect(getReferralDashboardHandler({} as functions.https.CallableContext, deps(makeDb({}))))
      .rejects.toMatchObject({ code: 'permission-denied' });
  });

  it('returns marketers + summed totals for an admin', async () => {
    const db = makeDb({
      'marketers/m1': marketer({ name: 'Ada', confirmedAmount: 1_000_000, qualified: 3 }),
      'marketers/m2': marketer({ name: 'Bola', confirmedAmount: 2_000_000, qualified: 5, installs: 20, activated: 12 }),
    });
    const res = await getReferralDashboardHandler(ctx(true), deps(db));

    expect(res.generatedAtMs).toBe(NOW.getTime());
    expect(res.totals).toEqual({
      marketers: 2, installs: 30, activated: 18, qualified: 8,
      pendingAmount: 1_000_000, confirmedAmount: 3_000_000, paidAmount: 0,
    });
    // Sorted most-owed first.
    expect(res.marketers.map((m) => m.name)).toEqual(['Bola', 'Ada']);
    expect(res.marketers[0]).toMatchObject({ id: 'm2', bankAccountNumber: '0123456789', confirmedAmount: 2_000_000 });
  });

  it('coerces missing/garbage aggregate fields to 0 (no NaN in totals)', async () => {
    const db = makeDb({ 'marketers/m1': { name: 'New', email: 'n@x.co', code: 'ZZZZ0000', status: 'active' } });
    const res = await getReferralDashboardHandler(ctx(true), deps(db));
    expect(res.totals).toMatchObject({ marketers: 1, installs: 0, confirmedAmount: 0 });
    expect(res.marketers[0]).toMatchObject({ installs: 0, pendingAmount: 0, phone: null, bankName: null });
  });

  it('returns an empty book without error', async () => {
    const res = await getReferralDashboardHandler(ctx(true), deps(makeDb({})));
    expect(res.marketers).toEqual([]);
    expect(res.totals.marketers).toBe(0);
  });
});

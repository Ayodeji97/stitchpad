import { aggregateFoundingTailorsLeaderboardHandler, monthKeyLagos } from '../../referral/foundingTailorsLeaderboard';

// ── Fake Firestore ───────────────────────────────────────────────────────────
// Same chainable collection().where() query layer as reconcileReferrals.test.ts,
// plus a batch() that the aggregator needs for its multi-doc leaderboard writes.
// No shared helper exists yet for the referral test suite (see
// getOrCreateMyReferralLink.test.ts), so this mirrors that inline pattern.

function cmp(a: any): number {
  if (a && typeof a.toMillis === 'function') return a.toMillis();
  if (a instanceof Date) return a.getTime();
  return a;
}
function matches(v: any, op: string, val: any): boolean {
  switch (op) {
    case '==': return v === val;
    case 'in': return Array.isArray(val) && val.includes(v);
    case '>=': return cmp(v) >= cmp(val);
    case '<': return cmp(v) < cmp(val);
    default: return false;
  }
}
function makeFakeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const docRef = (path: string): any => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
    set: async (data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, opts?.merge ? { ...prev, ...data } : data);
    },
  });
  const makeQuery = (path: string, filters: any[]): any => ({
    where: (field: string, op: string, val: any) => makeQuery(path, [...filters, { field, op, val }]),
    get: async () => {
      const prefix = `${path}/`;
      const docs: any[] = [];
      for (const [key, data] of store.entries()) {
        if (!key.startsWith(prefix)) continue;
        const rest = key.slice(prefix.length);
        if (rest.includes('/')) continue; // direct children only
        if (filters.every((f) => matches(data[f.field], f.op, f.val))) {
          docs.push({ id: rest, ref: docRef(key), data: () => data });
        }
      }
      return { docs, size: docs.length, empty: docs.length === 0, forEach: (fn: (d: any) => void) => docs.forEach(fn) };
    },
  });
  const db: any = {
    doc: (path: string) => docRef(path),
    collection: (path: string) => makeQuery(path, []),
    batch: () => {
      const ops: Array<() => void> = [];
      return {
        set: (ref: any, data: any, opts?: { merge?: boolean }) => {
          ops.push(() => {
            const prev = store.get(ref.path) ?? {};
            store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
          });
        },
        commit: async () => { ops.forEach((op) => op()); },
      };
    },
  };
  return { store, db };
}

const ts = (iso: string) => ({ toMillis: () => new Date(iso).getTime() });

test('counts only qualified, non-blocked referrals of program user-referrers, bucketed by qualifiedAt month', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'marketers/mB': { program: 'founding_tailors', name: 'Bola Wears', type: 'user' },
    'marketers/mAff': { type: 'affiliate', name: 'Paid Marketer' }, // must be excluded
    'referrals/r1': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-05T10:00:00Z'), flags: [] },
    'referrals/r2': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-20T10:00:00Z'), flags: [] },
    'referrals/r3': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-21T10:00:00Z'), flags: ['self_referral'] }, // blocked
    'referrals/r4': { marketerId: 'mB', milestone: 'qualified', qualifiedAt: ts('2026-08-06T10:00:00Z'), flags: [] },
    'referrals/r5': { marketerId: 'mB', milestone: 'activated', flags: [] }, // not qualified
    'referrals/r6': { marketerId: 'mAff', milestone: 'qualified', qualifiedAt: ts('2026-08-06T10:00:00Z'), flags: [] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([
    { marketerId: 'mA', name: 'Ada Styles', points: 2 },
    { marketerId: 'mB', name: 'Bola Wears', points: 1 },
  ]);
  expect((await db.doc('leaderboards/current').get()).data().monthId).toBe('2026-08');
});

test('monthKeyLagos buckets a UTC-evening instant into the correct Lagos month', () => {
  // 2026-07-31T23:30Z is 2026-08-01 00:30 in Lagos (UTC+1)
  expect(monthKeyLagos(new Date('2026-07-31T23:30:00Z').getTime())).toBe('2026-08');
});

test('excludes affiliate marketers from the board even if a referral names their id', () => {
  const { db } = makeFakeDb({
    'marketers/mAff': { type: 'affiliate', name: 'Paid Marketer' },
    'referrals/r1': { marketerId: 'mAff', milestone: 'qualified', qualifiedAt: ts('2026-08-06T10:00:00Z'), flags: [] },
  });

  return aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') }).then(async () => {
    const aug = (await db.doc('leaderboards/2026-08').get()).data();
    expect(aug.entries).toEqual([]);
  });
});

test('writes a current-month doc with an empty board when there are zero qualifying entries', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-09-01T00:00:00Z') });

  const sep = (await db.doc('leaderboards/2026-09').get()).data();
  expect(sep).toEqual({ monthId: '2026-09', updatedAt: expect.anything(), entries: [] });
  expect((await db.doc('leaderboards/current').get()).data().monthId).toBe('2026-09');
});

test('writes the alltime board aggregated across all months, points-desc', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'marketers/mB': { program: 'founding_tailors', name: 'Bola Wears', type: 'user' },
    'referrals/r1': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-07-05T10:00:00Z'), flags: [] },
    'referrals/r2': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-05T10:00:00Z'), flags: [] },
    'referrals/r3': { marketerId: 'mB', milestone: 'qualified', qualifiedAt: ts('2026-08-06T10:00:00Z'), flags: [] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const alltime = (await db.doc('leaderboards/alltime').get()).data();
  expect(alltime.entries).toEqual([
    { marketerId: 'mA', name: 'Ada Styles', points: 2 },
    { marketerId: 'mB', name: 'Bola Wears', points: 1 },
  ]);
});

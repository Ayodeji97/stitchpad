import {
  aggregateFoundingTailorsLeaderboardHandler,
  getFoundingTailorsLeaderboardHandler,
  monthKeyLagos,
} from '../../referral/foundingTailorsLeaderboard';

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
  const docRef = (path: string): any => {
    // Mirror real Firestore: db.doc() requires an even number of path
    // segments (collection/doc/collection/doc/...). An odd count means the
    // caller pointed at a collection, and the real SDK throws synchronously.
    // This matters for the malformed-`code` regression test below: without
    // it, the fake db would silently no-op instead of reproducing the bug.
    const segments = path.split('/').filter(Boolean);
    if (segments.length % 2 !== 0) {
      throw new Error(`Value for argument "documentPath" must point to a document, but was pointed to a collection: ${path}`);
    }
    return {
      path,
      get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
      set: async (data: any, opts?: { merge?: boolean }) => {
        const prev = store.get(path) ?? {};
        store.set(path, opts?.merge ? { ...prev, ...data } : data);
      },
    };
  };
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

test('activated referral with one active day earns activation + day-1 (2 points) in that day month', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': { marketerId: 'mA', milestone: 'activated', flags: [], observedDayKeys: ['2026-08-05'] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([{ marketerId: 'mA', name: 'Ada Styles', points: 2 }]);
  expect((await db.doc('leaderboards/current').get()).data().monthId).toBe('2026-08');
});

test('fully qualified referral with 4 active days in one month earns 5 points', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': {
      marketerId: 'mA', milestone: 'qualified', flags: [],
      observedDayKeys: ['2026-08-02', '2026-08-03', '2026-08-04', '2026-08-05'],
    },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([{ marketerId: 'mA', name: 'Ada Styles', points: 5 }]);
});

test('points split across a month boundary; activation counts in the first active day month', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': {
      marketerId: 'mA', milestone: 'qualified', flags: [],
      observedDayKeys: ['2026-08-30', '2026-08-31', '2026-09-01', '2026-09-02'],
    },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-09-05T00:00:00Z') });

  // Aug: activation (first active day is 08-30) + 08-30 + 08-31 = 3 points.
  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([{ marketerId: 'mA', name: 'Ada Styles', points: 3 }]);
  // Sep: 09-01 + 09-02 = 2 points.
  const sep = (await db.doc('leaderboards/2026-09').get()).data();
  expect(sep.entries).toEqual([{ marketerId: 'mA', name: 'Ada Styles', points: 2 }]);
});

test('a blocking flag withholds ALL points (activation + days)', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': {
      marketerId: 'mA', milestone: 'qualified', flags: ['self_referral'],
      observedDayKeys: ['2026-08-02', '2026-08-03', '2026-08-04', '2026-08-05'],
    },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([]);
});

test('excludes affiliate (non-program) marketers even if a referral names their id', async () => {
  const { db } = makeFakeDb({
    'marketers/mAff': { type: 'affiliate', name: 'Paid Marketer' },
    'referrals/r1': { marketerId: 'mAff', milestone: 'qualified', flags: [], observedDayKeys: ['2026-08-05'] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([]);
});

test('more than 4 active days is capped at 5 points', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': {
      marketerId: 'mA', milestone: 'qualified', flags: [],
      observedDayKeys: ['2026-08-01', '2026-08-02', '2026-08-03', '2026-08-04', '2026-08-05', '2026-08-06'],
    },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([{ marketerId: 'mA', name: 'Ada Styles', points: 5 }]);
});

test('activated referral with no observed active days yet earns 0 points', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': { marketerId: 'mA', milestone: 'activated', flags: [], observedDayKeys: [] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([]);
});

test('an attributed-only referral earns 0 points (excluded by the milestone scan)', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'referrals/r1': { marketerId: 'mA', milestone: 'attributed', flags: [], observedDayKeys: ['2026-08-05'] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([]);
});

test('monthKeyLagos buckets a UTC-evening instant into the correct Lagos month', () => {
  // 2026-07-31T23:30Z is 2026-08-01 00:30 in Lagos (UTC+1)
  expect(monthKeyLagos(new Date('2026-07-31T23:30:00Z').getTime())).toBe('2026-08');
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

test('still writes current + current-month + alltime (all empty) when there are zero program marketers', async () => {
  const { db } = makeFakeDb({});

  await expect(
    aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-09-01T00:00:00Z') }),
  ).resolves.not.toThrow();

  expect((await db.doc('leaderboards/current').get()).data()).toEqual({ monthId: '2026-09', updatedAt: expect.anything() });
  const sep = (await db.doc('leaderboards/2026-09').get()).data();
  expect(sep).toEqual({ monthId: '2026-09', updatedAt: expect.anything(), entries: [] });
  const alltime = (await db.doc('leaderboards/alltime').get()).data();
  expect(alltime).toEqual({ updatedAt: expect.anything(), entries: [] });
});

test('writes the alltime board aggregated across all months, points-desc', async () => {
  const { db } = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'marketers/mB': { program: 'founding_tailors', name: 'Bola Wears', type: 'user' },
    // mA: qualified with 4 days spanning Jul->Aug → 5 points total (3 in Jul, 2 in Aug).
    'referrals/r1': {
      marketerId: 'mA', milestone: 'qualified', flags: [],
      observedDayKeys: ['2026-07-30', '2026-07-31', '2026-08-01', '2026-08-02'],
    },
    // mB: activated with 1 day in Aug → 2 points (activation + day-1).
    'referrals/r2': { marketerId: 'mB', milestone: 'activated', flags: [], observedDayKeys: ['2026-08-06'] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const alltime = (await db.doc('leaderboards/alltime').get()).data();
  expect(alltime.entries).toEqual([
    { marketerId: 'mA', name: 'Ada Styles', points: 5 },
    { marketerId: 'mB', name: 'Bola Wears', points: 2 },
  ]);
});

// ── getFoundingTailorsLeaderboardHandler (public read callable) ────────────

test('returns ranked top rows without codes and resolves you from ?code', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': {
      monthId: '2026-08',
      updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [
        { marketerId: 'mA', name: 'Ada Styles', points: 3 },
        { marketerId: 'mB', name: 'Bola Wears', points: 1 },
      ],
    },
    'referralCodes/CODEB': { marketerId: 'mB' },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEB' }, { db });

  expect(res.monthId).toBe('2026-08');
  expect(res.top).toEqual([
    { rank: 1, name: 'Ada Styles', points: 3 },
    { rank: 2, name: 'Bola Wears', points: 1 },
  ]);
  expect((res.top[0] as any).marketerId).toBeUndefined();
  expect((res.top[0] as any).code).toBeUndefined();
  expect(res.you).toEqual({ rank: 2, points: 1 });
});

test('unknown code yields you=null and never leaks code existence', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
  });
  const res = await getFoundingTailorsLeaderboardHandler({ code: 'NOPE' }, { db });
  expect(res.you).toBeNull();
});

test('valid code but marketer has no entry this month yields you={rank:0,points:0}', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': {
      monthId: '2026-08',
      updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [{ marketerId: 'mA', name: 'Ada Styles', points: 3 }],
    },
    'referralCodes/CODEC': { marketerId: 'mC' },
  });
  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEC' }, { db });
  expect(res.you).toEqual({ rank: 0, points: 0 });
});

test('no code arg yields you=null and returns updatedAt/monthId from the current-month doc', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': {
      monthId: '2026-08',
      updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [{ marketerId: 'mA', name: 'Ada Styles', points: 3 }],
    },
  });
  const res = await getFoundingTailorsLeaderboardHandler({}, { db });
  expect(res.you).toBeNull();
  expect(res.monthId).toBe('2026-08');
  expect(res.updatedAt).toBe(new Date('2026-08-25T00:00:00Z').getTime());
});

test('malformed code containing a slash yields you=null without throwing (public caller, never a lookup)', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': {
      monthId: '2026-08',
      updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [{ marketerId: 'mA', name: 'Ada Styles', points: 3 }],
    },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'a/b' }, { db });

  expect(res.you).toBeNull();
  expect(res.monthId).toBe('2026-08');
  expect(res.top).toEqual([{ rank: 1, name: 'Ada Styles', points: 3 }]);
});

test('falls back to monthKeyLagos(now) when leaderboards/current is missing', async () => {
  const nowMonth = monthKeyLagos(Date.now());
  const { db } = makeFakeDb({
    [`leaderboards/${nowMonth}`]: { monthId: nowMonth, updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
  });
  const res = await getFoundingTailorsLeaderboardHandler({}, { db });
  expect(res.monthId).toBe(nowMonth);
  expect(res.top).toEqual([]);
});

test('resolves youAllTime rank+points from the alltime board', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': {
      monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [
        { marketerId: 'mA', name: 'Ada Styles', points: 3 },
        { marketerId: 'mB', name: 'Bola Wears', points: 1 },
      ],
    },
    'leaderboards/alltime': {
      updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [
        { marketerId: 'mB', name: 'Bola Wears', points: 20 },
        { marketerId: 'mA', name: 'Ada Styles', points: 12 },
      ],
    },
    'referralCodes/CODEA': { marketerId: 'mA' },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEA' }, { db });

  expect(res.you).toEqual({ rank: 1, points: 3 });         // current month
  expect(res.youAllTime).toEqual({ rank: 2, points: 12 }); // lifetime (mB is #1 all time)
});

test('youAllTime is {rank:0,points:0} for a valid code absent from the alltime board', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
    'leaderboards/alltime': { updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
    'referralCodes/CODEA': { marketerId: 'mA' },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEA' }, { db });

  expect(res.you).toEqual({ rank: 0, points: 0 });
  expect(res.youAllTime).toEqual({ rank: 0, points: 0 });
});

test('youAllTime is null for no code and for an unknown code', async () => {
  const { db } = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
    'leaderboards/alltime': { updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] },
  });

  expect((await getFoundingTailorsLeaderboardHandler({}, { db })).youAllTime).toBeNull();
  expect((await getFoundingTailorsLeaderboardHandler({ code: 'NOPE' }, { db })).youAllTime).toBeNull();
});

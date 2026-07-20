import * as admin from 'firebase-admin';
import {
  reconcileReferralsHandler,
  gradeReferral,
  computeActiveDayKeys,
  ratchetObservedDayKeys,
} from '../../referral/reconcileReferrals';
import { HOLD_WINDOW_DAYS, DAY_MS } from '../../referral/referralConstants';

// ── Fake Firestore ───────────────────────────────────────────────────────────
// Extends the recordAttribution harness with a chainable collection().where()
// query layer + a transaction that supports merge writes, which the grader needs.

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
      return { docs, size: docs.length, empty: docs.length === 0 };
    },
  });
  const db: any = {
    doc: (path: string) => docRef(path),
    collection: (path: string) => makeQuery(path, []),
    // A fresh tx per run that enforces Firestore's real read-before-write rule,
    // so a read-after-write (which the emulator/prod rejects) fails here too.
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
const SIGNUP = Date.UTC(2026, 6, 1, 10, 0, 0); // 2026-07-01 10:00 UTC
const WINDOW_END = SIGNUP + 14 * DAY_MS;
const NOW = SIGNUP + 5 * DAY_MS; // inside the window
const deps = (db: any, nowMs = NOW) => ({ db, now: () => new Date(nowMs) });

// Seed: an in-window, not-yet-qualified referral + its marketer + user doc.
function seed(extra: Record<string, any> = {}, referral: Record<string, any> = {}) {
  return makeDb({
    'marketers/m1': { payoutRatePerUser: 500_000, activated: 0, qualified: 0, pendingAmount: 0 },
    'users/u1': { businessName: 'Ada Styles' },
    'referrals/u1': {
      milestone: 'attributed',
      payoutState: 'none',
      flags: [],
      marketerId: 'm1',
      signupAt: ts(SIGNUP),
      qualificationWindowEndsAt: ts(WINDOW_END),
      ...referral,
    },
    ...extra,
  });
}
// Customers on N distinct Lagos days within the window.
function customersOnDays(n: number): Record<string, any> {
  const out: Record<string, any> = {};
  for (let i = 0; i < n; i += 1) {
    out[`users/u1/customers/c${i}`] = { createdAt: SIGNUP + (i + 0.5) * DAY_MS };
  }
  return out;
}

// ── computeActiveDayKeys ─────────────────────────────────────────────────────

describe('computeActiveDayKeys', () => {
  it('dedupes timestamps on the same Lagos day', () => {
    const day = SIGNUP + 2 * DAY_MS; // mid-morning Lagos, safely away from midnight
    expect(computeActiveDayKeys([day, day + 3_600_000, day + 7_200_000], SIGNUP, WINDOW_END)).toHaveLength(1);
  });

  it('counts distinct days, sorted', () => {
    const ms = [0, 1, 2, 3].map((i) => SIGNUP + (i + 0.5) * DAY_MS);
    const keys = computeActiveDayKeys(ms, SIGNUP, WINDOW_END);
    expect(keys).toHaveLength(4);
    expect([...keys]).toEqual([...keys].sort());
  });

  it('excludes activity before signup or at/after the window end', () => {
    const before = SIGNUP - DAY_MS;
    const atEnd = WINDOW_END; // exclusive
    const inside = SIGNUP + 2 * DAY_MS;
    expect(computeActiveDayKeys([before, atEnd, inside], SIGNUP, WINDOW_END)).toEqual([
      computeActiveDayKeys([inside], SIGNUP, WINDOW_END)[0],
    ]);
  });

  it('ignores non-finite timestamps', () => {
    expect(computeActiveDayKeys([NaN, undefined as any, SIGNUP + DAY_MS], SIGNUP, WINDOW_END)).toHaveLength(1);
  });
});

// ── ratchetObservedDayKeys ───────────────────────────────────────────────────

describe('ratchetObservedDayKeys', () => {
  // Convenience: every field explicit, so each test overrides only what it exercises.
  const call = (over: Partial<Parameters<typeof ratchetObservedDayKeys>[0]> = {}) =>
    ratchetObservedDayKeys({
      rawKeys: [],
      observedKeys: [],
      priorActiveKeys: [],
      lastRunDateKey: '2026-07-01',
      runDateKey: '2026-07-02',
      windowStartDateKey: '2026-07-01',
      ...over,
    });

  it('credits one completed day per run for an honest user', () => {
    // Run on 07-02 sees 07-01 activity.
    const r1 = call({ rawKeys: ['2026-07-01'], observedKeys: [], lastRunDateKey: '2026-07-01', runDateKey: '2026-07-02' });
    expect(r1.observedDayKeys).toEqual(['2026-07-01']);
    expect(r1.newlyCredited).toEqual(['2026-07-01']);

    // Run on 07-03 sees 07-02 activity; 07-01 already observed.
    const r2 = call({
      rawKeys: ['2026-07-01', '2026-07-02'],
      observedKeys: r1.observedDayKeys,
      lastRunDateKey: '2026-07-02',
      runDateKey: '2026-07-03',
    });
    expect(r2.observedDayKeys).toEqual(['2026-07-01', '2026-07-02']);
    expect(r2.newlyCredited).toEqual(['2026-07-02']);
  });

  it('credits nothing on the first run for a burst of future-dated keys', () => {
    // The attack: one session writing createdAt = +0/+1/+2/+3 days.
    const r = call({
      rawKeys: ['2026-07-02', '2026-07-03', '2026-07-04', '2026-07-05'],
      observedKeys: [],
      lastRunDateKey: '2026-07-02',
      runDateKey: '2026-07-02',
    });
    expect(r.observedDayKeys).toEqual([]);
    expect(r.newlyCredited).toEqual([]);
    expect(r.futureDated).toBe(true);
  });

  it('flags future-dated keys on every run where they are present', () => {
    const r = call({ rawKeys: ['2026-07-09'], runDateKey: '2026-07-03' });
    expect(r.futureDated).toBe(true);
    expect(r.newlyCredited).toEqual([]);
  });

  it('does not credit a backdated key older than the last run', () => {
    // 06-28 should have been seen by the 07-01 run. Appearing now means backdating.
    const r = call({
      rawKeys: ['2026-06-28'],
      observedKeys: [],
      lastRunDateKey: '2026-07-01',
      runDateKey: '2026-07-02',
    });
    expect(r.observedDayKeys).toEqual([]);
    expect(r.newlyCredited).toEqual([]);
    expect(r.futureDated).toBe(false);
  });

  it('defers a key equal to the run date to the next run', () => {
    const r1 = call({ rawKeys: ['2026-07-02'], lastRunDateKey: '2026-07-01', runDateKey: '2026-07-02' });
    expect(r1.newlyCredited).toEqual([]);

    const r2 = call({
      rawKeys: ['2026-07-02'],
      observedKeys: r1.observedDayKeys,
      lastRunDateKey: '2026-07-02',
      runDateKey: '2026-07-03',
    });
    expect(r2.newlyCredited).toEqual(['2026-07-02']);
  });

  it('credits every completed day in the gap after a grader outage', () => {
    // Last run 07-01, next run 07-05: 07-02..07-04 are all completed and unseen.
    const r = call({
      rawKeys: ['2026-07-02', '2026-07-03', '2026-07-04'],
      observedKeys: [],
      lastRunDateKey: '2026-07-01',
      runDateKey: '2026-07-05',
    });
    expect(r.observedDayKeys).toEqual(['2026-07-02', '2026-07-03', '2026-07-04']);
  });

  it('falls back to the window start when there is no prior run', () => {
    const r = call({
      rawKeys: ['2026-07-01'],
      observedKeys: [],
      lastRunDateKey: undefined,
      runDateKey: '2026-07-02',
      windowStartDateKey: '2026-07-01',
    });
    expect(r.observedDayKeys).toEqual(['2026-07-01']);
  });

  it('seeds from activeDayKeys for an in-flight referral instead of zeroing it', () => {
    // Migration: graded before the ratchet shipped, so observedKeys is absent.
    const r = call({
      rawKeys: ['2026-07-01', '2026-07-02'],
      observedKeys: undefined,
      priorActiveKeys: ['2026-07-01', '2026-07-02'],
      lastRunDateKey: undefined,
      runDateKey: '2026-07-03',
    });
    expect(r.observedDayKeys).toEqual(['2026-07-01', '2026-07-02']);
    expect(r.newlyCredited).toEqual([]);
  });

  it('never shrinks the observed set', () => {
    // Raw keys vanish (e.g. the user deleted the customers) — observed stands.
    const r = call({
      rawKeys: [],
      observedKeys: ['2026-07-01', '2026-07-02'],
      lastRunDateKey: '2026-07-02',
      runDateKey: '2026-07-03',
    });
    expect(r.observedDayKeys).toEqual(['2026-07-01', '2026-07-02']);
  });
});

// ── gradeReferral (pure) ─────────────────────────────────────────────────────

const base = {
  milestone: 'attributed' as const,
  payoutState: 'none' as const,
  activated: false,
  qualifiesByActivity: false,
  hasFlags: false,
  payoutRatePerUser: 500_000,
  nowMs: NOW,
};

describe('gradeReferral', () => {
  it('promotes attributed → activated when set up with a customer', () => {
    const r = gradeReferral({ ...base, activated: true });
    expect(r).toMatchObject({ milestone: 'activated', payoutState: 'none', activatedDelta: 1, qualifiedDelta: 0 });
  });

  it('promotes to qualified and opens a pending payout when clean', () => {
    const r = gradeReferral({ ...base, activated: true, qualifiesByActivity: true });
    expect(r).toMatchObject({
      milestone: 'qualified',
      payoutState: 'pending',
      payoutAmount: 500_000,
      activatedDelta: 1,
      qualifiedDelta: 1,
      pendingAmountDelta: 500_000,
    });
    expect(r?.holdEndsAtMs).toBe(NOW + HOLD_WINDOW_DAYS * DAY_MS);
  });

  it('advances milestone but WITHHOLDS payout when flagged', () => {
    const r = gradeReferral({ ...base, activated: true, qualifiesByActivity: true, hasFlags: true });
    expect(r).toMatchObject({ milestone: 'qualified', payoutState: 'none', payoutAmount: 0, pendingAmountDelta: 0, qualifiedDelta: 1 });
    expect(r?.holdEndsAtMs).toBeNull();
  });

  it('does not double-count activated when jumping from already-activated', () => {
    const r = gradeReferral({ ...base, milestone: 'activated', activated: true, qualifiesByActivity: true });
    expect(r).toMatchObject({ milestone: 'qualified', activatedDelta: 0, qualifiedDelta: 1 });
  });

  it('requires activation — activity alone does not qualify', () => {
    expect(gradeReferral({ ...base, activated: false, qualifiesByActivity: true })).toBeNull();
  });

  it('returns null when nothing changes', () => {
    expect(gradeReferral({ ...base, milestone: 'activated', activated: true })).toBeNull();
    expect(gradeReferral(base)).toBeNull();
  });
});

// ── reconcileReferralsHandler (integration) ──────────────────────────────────

describe('reconcileReferralsHandler', () => {
  it('qualifies a set-up user active on 4 distinct days + opens the payout', async () => {
    const { store, db } = seed(customersOnDays(4));
    const res = await reconcileReferralsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, activated: 1, qualified: 1 });

    const ref = store.get('referrals/u1');
    expect(ref.milestone).toBe('qualified');
    expect(ref.payoutState).toBe('pending');
    expect(ref.payoutAmount).toBe(500_000);
    expect(ref.activeDays).toBe(4);
    expect(ref.holdEndsAt.toMillis()).toBe(NOW + HOLD_WINDOW_DAYS * DAY_MS);

    const m = store.get('marketers/m1');
    expect(m).toMatchObject({ activated: 1, qualified: 1, pendingAmount: 500_000 });
  });

  it('activates (no payout) when set up but under the distinct-day bar', async () => {
    const { store, db } = seed(customersOnDays(3));
    const res = await reconcileReferralsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, activated: 1, qualified: 0 });
    expect(store.get('referrals/u1').milestone).toBe('activated');
    expect(store.get('referrals/u1').payoutState).toBe('none');
    expect(store.get('marketers/m1')).toMatchObject({ activated: 1, qualified: 0, pendingAmount: 0 });
  });

  it('does nothing when the workshop is not set up, even with 4 active days', async () => {
    const { store, db } = seed({ ...customersOnDays(4), 'users/u1': { businessName: '   ' } });
    const res = await reconcileReferralsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, activated: 0, qualified: 0 });
    expect(store.get('referrals/u1').milestone).toBe('attributed');
  });

  it('counts a measurement as a meaningful write toward a distinct day', async () => {
    // 3 customer-days + a measurement on a distinct 4th day = qualified.
    const { store, db } = seed({
      ...customersOnDays(3),
      'users/u1/customers/c0/measurements/mm1': { createdAt: SIGNUP + 3.5 * DAY_MS },
    });
    const res = await reconcileReferralsHandler(deps(db));
    expect(res.qualified).toBe(1);
    expect(store.get('referrals/u1').milestone).toBe('qualified');
  });

  it('withholds the payout for a flagged referral but still advances it', async () => {
    const { store, db } = seed(customersOnDays(4), { flags: ['self_referral'] });
    const res = await reconcileReferralsHandler(deps(db));
    expect(res.qualified).toBe(1);
    expect(store.get('referrals/u1').milestone).toBe('qualified');
    expect(store.get('referrals/u1').payoutState).toBe('none');
    expect(store.get('referrals/u1').payoutAmount).toBeUndefined();
    expect(store.get('marketers/m1').pendingAmount).toBe(0);
  });

  it('pays out a referral flagged only with the advisory missing_device_hash', async () => {
    const { store, db } = seed(customersOnDays(4), { flags: ['missing_device_hash'] });
    const res = await reconcileReferralsHandler(deps(db));
    expect(res.qualified).toBe(1);
    expect(store.get('referrals/u1').milestone).toBe('qualified');
    // Advisory flag does NOT withhold — the payout opens as pending.
    expect(store.get('referrals/u1').payoutState).toBe('pending');
    expect(store.get('marketers/m1').pendingAmount).toBe(500_000);
  });

  it('refreshes the cached day-count even when the milestone does not change', async () => {
    // Already activated, stale stored day-count of 2, now genuinely 3 (still < 4).
    const { store, db } = seed(customersOnDays(3), {
      milestone: 'activated',
      activeDays: 2,
      activeDayKeys: ['2026-06-30', '2026-07-01'],
    });
    const res = await reconcileReferralsHandler(deps(db));
    expect(res).toEqual({ scanned: 1, activated: 0, qualified: 0 });
    expect(store.get('referrals/u1').milestone).toBe('activated');
    expect(store.get('referrals/u1').activeDays).toBe(3);
  });

  it('is idempotent — a second run makes no further change', async () => {
    const { store, db } = seed(customersOnDays(4));
    await reconcileReferralsHandler(deps(db));
    const res2 = await reconcileReferralsHandler(deps(db));
    // The now-qualified referral is out of the candidate set entirely.
    expect(res2).toEqual({ scanned: 0, activated: 0, qualified: 0 });
    expect(store.get('marketers/m1')).toMatchObject({ activated: 1, qualified: 1, pendingAmount: 500_000 });
  });

  it('still qualifies final-window-day activity graded within the grace period', async () => {
    // Window closed 1 day ago (< grace) — the user finished their 4th distinct
    // in-window day after the last in-window run, so tonight must still catch it.
    const { store, db } = seed(customersOnDays(4));
    const res = await reconcileReferralsHandler(deps(db, WINDOW_END + DAY_MS));
    expect(res).toEqual({ scanned: 1, activated: 1, qualified: 1 });
    expect(store.get('referrals/u1').milestone).toBe('qualified');
    expect(store.get('referrals/u1').payoutState).toBe('pending');
  });

  it('drops referrals more than the grace period past their window', async () => {
    const { store, db } = seed(customersOnDays(4));
    const res = await reconcileReferralsHandler(deps(db, WINDOW_END + 3 * DAY_MS)); // beyond grace
    expect(res.scanned).toBe(0);
    expect(store.get('referrals/u1').milestone).toBe('attributed');
  });
});

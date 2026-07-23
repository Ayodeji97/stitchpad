# Referral Active-Day Ratchet (Lane A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Credit referral active-days only when a nightly grader run actually observed them for an already-completed day, so a single session of future-dated `createdAt` writes can no longer qualify a marketer payout instantly.

**Architecture:** Add a pure exported function `ratchetObservedDayKeys()` to `functions/src/referral/reconcileReferrals.ts` that takes the raw window-bounded day-keys plus the referral's prior ratchet state and returns the new monotonically-growing `observedDayKeys`. The handler persists `observedDayKeys` / `lastObservedRunDateKey` on `referrals/{uid}` and switches the qualification test from the raw recomputed set to the observed set. `activeDayKeys` / `activeDays` keep their current meaning for the dashboard and audit trail.

**Tech Stack:** TypeScript, Firebase Cloud Functions v1, Jest. All work is server-side; no client or `firestore.rules` change (that is Lane B).

**Spec:** `docs/superpowers/specs/2026-07-20-referral-active-day-ratchet-design.md`

## Global Constraints

- Branch: `fix/referral-active-day-ratchet`. Do not commit to `main`; the work merges via PR.
- Day-keys are `'YYYY-MM-DD'` Africa/Lagos strings from `lagosDateKey()` (`functions/src/notifications/lagosTime.ts:15`). Lexicographic `<` / `>=` is chronological — compare them as strings, never parse to dates.
- `QUALIFY_DISTINCT_DAYS = 4` and `RECONCILE_GRACE_DAYS = 2` are existing constants. Do not change either value.
- `future_dated_activity` is **advisory**: add it to the `ReferralFlag` union but do NOT add it to `BLOCKING_FLAGS`.
- `observedDayKeys` must never shrink. Every write is a union with the prior value.
- The ratchet function must be pure — no Firestore access, no `Date.now()`. All time enters as `runDateKey`.
- Run tests with `npm test` from `functions/`. Lint with `npm run lint` from `functions/` (CI runs lint first — see `feedback_functions_ci_eslint`).
- Do not touch `referralDashboard.ts`. It exposes marketer-level aggregates only; there is nowhere for per-referral state to land.

---

### Task 1: Pure ratchet function

**Files:**
- Modify: `functions/src/referral/reconcileReferrals.ts` (add after `computeActiveDayKeys`, ~line 73)
- Test: `functions/src/__tests__/referral/reconcileReferrals.test.ts` (new `describe` block after the `computeActiveDayKeys` block, ~line 190)

**Interfaces:**
- Consumes: `lagosDateKey` (already imported in the module), `computeActiveDayKeys` output shape (`string[]` of `'YYYY-MM-DD'`).
- Produces: `ratchetObservedDayKeys(input: RatchetInput): RatchetResult`, plus exported interfaces `RatchetInput` and `RatchetResult`. Task 2 calls this from inside the handler transaction.

**Semantics** (each rule exists for a reason — do not "simplify" them):

| Rule | Why |
|---|---|
| `k > runDateKey` ⇒ `futureDated = true` | A correct client cannot produce a future day-key. |
| eligible = `k < runDateKey` (strict) | The 03:30 run on date *D* is *inside* day *D*; crediting *D* would hand out a free day each night. |
| newly = `k >= lastRunDateKey` (inclusive) | The run on *D* sees activity from *D-1*, which the *D-1* run (03:30, before that activity) could not have seen. `>` would reject every genuine day. |
| first run floor: `max(windowStartDateKey, prevDayKey(runDateKey))` | No prior run. The floor is clamped to *yesterday* so a first run credits at most the one day it could legitimately have observed. Using the raw attribution date would let a dormant in-flight referral (which persists no `activeDayKeys`, so the migration branch can't fire) credit every backdated day in the window at once — the original bypass. |
| migration: `observedKeys` absent but `priorActiveKeys` present ⇒ seed from `priorActiveKeys`, credit nothing this run | In-flight referrals must not lose accrued days. Accepted consequence: days already forged by in-flight referrals are grandfathered (bounded window, one known marketer, 7-day hold + manual `markReferralPaid` still apply). |

- [ ] **Step 1: Write the failing tests**

Add to `functions/src/__tests__/referral/reconcileReferrals.test.ts`. Import `ratchetObservedDayKeys` by adding it to the existing import block at the top of the file:

```typescript
import {
  reconcileReferralsHandler,
  gradeReferral,
  computeActiveDayKeys,
  ratchetObservedDayKeys,
} from '../../referral/reconcileReferrals';
```

Then add this block after the `computeActiveDayKeys` describe block:

```typescript
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

  it('caps a genuine first run (no prior run, dormant referral) to a single day', () => {
    // The dormant-referral bypass: no observedKeys, no priorActiveKeys, no
    // lastRunDateKey. A backdated burst across the whole window must NOT all
    // credit at once — the yesterday-clamp allows only the single completed day.
    const r = call({
      rawKeys: ['2026-07-01', '2026-07-02', '2026-07-03', '2026-07-04'],
      observedKeys: [],
      priorActiveKeys: undefined,
      lastRunDateKey: undefined,
      runDateKey: '2026-07-05',
      windowStartDateKey: '2026-07-01',
    });
    // Floor = max('2026-07-01', prevDay('2026-07-05')='2026-07-04') = '2026-07-04'.
    expect(r.newlyCredited).toEqual(['2026-07-04']);
  });

  it('treats both-undefined as a normal new referral, not a migration', () => {
    // observedKeys AND priorActiveKeys undefined = a fresh referral with no prior
    // ratchet state. Migration must NOT fire; normal crediting applies.
    const r = call({
      rawKeys: ['2026-07-01'],
      observedKeys: undefined,
      priorActiveKeys: undefined,
      lastRunDateKey: '2026-07-01',
      runDateKey: '2026-07-02',
    });
    expect(r.newlyCredited).toEqual(['2026-07-01']);
  });

  it('treats undefined-observed + EMPTY activeDayKeys as a normal new referral', () => {
    // The REAL production shape of a brand-new referral: recordAttribution writes
    // activeDayKeys: [], and observedDayKeys is absent. Migration must NOT fire on
    // an empty prior set — otherwise the signup-day activity is stranded forever.
    const r = call({
      rawKeys: ['2026-07-01'],
      observedKeys: undefined,
      priorActiveKeys: [],           // <- empty, as recordAttribution initializes it
      lastRunDateKey: '2026-07-01',
      runDateKey: '2026-07-02',
    });
    expect(r.newlyCredited).toEqual(['2026-07-01']); // credited, NOT dropped as a migration
  });

  it('does not treat observedKeys:[] + priorActiveKeys present as a migration', () => {
    // A referral already on the ratchet (observedKeys defined, even if empty) must
    // take the normal path, never the migration seed.
    const r = call({
      rawKeys: ['2026-07-01'],
      observedKeys: [],
      priorActiveKeys: ['2026-06-01', '2026-06-02'],
      lastRunDateKey: '2026-07-01',
      runDateKey: '2026-07-02',
    });
    expect(r.observedDayKeys).toEqual(['2026-07-01']); // NOT the June priorActiveKeys
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
    // A key ON the run date is a day still in progress, not fraud — must not flag.
    // (Pins futureDated at the k===runDateKey boundary: `>` must not become `>=`.)
    expect(r1.futureDated).toBe(false);

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
    // Floor = max('2026-07-01', prevDay('2026-07-02')='2026-07-01') = '2026-07-01'.
    expect(r.observedDayKeys).toEqual(['2026-07-01']);
  });

  it('window start restricts first-run crediting when it is later than yesterday', () => {
    // windowStart must STRICTLY exceed prevDay(run) to bind the max(); otherwise
    // prevDay(run) dominates and the test can't isolate windowStart's role. Here
    // attribution is "today": windowStart '2026-07-10' beats prevDay('2026-07-10')
    // = '2026-07-09'. A completed-yesterday key would clear the yesterday-floor but
    // is excluded by the later windowStart floor — so this pins windowStart, and a
    // `floor = lastRunDateKey ?? prevDayKey(runDateKey)` refactor (dropping
    // windowStart) would wrongly credit it and fail this test.
    const r = call({
      rawKeys: ['2026-07-09'],
      observedKeys: [],
      lastRunDateKey: undefined,
      runDateKey: '2026-07-10',
      windowStartDateKey: '2026-07-10',
    });
    // Floor = max('2026-07-10', prevDay('2026-07-10')='2026-07-09') = '2026-07-10';
    // '2026-07-09' < floor, so nothing credits.
    expect(r.newlyCredited).toEqual([]);
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
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts -t ratchetObservedDayKeys
```

Expected: FAIL. TypeScript reports `ratchetObservedDayKeys` is not exported from `../../referral/reconcileReferrals`.

- [ ] **Step 3: Write the implementation**

In `functions/src/referral/reconcileReferrals.ts`, insert directly after the closing brace of `computeActiveDayKeys` (~line 73):

```typescript
// ── Pure: server-observed day ratchet ────────────────────────────────────────

export interface RatchetInput {
  // CONTRACT: observedKeys and priorActiveKeys must be passed as the RAW stored
  // field values — do NOT default them with `?? []` at the call site. The
  // `observedKeys === undefined && priorActiveKeys !== undefined` migration test
  // is load-bearing: `?? []` on either one silently mis-routes every in-flight or
  // brand-new referral. Task 2 passes `data.observedDayKeys` / `data.activeDayKeys`
  // directly for exactly this reason.

  /** Window-bounded day-keys recomputed this run (from computeActiveDayKeys). */
  rawKeys: string[];
  /** Prior observedDayKeys; undefined for a referral graded before the ratchet shipped. */
  observedKeys: string[] | undefined;
  /** Prior activeDayKeys; used only to seed the migration case. */
  priorActiveKeys: string[] | undefined;
  /** Lagos date-key of the previous grader run; undefined on the first run. */
  lastRunDateKey: string | undefined;
  /** Lagos date-key of THIS grader run. */
  runDateKey: string;
  /** Lagos date-key of the qualification window start (attribution date). */
  windowStartDateKey: string;
}

export interface RatchetResult {
  /** Monotonic union of previously-observed and newly-credited keys, sorted. */
  observedDayKeys: string[];
  /** Keys credited by THIS run (empty on a migration seed). */
  newlyCredited: string[];
  /** A raw key dated after the run date — impossible for a correct client. */
  futureDated: boolean;
}

/** The Lagos date-key one calendar day before `key` ('2026-07-05' -> '2026-07-04'). */
function prevDayKey(key: string): string {
  const [y, m, d] = key.split('-').map(Number);
  // UTC math on a date-only key never crosses a DST/offset boundary, so this is a
  // clean -1 day. lagosDateKey already folds the Lagos offset into the key.
  const prev = new Date(Date.UTC(y, m - 1, d) - 86_400_000);
  return prev.toISOString().slice(0, 10);
}

/**
 * Credits a day-key only when this run could legitimately have observed it: the
 * day is already over, and it is not older than the run that should have caught
 * it. The nightly cadence is the unforgeable clock — a burst of future-dated
 * writes yields at most one credited day per run, so reaching
 * QUALIFY_DISTINCT_DAYS takes as many nights as an honest user needs.
 *
 * Day-keys are 'YYYY-MM-DD', so string comparison is chronological.
 */
export function ratchetObservedDayKeys(input: RatchetInput): RatchetResult {
  const { rawKeys, observedKeys, priorActiveKeys, lastRunDateKey, runDateKey, windowStartDateKey } = input;

  const futureDated = rawKeys.some((k) => k > runDateKey);

  // Migration: graded before the ratchet existed. Seed from the accrued raw set
  // so a legitimate in-flight referral doesn't lose its days and become unable to
  // qualify before its window closes. Credit nothing else this run — from the
  // next run on, the normal rules apply.
  //
  // The `length > 0` guard is REQUIRED, not defensive: recordAttribution.ts:192
  // initializes every new referral with `activeDayKeys: []`, so `!== undefined`
  // alone is true for brand-new referrals too. Without the length check a fresh
  // referral is misclassified as a migration, seeds observedDayKeys from [], stamps
  // lastObservedRunDateKey, and permanently strands its signup-day activity below
  // the next floor — an honest 4-day user loses day 1 and never qualifies. An empty
  // priorActiveKeys has no accrued days to preserve, so it correctly falls through
  // to the normal path.
  if (observedKeys === undefined && priorActiveKeys !== undefined && priorActiveKeys.length > 0) {
    return {
      observedDayKeys: Array.from(new Set(priorActiveKeys)).sort(),
      newlyCredited: [],
      futureDated,
    };
  }

  const observed = new Set(observedKeys ?? []);
  // Floor = the earlier bound on creditable days.
  //  - With a prior run: keys >= that run's date; earlier days were its job.
  //  - First run (no prior run): clamp to YESTERDAY, not the attribution date. A
  //    dormant in-flight referral persists no activeDayKeys (the grader skips the
  //    write when nothing changed), so the migration branch can't fire and the
  //    attribution-date floor would credit every backdated window-day at once —
  //    the exact bypass this ratchet closes. `max(windowStart, prevDay(run))`
  //    caps a first run to the one day it could legitimately have observed.
  const floor = lastRunDateKey ?? maxKey(windowStartDateKey, prevDayKey(runDateKey));

  const newlyCredited = rawKeys
    .filter((k) => k < runDateKey)   // completed days only
    .filter((k) => k >= floor)       // not older than the run that should have seen it
    .filter((k) => !observed.has(k));

  const deduped = Array.from(new Set(newlyCredited)).sort();
  for (const k of deduped) observed.add(k);

  return {
    observedDayKeys: Array.from(observed).sort(),
    newlyCredited: deduped,
    futureDated,
  };
}

/** The chronologically later of two 'YYYY-MM-DD' keys (lexicographic == chronological). */
function maxKey(a: string, b: string): string {
  return a >= b ? a : b;
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts -t ratchetObservedDayKeys
```

Expected: PASS, 13 tests.

- [ ] **Step 5: Lint**

```bash
cd functions && npm run lint
```

Expected: exit 0, no errors.

- [ ] **Step 6: Commit**

```bash
git add functions/src/referral/reconcileReferrals.ts functions/src/__tests__/referral/reconcileReferrals.test.ts
git commit -m "feat(referral): pure server-observed day ratchet

Credits an active-day key only when a grader run could legitimately have
observed it: the day is over, and it is not older than the run that should
have caught it. Pure + fully unit-tested; not yet wired into the handler."
```

---

### Task 2: Wire the ratchet into the grader

**Files:**
- Modify: `functions/src/referral/referralConstants.ts:67` (add the flag to the union)
- Modify: `functions/src/referral/reconcileReferrals.ts` (transaction body, ~lines 258-315)
- Test: `functions/src/__tests__/referral/reconcileReferrals.test.ts` (`reconcileReferralsHandler` describe block)

**Interfaces:**
- Consumes: `ratchetObservedDayKeys(input: RatchetInput): RatchetResult` from Task 1.
- Produces: two new persisted fields on `referrals/{uid}` — `observedDayKeys: string[]` and `lastObservedRunDateKey: string`. No other module reads them.

**Key change:** `qualifiesByActivity` currently comes from `signals.activeDayKeys.length` and is computed *outside* the transaction (`reconcileReferrals.ts:249`). It must move *inside*, because the ratchet reads `observedDayKeys` — a racy field that a concurrent run or a manual debug invocation could also be writing.

- [ ] **Step 1: Add the advisory flag**

In `functions/src/referral/referralConstants.ts`, replace line 67:

```typescript
export type ReferralFlag = 'self_referral' | 'device_reuse' | 'velocity' | 'missing_device_hash' | 'future_dated_activity';
```

Leave `BLOCKING_FLAGS` (lines 70-74) **unchanged**. Advisory is deliberate: the ratchet has already denied the credit, so the payout is withheld by arithmetic. Making it blocking would let a client-clock bug permanently poison a legitimate referral.

- [ ] **Step 2: Write the failing handler tests**

Add these to the existing `describe('reconcileReferralsHandler', ...)` block:

```typescript
  it('does not qualify a burst of future-dated writes', async () => {
    // The attack: 4 customers in one session, dated today..+3 days.
    const runDay = NOW;
    const burst: Record<string, any> = {};
    for (let i = 0; i < 4; i += 1) {
      burst[`users/u1/customers/c${i}`] = { createdAt: runDay + i * DAY_MS };
    }
    const { store, db } = seed(burst);

    const res = await reconcileReferralsHandler(deps(db));
    expect(res.qualified).toBe(0);

    const ref = store.get('referrals/u1');
    expect(ref.milestone).not.toBe('qualified');
    expect(ref.payoutState).toBe('none');
    expect(ref.observedDayKeys).toEqual([]);
    expect(ref.flags).toContain('future_dated_activity');
  });

  it('keeps future_dated_activity advisory so the milestone still advances', async () => {
    // Activation (businessName + >=1 customer) must not be blocked by the flag.
    const { store, db } = seed({ 'users/u1/customers/c0': { createdAt: NOW + 3 * DAY_MS } });

    await reconcileReferralsHandler(deps(db));

    const ref = store.get('referrals/u1');
    expect(ref.flags).toContain('future_dated_activity');
    expect(ref.milestone).toBe('activated');
  });

  it('seeds observedDayKeys from activeDayKeys for an in-flight referral', async () => {
    // Graded before the ratchet shipped: activeDayKeys present, observedDayKeys absent.
    const { store, db } = seed(customersOnDays(4), {
      activeDayKeys: ['2026-07-01', '2026-07-02', '2026-07-03', '2026-07-04'],
      activeDays: 4,
    });

    await reconcileReferralsHandler(deps(db));

    const ref = store.get('referrals/u1');
    expect(ref.observedDayKeys).toEqual(['2026-07-01', '2026-07-02', '2026-07-03', '2026-07-04']);
    expect(ref.lastObservedRunDateKey).toBeDefined();
  });

  it('credits final-window-day activity within the reconcile grace', async () => {
    // Activity on the last window day, graded on the following run — the one place
    // "completed days only" could silently cost a legitimate payout.
    const lastDay = WINDOW_END - 0.5 * DAY_MS;
    const { store, db } = seed(
      {
        'users/u1/customers/c0': { createdAt: SIGNUP + 0.5 * DAY_MS },
        'users/u1/customers/c1': { createdAt: SIGNUP + 1.5 * DAY_MS },
        'users/u1/customers/c2': { createdAt: SIGNUP + 2.5 * DAY_MS },
        'users/u1/customers/c3': { createdAt: lastDay },
      },
      {
        observedDayKeys: ['2026-07-01', '2026-07-02', '2026-07-03'],
        lastObservedRunDateKey: '2026-07-14',
      },
    );

    // Run the day AFTER the window closed, still inside RECONCILE_GRACE_DAYS.
    const res = await reconcileReferralsHandler(deps(db, WINDOW_END + 0.5 * DAY_MS));

    expect(res.qualified).toBe(1);
    expect(store.get('referrals/u1').milestone).toBe('qualified');
  });

  it('never shrinks observedDayKeys across runs', async () => {
    const { store, db } = seed(customersOnDays(2), {
      observedDayKeys: ['2026-07-01', '2026-07-02'],
      lastObservedRunDateKey: '2026-07-03',
    });

    await reconcileReferralsHandler(deps(db));

    expect(store.get('referrals/u1').observedDayKeys).toEqual(
      expect.arrayContaining(['2026-07-01', '2026-07-02']),
    );
  });
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts -t reconcileReferralsHandler
```

Expected: FAIL. The new assertions fail because `observedDayKeys` is never written and `future_dated_activity` is never set.

**⚠️ Six pre-existing handler tests WILL break after wiring — this is expected, and Step 4.5 migrates them.** They `seed(customersOnDays(4))` (or 3 + a measurement) and call the handler ONCE, several days post-signup, expecting 4 already-elapsed days to credit in that single call. Task 1's first-run floor now correctly forbids that — a never-before-graded referral credits at most one day per run, because the ratchet cannot tell a genuinely-dormant referral's first look from a backdated fraud burst. In real nightly cadence an honest user still qualifies (the `observedChanged === (f.observedDayKeys === undefined)` guard forces a seeding write on the very first grading night even with zero activity, so `lastObservedRunDateKey` is set by night 1 and days accrue one per night thereafter). The six affected tests: `qualifies a set-up user active on 4 distinct days`, `counts a measurement as a meaningful write`, `withholds the payout for a flagged referral`, `pays out a referral flagged only with the advisory missing_device_hash`, `is idempotent`, and the grace-period qualification test. Do NOT loosen the ratchet floor to make them pass — that reopens the bypass. Migrate them per Step 4.5.

- [ ] **Step 4: Wire the handler**

In `functions/src/referral/reconcileReferrals.ts`:

**4a.** Import `lagosDateKey` if it is not already imported at the top of the file (it is used by `computeActiveDayKeys`, so it should be — verify before adding a duplicate import):

```typescript
import { lagosDateKey } from '../notifications/lagosTime';
```

**4b.** Delete the pre-transaction qualification line (currently `reconcileReferrals.ts:249`):

```typescript
    const qualifiesByActivity = signals.activeDayKeys.length >= QUALIFY_DISTINCT_DAYS;
```

**4c.** Extend the typed transaction read to pull the ratchet fields. Replace the `const f = fresh.data() as {...}` block with:

```typescript
      const f = fresh.data() as {
        milestone: ReferralMilestone;
        payoutState: PayoutState;
        flags?: ReferralFlag[];
        marketerId: string;
        activeDayKeys?: string[];
        observedDayKeys?: string[];
        lastObservedRunDateKey?: string;
      };
```

**4d.** Immediately after the marketer read (`const m = (await tx.get(marketerRef)).data() ?? {};`), apply the ratchet and derive qualification from it:

```typescript
      const runDateKey = lagosDateKey(nowMs);
      const ratchet = ratchetObservedDayKeys({
        rawKeys: signals.activeDayKeys,
        observedKeys: f.observedDayKeys,
        priorActiveKeys: f.activeDayKeys,
        lastRunDateKey: f.lastObservedRunDateKey,
        runDateKey,
        windowStartDateKey: lagosDateKey(windowStartMs),
      });
      // Qualification is driven by the ratcheted set, NOT the raw recomputed one.
      const qualifiesByActivity = ratchet.observedDayKeys.length >= QUALIFY_DISTINCT_DAYS;
      const flags: ReferralFlag[] = ratchet.futureDated && !(f.flags ?? []).includes('future_dated_activity')
        ? [...(f.flags ?? []), 'future_dated_activity']
        : (f.flags ?? []);
```

**4e.** Extend the change-detection so a ratchet-only change still writes. Replace the `daysChanged` block with:

```typescript
      const prevKeys = f.activeDayKeys ?? [];
      const daysChanged =
        prevKeys.length !== signals.activeDayKeys.length ||
        prevKeys.some((k, i) => k !== signals.activeDayKeys[i]);
      const prevObserved = f.observedDayKeys ?? [];
      const observedChanged =
        f.observedDayKeys === undefined ||
        prevObserved.length !== ratchet.observedDayKeys.length ||
        f.lastObservedRunDateKey !== runDateKey;
      const flagsChanged = flags.length !== (f.flags ?? []).length;
      if (!grade && !daysChanged && !observedChanged && !flagsChanged) return null;
```

**4f.** Persist the new fields. Extend the `update` object:

```typescript
      const update: Record<string, unknown> = {
        activeDays: signals.activeDayKeys.length,
        activeDayKeys: signals.activeDayKeys,
        observedDayKeys: ratchet.observedDayKeys,
        lastObservedRunDateKey: runDateKey,
        flags,
        updatedAt: nowTs,
      };
```

**4g.** The `gradeReferral(...)` call already receives `qualifiesByActivity` and `hasFlags: hasBlockingFlag(f.flags)`. Change the flags argument to use the updated array so a run that adds the advisory flag stays consistent:

```typescript
        hasFlags: hasBlockingFlag(flags),
```

- [ ] **Step 4.5: Migrate the six collided pre-existing tests**

Each of the six broke because it compressed multiple nightly runs into one call. Under the ratchet, a single call cannot retroactively credit already-elapsed days. Migrate them to the ratchet's reality, keeping each test's ORIGINAL subject and assertions — change only the seed so qualification is reachable.

**First fix the default `seed()` fixture so it mirrors production** (codex review, 2026-07-22). `recordAttribution.ts:192` creates every referral with `activeDays: 0, activeDayKeys: []`, but the test `seed()` helper omits them — which masked the migration-misclassification P1 (a bare `seed()` referral had `activeDayKeys: undefined`, taking the both-undefined normal path, while a real referral has `[]` and hit the migration branch). Add both fields to the default `referrals/u1` doc in `seed()`:

```typescript
    'referrals/u1': {
      milestone: 'attributed',
      payoutState: 'none',
      flags: [],
      marketerId: 'm1',
      signupAt: ts(SIGNUP),
      qualificationWindowEndsAt: ts(WINDOW_END),
      activeDays: 0,
      activeDayKeys: [],          // <- mirror recordAttribution; exercises the real migration guard
      ...referral,
    },
```

With this fixture change, the new accumulation test (and any bare-`seed()` test) genuinely exercises the brand-new production shape — and would FAIL against the un-guarded migration condition, which is what makes it a real regression test for the P1.

**Add this helper** near the other test helpers (after `customersOnDays`, ~line 107):

```typescript
// Seed a referral as if prior nightly runs already observed `priorKeys`, with the
// last run on `lastRunDateKey`. A single handler call then credits only the days in
// [lastRunDateKey, runDateKey) — the ratchet's real per-run behaviour — so a test
// that wants to reach the 4-day bar in one call must supply 3 prior observed days
// plus fresh activity on the day the current run will credit.
function priorNights(priorKeys: string[], lastRunDateKey: string) {
  return { observedDayKeys: priorKeys, lastObservedRunDateKey: lastRunDateKey };
}
```

**The pattern for a single-call qualification** (used by tests 1-4 below): pre-seed 3 completed observed days, set `lastObservedRunDateKey` to the day before `runDateKey` (`lagosDateKey(NOW)`), and ensure one customer's `createdAt` lands on that same day-before so the current run credits the 4th day and tips qualification. `NOW = SIGNUP + 5*DAY_MS` → `runDateKey = '2026-07-06'`, so `prevDay = '2026-07-05'`; a customer at `SIGNUP + 4.5*DAY_MS` is `'2026-07-05'`. Concretely, replace `seed(customersOnDays(4))` with:

```typescript
    const { store, db } = seed(
      {
        'users/u1/customers/c3': { createdAt: SIGNUP + 4.5 * DAY_MS }, // '2026-07-05' — credited this run
      },
      priorNights(['2026-07-01', '2026-07-02', '2026-07-03'], '2026-07-05'),
    );
```

Apply that same seed swap to:
1. `qualifies a set-up user active on 4 distinct days + opens the payout` — assertions unchanged (`qualified: 1`, `payoutAmount 500_000`, `holdEndsAt`, marketer rollup); note `activeDays` now reflects the raw recomputed set (1 customer ⇒ `activeDays: 1`), so change that one assertion from `4` to `1` (the RAW day-count is cosmetic; qualification rides `observedDayKeys.length` = 4).
2. `withholds the payout for a flagged referral but still advances it` — add `flags: ['self_referral']` to the second `seed()` arg alongside `priorNights(...)`; assertions unchanged.
3. `pays out a referral flagged only with the advisory missing_device_hash` — add `flags: ['missing_device_hash']`; assertions unchanged.
4. `counts a measurement as a meaningful write toward a distinct day` — put the tipping write on a measurement instead of a customer: seed `'users/u1/customers/c0/measurements/mm1': { createdAt: SIGNUP + 4.5 * DAY_MS }` plus a customer for activation (`'users/u1/customers/c0': { createdAt: SIGNUP + 0.5 * DAY_MS }`), with `priorNights(['2026-07-01','2026-07-02','2026-07-03'], '2026-07-05')`; assert `qualified: 1`.
5. `is idempotent — a second run makes no further change` — this runs the handler twice. After migration the FIRST call qualifies (as in test 1); assert the second call returns `{ scanned: 1, activated: 0, qualified: 0 }` and leaves `observedDayKeys` / milestone unchanged. Seed as in test 1.
6. The grace-period qualification test (`credits final-window-day activity within the reconcile grace`, already in the plan's new tests) uses `lastObservedRunDateKey: '2026-07-14'` — verify it still passes as written; it already models prior nights correctly.

**Add one new test** proving genuine multi-night accumulation from zero (the honest-user property, at the handler level):

```typescript
  it('accrues one observed day per nightly run until an honest user qualifies', async () => {
    const { store, db } = seed(); // no activity yet
    // Four nightly runs; each night the tailor adds a customer that day, and the
    // NEXT run (day now complete) credits it. Day-keys 07-01..07-04.
    const nights = [
      { activityMs: SIGNUP + 0.5 * DAY_MS, runMs: SIGNUP + 1.1 * DAY_MS },
      { activityMs: SIGNUP + 1.5 * DAY_MS, runMs: SIGNUP + 2.1 * DAY_MS },
      { activityMs: SIGNUP + 2.5 * DAY_MS, runMs: SIGNUP + 3.1 * DAY_MS },
      { activityMs: SIGNUP + 3.5 * DAY_MS, runMs: SIGNUP + 4.1 * DAY_MS },
    ];
    let qualified = 0;
    for (let i = 0; i < nights.length; i += 1) {
      store.set(`users/u1/customers/c${i}`, { createdAt: nights[i].activityMs });
      const res = await reconcileReferralsHandler(deps(db, nights[i].runMs));
      qualified = res.qualified;
    }
    // Qualifies on the final run, not before — proves no single call short-circuits.
    expect(qualified).toBe(1);
    expect(store.get('referrals/u1').milestone).toBe('qualified');
    expect(store.get('referrals/u1').observedDayKeys.length).toBeGreaterThanOrEqual(4);
  });

  it('does not strand a brand-new referral’s first active day (empty activeDayKeys)', async () => {
    // Regression for the migration-misclassification P1 (codex review). A referral
    // in its real production shape (activeDayKeys: [], no observedDayKeys) active on
    // day 0 must have that day credited on the run that observes it — the migration
    // branch must NOT fire on the empty array and swallow it.
    const { store, db } = seed(); // default fixture now carries activeDayKeys: []
    // Night 1 (day-0 activity present), then night 2 observes the completed day 0.
    store.set('users/u1/customers/c0', { createdAt: SIGNUP + 0.5 * DAY_MS }); // '2026-07-01'
    await reconcileReferralsHandler(deps(db, SIGNUP + 1.1 * DAY_MS)); // run '2026-07-02'
    await reconcileReferralsHandler(deps(db, SIGNUP + 2.1 * DAY_MS)); // run '2026-07-03'
    expect(store.get('referrals/u1').observedDayKeys).toContain('2026-07-01');
  });
```

If any exact day-key does not line up (Lagos offset pushes a boundary), run the focused suite and adjust the `SIGNUP + N.5 * DAY_MS` offsets until the intended day-keys result — do NOT change the ratchet or the floor to force a pass.

- [ ] **Step 4.6: Fix the measurement-scan gate for ratcheted eligibility (codex P2)**

`gatherSignals` (`reconcileReferrals.ts:283`) skips the per-customer measurement subcollection reads once customers/orders alone yield `QUALIFY_DISTINCT_DAYS` RAW distinct days (`if (dayKeys.length < QUALIFY_DISTINCT_DAYS)`, ~line 307). That was correct before the ratchet — 4 raw days *meant* qualified. Now qualification rides the ratcheted `observedDayKeys`, so 4 raw customer/order days that are already-observed, backdated below the floor, or future-dated no longer guarantee qualification — yet they still suppress the measurement scan. If the genuine 4th eligible day is a measurement-only day, the ratchet never sees it and a legitimate referral silently fails to qualify (fails safe — under-credits, never over-credits).

**Fix: gate the measurement scan on the already-OBSERVED count, not the raw count.** Measurements can only add days to the raw set; the only time they truly can't change the outcome is when the referral is already qualified (`observedDayKeys.length >= QUALIFY_DISTINCT_DAYS`). A not-yet-qualified referral must always scan, because a measurement might supply the newly-eligible day.

**4.6a.** Add an `alreadyObservedCount` parameter to `gatherSignals`:

```typescript
async function gatherSignals(
  db: admin.firestore.Firestore,
  uid: string,
  signupMs: number,
  windowEndMs: number,
  alreadyObservedCount: number,
): Promise<CandidateSignals> {
```

**4.6b.** Change the measurement-scan gate from the raw count to the observed count:

```typescript
  // Only pay for the (potentially many) per-customer measurement reads if the
  // referral is not already qualified. Post-ratchet the RAW day-count no longer
  // implies qualification (raw days may be already-observed / below-floor /
  // future-dated), so a not-yet-qualified referral must scan measurements — a
  // measurement-only day could be its genuine newly-eligible day. A qualified
  // referral (observed >= bar) can gain nothing, so it still skips.
  if (alreadyObservedCount < QUALIFY_DISTINCT_DAYS) {
```

**4.6c.** At the call site (`reconcileReferrals.ts:358`), pass the non-transactional observed count from the outer query snapshot (`data` is the doc read before the transaction; its `observedDayKeys` is a safe hint — the optimization does not need transactional accuracy, and it fails toward scanning-more, never toward skipping):

```typescript
    const signals = await gatherSignals(
      db, uid, windowStartMs, windowEndMs, (data.observedDayKeys?.length ?? 0),
    );
```

**4.6d.** Add a handler test proving a measurement supplies the qualifying 4th day even when customers/orders already have 4 raw (but ineligible) days:

```typescript
  it('scans measurements for the qualifying day even when raw customer days already number four', async () => {
    // observed=3 (days 07-01..07-03). Four customer raw days exist but are all
    // ineligible this run: three are already observed, one is future-dated. The
    // genuine 4th eligible day (07-04, completed, in [floor, run)) is a
    // MEASUREMENT-only day. The raw-count optimization would have skipped it.
    const { store, db } = seed(
      {
        'users/u1/customers/c0': { createdAt: SIGNUP + 0.5 * DAY_MS }, // '2026-07-01' (observed)
        'users/u1/customers/c1': { createdAt: SIGNUP + 1.5 * DAY_MS }, // '2026-07-02' (observed)
        'users/u1/customers/c2': { createdAt: SIGNUP + 2.5 * DAY_MS }, // '2026-07-03' (observed)
        'users/u1/customers/c3': { createdAt: SIGNUP + 8.5 * DAY_MS }, // '2026-07-09' future vs run '2026-07-06'
        'users/u1/customers/c0/measurements/mm1': { createdAt: SIGNUP + 3.5 * DAY_MS }, // '2026-07-04' — the real 4th day
      },
      priorNights(['2026-07-01', '2026-07-02', '2026-07-03'], '2026-07-04'),
    );
    // Run '2026-07-06' (NOW): floor = lastObservedRunDateKey '2026-07-04'; '2026-07-04'
    // measurement day is completed and >= floor → credited → observed reaches 4.
    const res = await reconcileReferralsHandler(deps(db));
    expect(res.qualified).toBe(1);
    expect(store.get('referrals/u1').milestone).toBe('qualified');
    expect(store.get('referrals/u1').observedDayKeys).toContain('2026-07-04');
  });
```

Run the focused suite and confirm it FAILS against the raw-count gate (measurement skipped → observed stays 3 → not qualified) and PASSES after the gate change. Adjust `SIGNUP + N.5 * DAY_MS` offsets if a Lagos boundary shifts a key; do not change the ratchet.

- [ ] **Step 5: Run the full referral test suite**

```bash
cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts
```

Expected: PASS — the 5 new ratchet-wiring tests, the 6 migrated tests, the new accumulation test, and every untouched test.

- [ ] **Step 6: Run the whole functions suite + lint**

```bash
cd functions && npm run lint && npm test
```

Expected: both exit 0. Other referral tests (`recordAttribution`, `confirmReferralPayouts`) must be unaffected.

- [ ] **Step 7: Commit**

```bash
git add functions/src/referral/reconcileReferrals.ts functions/src/referral/referralConstants.ts functions/src/__tests__/referral/reconcileReferrals.test.ts
git commit -m "feat(referral): qualify on server-observed days, not client createdAt

Qualification now reads the ratcheted observedDayKeys instead of the raw
recomputed set, so future-dated createdAt writes buy at most one credited day
per nightly run. Adds the advisory future_dated_activity flag. In-flight
referrals seed observedDayKeys from activeDayKeys so they keep accrued days.

Migrates six pre-existing handler tests that assumed a single call credits
multiple already-elapsed days (impossible under the ratchet's first-run floor)
to seed prior-night observed state, and adds a multi-run accumulation test
proving an honest user still qualifies over four nightly runs."
```

---

### Task 3: Deploy and verify against live data

**Files:** none — this task is operational.

**Interfaces:**
- Consumes: the deployed `reconcileReferrals` / `debugReconcileReferrals` functions.

- [ ] **Step 1: Open the PR**

```bash
git push -u origin fix/referral-active-day-ratchet
gh pr create --title "fix(referral): server-observed active-day ratchet (Lane A)" --body "See docs/superpowers/specs/2026-07-20-referral-active-day-ratchet-design.md"
```

Per `feedback_review_rotation`, a non-trivial PR needs **both** Cursor Bugbot and `codex review` before merge. Per `feedback_codex_review_model`, invoke codex as `codex review -c model=gpt-5.5`.

- [ ] **Step 2: Deploy after merge (Daniel runs — the harness blocks production deploys)**

```bash
cd functions && npm run deploy
```

Both `reconcileReferrals` and `debugReconcileReferrals` are already in the `deploy --only` allow-list in `functions/package.json`. Verify that is still true before deploying (`feedback_functions_deploy_allowlist`).

- [ ] **Step 3: Verify against a seeded account**

Use the in-app debug menu's "Run grader" action against a seeded test account, then inspect `referrals/{uid}` in the Firestore console:

- `observedDayKeys` exists and is a sorted `'YYYY-MM-DD'` array.
- `lastObservedRunDateKey` equals today's Lagos date.
- For a referral that was in flight before the deploy: `observedDayKeys` matches its prior `activeDayKeys` — **not** empty. This is the regression that would silently cost a legitimate marketer payout.

- [ ] **Step 4: Confirm no legitimate referral regressed**

Query `referrals` where `milestone == 'attributed'` or `'activated'` and confirm every doc has `observedDayKeys.length >= 1` wherever `activeDays >= 1`. Any doc with `activeDays >= 1` but `observedDayKeys == []` means the migration seed did not fire — investigate before the next nightly run.

- [ ] **Step 5: Update the memory backlog**

Mark `project_referral_activity_timestamp_lane_b` as "Lane A shipped <date>; Lane B still open" so the sequencing note stays accurate — the ratchet must **remain** in place after Lane B ships, because old binaries stay forgeable.

---

## Known bound (accepted, not a gap)

On a referral's very first grader run (`lastRunDateKey` absent), the floor is clamped to `max(windowStartDateKey, prevDayKey(runDateKey))` — yesterday — so a first run credits at most the single completed day it could legitimately have observed. **Accepted cost:** if the grader is down for several nights spanning a *new* referral's first run, the completed days before yesterday are lost permanently (the user can still earn fresh days inside the remaining 14-day window). This is the deliberate fraud-favoring trade over the original attribution-date floor, which would have let a dormant in-flight referral credit an entire backdated burst in one run. Every payout still passes the 7-day hold and the manual `markReferralPaid` gate regardless.

**Revision note (2026-07-22):** the attribution-date floor in the first draft of this plan was a live bypass — the Task 1 review traced that the grader skips the write when nothing changed (`reconcileReferrals.ts:352-359`), so a dormant referral reaches this branch with no prior state and the attribution floor credits every backdated day at once. Fixed to the yesterday-clamp above.

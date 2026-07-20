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
| first run: `lastRunDateKey ?? windowStartDateKey` | No prior run; the attribution date is the earliest defensible floor. |
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
  if (observedKeys === undefined && priorActiveKeys !== undefined) {
    return {
      observedDayKeys: Array.from(new Set(priorActiveKeys)).sort(),
      newlyCredited: [],
      futureDated,
    };
  }

  const observed = new Set(observedKeys ?? []);
  // No prior run means no run has yet had a chance to observe anything; the
  // attribution date is the earliest defensible floor.
  const floor = lastRunDateKey ?? windowStartDateKey;

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
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts -t ratchetObservedDayKeys
```

Expected: PASS, 9 tests.

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

Expected: FAIL. The new assertions fail because `observedDayKeys` is never written and `future_dated_activity` is never set. **Pre-existing tests in this block must still pass** — if one breaks, stop and re-read the failure before changing it; the ratchet is designed not to alter honest-user outcomes.

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

- [ ] **Step 5: Run the full referral test suite**

```bash
cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts
```

Expected: PASS, all tests including the pre-existing ones.

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
referrals seed observedDayKeys from activeDayKeys so they keep accrued days."
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

On a referral's very first grader run, `lastRunDateKey` is absent and the floor becomes the attribution date, so every completed day since attribution is creditable at once. Under the nightly cadence a referral is first graded within ~24h of attribution, so at most one day is available — but a multi-day grader outage spanning a referral's first run would widen that. This is the spec's accepted bound: payouts still pass the 7-day hold and the manual `markReferralPaid` gate.

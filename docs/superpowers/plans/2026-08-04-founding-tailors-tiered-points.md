# Founding Tailors Tiered Points Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change Founding Tailors scoring from binary (1 point per `qualified` referral) to tiered — +1 activation point plus +1 per active day (max 4), 5 points max per referral, each point counted in the Lagos month it was earned.

**Architecture:** Aggregator-only change. `aggregateFoundingTailorsLeaderboardHandler` is rewritten to award tiered points from each referral's existing `observedDayKeys` (the ratcheted list of distinct active Lagos days). `reconcileReferrals` (the grader) is untouched — no new Firestore field, no migration. Plus copy updates to the "How points work" explainer on web + app.

**Tech Stack:** TypeScript + firebase-functions v1 + firebase-admin (Jest tests, inline fake Firestore); Astro (web copy); Kotlin Multiplatform Compose + compose.resources (app copy).

## Global Constraints

- **Firebase region:** `europe-west1` (unchanged; the function already sets it).
- **"Point" gating (do not reinvent):** a referral earns 0 points if it carries any BLOCKING flag — use `hasBlockingFlag(flags)` from `referralConstants.ts` (blocking = `self_referral`, `device_reuse`, `velocity`).
- **Day cap:** `QUALIFY_DISTINCT_DAYS` (= 4) from `referralConstants.ts` — max 4 day-points, so max 5 points per referral.
- **`observedDayKeys` are `YYYY-MM-DD` Africa/Lagos date-keys** already (written by the grader); a key's month is `key.slice(0, 7)` — no timezone conversion.
- **Zero program marketers still writes empty boards** (current behavior, enforced by an existing test) — do NOT add an early return.
- **Community-facing copy:** no em dashes; app strings use `&apos;` not `\'` (CMP iOS renders backslash-escapes literally).
- **Deploy gate:** `aggregateFoundingTailorsLeaderboard` is already in `functions/index.ts` exports and the `package.json` deploy allow-list. Run `npm run lint` locally (CI lints before testing).

---

## Task 1: Rewrite the aggregator to tiered points

**Files:**
- Modify: `functions/src/referral/foundingTailorsLeaderboard.ts` (imports line 12-13; handler lines 32-72; top comment lines 1-8)
- Test: `functions/src/__tests__/referral/foundingTailorsLeaderboard.test.ts` (replace the aggregator tests, lines ~85-181; keep the fake db lines 1-81 and the read-callable tests lines 183-275 untouched)
- Modify: `functions/scripts/foundingTailorsSmoke.js` (seed `observedDayKeys` + assert tiered totals)

**Interfaces:**
- Consumes: `hasBlockingFlag`, `QUALIFY_DISTINCT_DAYS`, `MARKETERS`, `REFERRALS`, `ACTIVE_DAY_TIMEZONE`, `REGION`, `FOUNDING_TAILORS_PROGRAM`; each `referrals/{uid}` doc's `marketerId`, `flags`, `observedDayKeys`, `milestone`; each program `marketers/{id}` doc's `name`, `program`.
- Produces: unchanged public docs `leaderboards/{monthId}` = `{ monthId, updatedAt, entries: [{ marketerId, name, points }] }` (points-desc, name tiebreak), `leaderboards/current` = `{ monthId, updatedAt }`, `leaderboards/alltime` = `{ updatedAt, entries }`. `points` is now a tiered sum. `monthKeyLagos`, `sortEntries`, `LeaderEntry`, `AggregatorDeps` keep their current signatures. The read callable (`getFoundingTailorsLeaderboardHandler`) is NOT changed.

- [ ] **Step 1: Replace the aggregator tests with the tiered set**

In `foundingTailorsLeaderboard.test.ts`, replace everything from the first `test('counts only qualified...` (line ~85) through the end of the `test('writes the alltime board...` (line ~181) — i.e. only the aggregator tests — with the block below. Do NOT touch the fake-db helper above it or the read-callable tests below it.

```ts
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
    // mA: qualified with 4 days spanning Jul->Aug → 5 points total (2 in Jul, 3 in Aug).
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts`
Expected: the new tiered tests FAIL (the current handler counts 1 per qualified referral by `qualifiedAt`, so e.g. the "5 points" test gets 1 and the "activation + day-1 = 2" test gets 0 because the fixtures no longer set `qualifiedAt`). The read-callable tests still PASS.

- [ ] **Step 3: Rewrite the handler imports**

In `foundingTailorsLeaderboard.ts`, change the two import lines (12-13) to add `QUALIFY_DISTINCT_DAYS` and drop the now-unused `ReferralMilestone`:

```ts
import { REGION, MARKETERS, REFERRALS, REFERRAL_CODES, ACTIVE_DAY_TIMEZONE, QUALIFY_DISTINCT_DAYS, hasBlockingFlag } from './referralConstants';
import type { ReferralFlag } from './referralConstants';
```

- [ ] **Step 4: Rewrite the handler body**

Replace the whole `aggregateFoundingTailorsLeaderboardHandler` function (lines ~32-72) with the version below. Add the small `monthOfDayKey` helper just above it (next to `sortEntries`).

```ts
/** The Africa/Lagos calendar month (YYYY-MM) of a 'YYYY-MM-DD' Lagos day-key. */
function monthOfDayKey(dayKey: string): string {
  return dayKey.slice(0, 7);
}

export async function aggregateFoundingTailorsLeaderboardHandler(deps: AggregatorDeps): Promise<void> {
  // 1. Program user-referrers → id -> display name.
  const marketersSnap = await deps.db.collection(MARKETERS).where('program', '==', FOUNDING_TAILORS_PROGRAM).get();
  const names = new Map<string, string>();
  marketersSnap.forEach((d) => names.set(d.id, (d.data().name as string) ?? 'Tailor'));

  // 2. Scan activated + qualified referrals; award TIERED points, each bucketed
  //    into the Lagos month it was earned in. A referral is worth up to 5 points:
  //    +1 activation (at the first active day's month) and +1 per active day
  //    (capped at QUALIFY_DISTINCT_DAYS). Blocking-flagged referrals earn 0.
  //    observedDayKeys are ratcheted 'YYYY-MM-DD' Lagos keys from the grader.
  const referralsSnap = await deps.db.collection(REFERRALS).where('milestone', 'in', ['activated', 'qualified']).get();
  const byMonth = new Map<string, Map<string, LeaderEntry>>();
  const allTime = new Map<string, LeaderEntry>();
  const bump = (map: Map<string, LeaderEntry>, id: string) => {
    const e = map.get(id) ?? { marketerId: id, name: names.get(id) as string, points: 0 };
    e.points += 1; map.set(id, e);
  };
  const award = (monthKey: string, id: string) => {
    if (!byMonth.has(monthKey)) byMonth.set(monthKey, new Map());
    bump(byMonth.get(monthKey) as Map<string, LeaderEntry>, id);
    bump(allTime, id);
  };

  referralsSnap.forEach((d) => {
    const r = d.data() as { marketerId: string; flags?: ReferralFlag[]; observedDayKeys?: string[] };
    if (!names.has(r.marketerId)) return;                 // not a program referrer (e.g. affiliate)
    if (hasBlockingFlag(r.flags)) return;                 // fraud-flagged → 0 points
    const days = [...(r.observedDayKeys ?? [])].sort().slice(0, QUALIFY_DISTINCT_DAYS);
    if (days.length === 0) return;                        // no creditable active day yet → no anchor
    award(monthOfDayKey(days[0]), r.marketerId);          // +1 activation, at the first active day's month
    for (const day of days) award(monthOfDayKey(day), r.marketerId); // +1 per active day (already capped)
  });

  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const currentMonth = monthKeyLagos(deps.now().getTime());
  const batch = deps.db.batch();
  for (const [mk, map] of byMonth) {
    batch.set(deps.db.doc(`leaderboards/${mk}`), { monthId: mk, updatedAt: nowTs, entries: sortEntries(map) });
  }
  // Ensure the current month doc exists even with zero points (page renders an empty board, not an error).
  if (!byMonth.has(currentMonth)) {
    batch.set(deps.db.doc(`leaderboards/${currentMonth}`), { monthId: currentMonth, updatedAt: nowTs, entries: [] });
  }
  batch.set(deps.db.doc('leaderboards/current'), { monthId: currentMonth, updatedAt: nowTs });
  batch.set(deps.db.doc('leaderboards/alltime'), { updatedAt: nowTs, entries: sortEntries(allTime) });
  await batch.commit();
}
```

- [ ] **Step 5: Update the file's top comment (lines 1-8)**

Replace the top comment block describing the old behavior with:

```ts
// Founding Tailors leaderboard — daily aggregator.
//
// Awards TIERED points to program (`founding_tailors`) user-referrers from each
// referral's `observedDayKeys` (the grader's ratcheted distinct active Lagos
// days): +1 activation point at the first active day's month, plus +1 per active
// day capped at QUALIFY_DISTINCT_DAYS — 5 points max. Blocking-flagged referrals
// earn 0. Each point is bucketed into the Lagos month it was earned. Writes the
// public `leaderboards/*` docs the app + web read directly. The read-side callable
// lives below in this same file.
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts`
Expected: PASS (all tiered aggregator tests + the unchanged read-callable tests).

- [ ] **Step 7: Update the emulator smoke script**

In `functions/scripts/foundingTailorsSmoke.js`, the seeded referrals currently set `milestone: 'qualified'` + `qualifiedAt` but no `observedDayKeys`, so under the tiered aggregator they would score 0. Update the three seeded referral writes in the `[3]` section to add `observedDayKeys` and update the assertions:

- `referrals/ref_clean_1`: add `observedDayKeys: ['2026-08-01', '2026-08-02', '2026-08-03', '2026-08-04']` (a fully qualified referral → 5 points).
- `referrals/ref_clean_2`: change to `milestone: 'activated'` with `observedDayKeys: ['2026-08-02']` (activation + 1 day → 2 points).
- `referrals/ref_blocked`: keep `flags: ['self_referral']`, add `observedDayKeys: ['2026-08-03', '2026-08-04', '2026-08-05', '2026-08-06']` (still 0 points).
- `referrals/ref_affiliate`: add `observedDayKeys: ['2026-08-03']` (still excluded).

Then change the `[3]` assertions from `points === 2` to the tiered totals: the referrer's board points = 5 (ref_clean_1) + 2 (ref_clean_2) = **7**, all in `2026-08`. Update the `[4]` read-callable assertions to expect `points: 7` and `you: { rank: 1, points: 7 }`.

- [ ] **Step 8: Lint + typecheck + full referral suite + commit**

```bash
cd functions && npm run lint && npx tsc --noEmit && npx jest src/__tests__/referral
git add src/referral/foundingTailorsLeaderboard.ts src/__tests__/referral/foundingTailorsLeaderboard.test.ts scripts/foundingTailorsSmoke.js
git commit -m "feat(founding-tailors): tiered leaderboard points (activation + per-day)"
```

Expected: lint clean, tsc clean, all referral suites green.

---

## Task 2: Update the web "How points work" copy

**Files:**
- Modify: `~/Desktop/Business/StitchPad/StitchPad-IT/stitchpad-web/src/pages/founding-tailors.astro` (the "How points work" `<ul>`)

This is in the separate `stitchpad-web` repo. There is no unit test; the gate is `npm run build` + Prettier.

- [ ] **Step 1: Pull main and branch**

```bash
cd ~/Desktop/Business/StitchPad/StitchPad-IT/stitchpad-web
git checkout main && git pull --ff-only origin main
git checkout -b feat/founding-tailors-tiered-copy
```

- [ ] **Step 2: Replace the 4 bullet `<li>`s with the 5 tiered bullets**

In the `<ul class="text-ink-muted mt-5 flex flex-col gap-3 ...">` block of `founding-tailors.astro`, replace the four existing `<li>` items with these five (keep the same `<li class="flex gap-3">` / bullet-span structure; Prettier will reflow):

```astro
          <li class="flex gap-3">
            <span class="text-indigo-500" aria-hidden="true">&bull;</span>
            <span>You earn <strong class="text-ink font-semibold">up to 5 points</strong> for every tailor you invite.</span>
          </li>
          <li class="flex gap-3">
            <span class="text-indigo-500" aria-hidden="true">&bull;</span>
            <span><strong class="text-ink font-semibold">1 point</strong> when they set up their workshop and add their first customer or order.</span>
          </li>
          <li class="flex gap-3">
            <span class="text-indigo-500" aria-hidden="true">&bull;</span>
            <span><strong class="text-ink font-semibold">1 more point</strong> for each day they use the app, up to 4 days, in their first 2 weeks.</span>
          </li>
          <li class="flex gap-3">
            <span class="text-indigo-500" aria-hidden="true">&bull;</span>
            <span>Installs and signups alone do not count. We reward real, working tailors.</span>
          </li>
          <li class="flex gap-3">
            <span class="text-indigo-500" aria-hidden="true">&bull;</span>
            <span>Each point counts in the month it was earned. The top 3 each month win a free customized StitchPad shirt, and points bank toward free Pro months.</span>
          </li>
```

- [ ] **Step 3: Format + build**

```bash
npm run format && npm run build
```
Expected: Prettier writes, build completes (14 pages).

- [ ] **Step 4: Commit + push + PR**

```bash
git add src/pages/founding-tailors.astro
git commit -m "feat(founding-tailors): explainer copy for tiered points"
git push -u origin feat/founding-tailors-tiered-copy
gh pr create --title "feat(founding-tailors): tiered points explainer copy" --body "Updates the /founding-tailors How points work section to the tiered model (1 activation point + 1 per active day, 5 max, counted in the month earned). Copy only."
```
(The merge is done by the user — deploy is gated. Vercel auto-deploys main on merge.)

---

## Task 3: Update the app "How points work" copy

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (the `founding_tailors_how_*` strings; add `founding_tailors_how_point5`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsScreen.kt` (add `point5` to the bullet list + its import)

No unit test (static copy); gate is `assembleDebug` + `detekt`.

- [ ] **Step 1: Update the strings**

In `strings.xml`, replace the four existing `founding_tailors_how_point1..4` with these, and add `point5` (no em dashes; no `\'`):

```xml
    <string name="founding_tailors_how_point1">You earn up to 5 points for every tailor you invite.</string>
    <string name="founding_tailors_how_point2">1 point when they set up their workshop and add their first customer or order.</string>
    <string name="founding_tailors_how_point3">1 more point for each day they use the app, up to 4 days, in their first 2 weeks.</string>
    <string name="founding_tailors_how_point4">Installs and signups alone do not count. We reward real, working tailors.</string>
    <string name="founding_tailors_how_point5">Each point counts in the month it was earned. The top 3 each month win a free customized StitchPad shirt, and points bank toward free Pro months.</string>
```

- [ ] **Step 2: Add point5 to the screen**

In `FoundingTailorsScreen.kt`, add the import `import stitchpad.composeapp.generated.resources.founding_tailors_how_point5` (in `founding_tailors_how_*` alphabetical position, after `founding_tailors_how_point4`), and add `Res.string.founding_tailors_how_point5,` to the `listOf(...)` inside the "How points work" section (after `founding_tailors_how_point4`).

- [ ] **Step 3: Build + detekt**

```bash
./gradlew :composeApp:assembleDebug detekt
```
Expected: BUILD SUCCESSFUL, detekt clean. (Optional: `:composeApp:installDebug` to eyeball the 5 bullets on the device.)

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsScreen.kt
git commit -m "feat(founding-tailors): app explainer copy for tiered points"
```

---

## Deploy & rollout order

1. **Redeploy the aggregator only** (reconcile is unchanged): `cd functions && npm run lint && firebase deploy --only functions:aggregateFoundingTailorsLeaderboard --project stitchpad-30607`.
2. **Trigger one aggregation** so `leaderboards/*` reflect the tiered math immediately (a one-off admin invocation of `aggregateFoundingTailorsLeaderboardHandler` against prod, or wait for the 04:00 Lagos schedule). No migration — it recomputes the whole board from current referral state.
3. **Web copy:** user merges the Task 2 PR → Vercel deploys.
4. **App copy:** rides with the Founding Tailors app PR (#338) through the store pipeline.

## Self-review notes (coverage vs spec)

- Point model (+1 activation, +1/day cap 4, 5 max) → Task 1 Step 4 + tests (5-point, 2-point, cap-at-5).
- Per-event month bucketing + boundary split → Task 1 "points split across a month boundary" test.
- Anti-gaming (blocking flags → 0) → Task 1 "blocking flag withholds ALL points" test; activation needs `activated` milestone → "attributed-only earns 0" + "no observed days earns 0" tests.
- Aggregator-only, reconcile untouched, no new field → Task 1 reuses `observedDayKeys`; no reconcile file in scope.
- Zero-marketers still writes empty boards → kept test + no early return (Global Constraints).
- Copy updates → Tasks 2 (web) + 3 (app).
- Read callable + leaderboard doc shape unchanged → not modified; existing read-callable tests kept green in Task 1.

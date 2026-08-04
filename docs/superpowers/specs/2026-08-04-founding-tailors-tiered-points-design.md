# Founding Tailors — Tiered Points Design

**Date:** 2026-08-04
**Status:** Approved (brainstorming), pending implementation plan
**Supersedes:** the binary scoring in the original Founding Tailors leaderboard (1 point per `qualified` referral).

## Goal

Replace the binary Founding Tailors score (1 point only when a referral fully qualifies) with a **tiered, incrementally-accruing** score, so referrers see progress and get rewarded earlier for referrals that start using the app — without opening a meaningful new fraud surface.

## Point model

A single referral is worth **at most 5 points**:

- **+1 activation point** — awarded once the referral reaches the existing `activated` milestone (business name set AND at least one real customer or order).
- **+1 per active day, capped at 4** — one point for each distinct Africa/Lagos day the referred tailor creates real work (customer / order / measurement) in the app, within the 14-day qualification window. This reuses the grader's existing distinct-day tracking.

Because `activated` requires the first customer/order, the activation event coincides with the referral's **first** active day. So the progression across a genuine 4-day path is **2 → 3 → 4 → 5** points (day 1 lands the activation point + the day-1 point together), and a **fully `qualified` referral (4 distinct days) totals exactly 5 points.**

### Anti-gaming

- A referral carrying any **blocking** flag (`self_referral`, `device_reuse`, `velocity`) earns **0 points** — activation and all day-points withheld. This is the same exclusion the current aggregator applies (`hasBlockingFlag`).
- 4 of the 5 points still require genuine multi-day usage gated by the nightly ratchet (one distinct day credited per grader run), so the bulk of a marketer's score is unfarmable. The activation point additionally requires a real first customer/order (not just an install or a typed business name), so it cannot be farmed by bare signups.

## Monthly counting

The competition is monthly (top 3 each Lagos month win a shirt). **Each point counts in the Lagos month it was earned:**

- the activation point → the month of the referral's **first active day**;
- each day-point → the month of **that** day.

A referral whose 14-day window straddles a month boundary therefore splits its points across the two months' boards. This is intended ("points you earned this month").

## Architecture

**Only the aggregator changes.** `reconcileReferrals` (the nightly grader) is **unchanged** — it already stamps `qualifiedAt` and ratchets `observedDayKeys`. No new Firestore field, no migration, no reconcile edit.

### Why no new timestamp

`observedDayKeys` on `referrals/{uid}` is the ratcheted, monotonic list of distinct in-window active Lagos days, stored as `YYYY-MM-DD` Lagos date-keys (see `lagosDateKey`). This already carries everything the tiered aggregator needs:

- **Day-points:** the first `QUALIFY_DISTINCT_DAYS` (4) sorted `observedDayKeys`; each key's month is `key.slice(0, 7)` (already a Lagos key — no timezone conversion needed).
- **Activation-point month:** the month of `observedDayKeys[0]` (the earliest active day), which is when activation actually happened.

`qualifiedAt` (still stamped by reconcile) is no longer read by the aggregator; it is left in place, harmless.

### The new aggregator (`aggregateFoundingTailorsLeaderboard`)

Replaces the current "count 1 per qualified referral, bucket by `qualifiedAt` month" logic.

1. Load program marketers (`marketers where program == 'founding_tailors'`) → `id → name`. If none, return early (unchanged).
2. Scan referrals with `where('milestone', 'in', ['activated', 'qualified'])` — **note the change** from the current `== 'qualified'`, so activated-but-not-yet-qualified referrals now contribute.
3. For each referral whose `marketerId` is a program marketer AND `!hasBlockingFlag(flags)`:
   - `days = [...observedDayKeys].sort().slice(0, QUALIFY_DISTINCT_DAYS)`
   - if `days.length === 0` → skip (no creditable day yet; activation has no month anchor).
   - **activation point:** `+1` for `marketerId` in month `days[0].slice(0, 7)` (milestone is already `activated` or `qualified` by the scan filter).
   - **day-points:** for each `day` in `days`, `+1` for `marketerId` in month `day.slice(0, 7)`.
4. Aggregate per marketer **per month** → `leaderboards/{monthId}.entries` (sorted points-desc, then name); also maintain `leaderboards/current` (`{ monthId }` for the current Lagos month) and `leaderboards/alltime` (sum of every point ever). Ensure the current-month doc exists even when empty (unchanged behavior).

Per-referral point total is inherently capped at 5 (1 activation + ≤4 days).

### Unchanged pieces

- **`reconcileReferrals`** — no change.
- **`getFoundingTailorsLeaderboard`** (public read callable) — no change. It still returns `{ updatedAt, monthId, top: [{rank,name,points}], you: {rank,points}|null }`; `points` is now the tiered sum for the month. `top` rows still carry no marketerIds.
- **Leaderboard doc shape** — `leaderboards/{monthId} = { monthId, updatedAt, entries: [{marketerId, name, points}] }` — unchanged shape; `points` is now a tiered sum.
- **App/web leaderboard rendering** — unchanged (just larger point numbers).

## Copy / UI updates

Update the "How points work" explainer in both places to describe the tiered model (no em dashes):

- Web: `stitchpad-web` `src/pages/founding-tailors.astro` "How points work" section.
- App: `FoundingTailorsScreen.kt` — the `founding_tailors_how_*` string resources.

Draft copy:
- You earn up to 5 points for every tailor you invite.
- 1 point when they set up their workshop and add their first customer or order.
- 1 more point for each day they actively use the app, up to 4 days, in their first 2 weeks.
- Installs and signups alone earn nothing, and points only count for real, active tailors.
- Each point counts in the month it was earned. The top 3 each month win a free customized StitchPad shirt, and points bank toward free Pro months.

## Edge cases

- **Activated, but no `observedDayKeys` yet** (activity today, not a completed day): 0 points until the next grader run credits the first day, then activation + day-1 appear together. ~1-day lag, acceptable.
- **More than 4 active days:** day-points capped at the first 4; extra days earn nothing (total stays 5).
- **Referral later flagged:** the daily aggregator recomputes from current state, so a newly-flagged referral drops to 0 points on the next run.
- **No program marketers / empty month:** unchanged — early return / current-month empty board still rendered.

## Testing

- Unit tests (`functions/src/__tests__/referral/foundingTailorsLeaderboard.test.ts`):
  - activated + 1 active day → 2 points (activation + day-1) in that day's month.
  - qualified with 4 active days in one month → 5 points that month.
  - 4 active days split across a month boundary → points split correctly across two months' boards; activation counts in the first active day's month.
  - blocking flag → 0 points (both tiers withheld).
  - affiliate (non-program) marketer excluded.
  - >4 active days → capped at 5.
  - `attributed`-only referral → 0 points (not in scan set).
- Keep the direct-handler emulator smoke script (`functions/scripts/foundingTailorsSmoke.js`) updated to assert tiered totals.

## Deploy / rollout

1. Update + unit-test the aggregator; lint.
2. Redeploy `aggregateFoundingTailorsLeaderboard` (already in the deploy allow-list). No reconcile redeploy needed.
3. Run one aggregation so `leaderboards/current` reflects the new math (or wait for the 04:00 Lagos schedule).
4. Ship the web + app copy updates (web via `stitchpad-web` PR; app copy rides with the Founding Tailors app PR #338).
5. No data migration — the program is new and the aggregator recomputes the whole board each run.

## Non-goals

- No dedicated `activatedAt` timestamp (rejected for a ~1-day gratification gain that isn't worth a reconcile change + month-skew handling).
- No change to the qualification definition, the 14-day window, the ratchet, or the fraud flags.
- The deferred in-app "progress view" (per-referral invited → set up → earning) remains a separate future item; it needs a new read surface exposing a referrer's own per-referral milestones.

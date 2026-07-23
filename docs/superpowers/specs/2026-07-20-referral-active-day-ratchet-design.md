# Referral active-day ratchet — design

Date: 2026-07-20
Status: approved, pending implementation plan
Lane: **A** (Cloud Functions only — deploys against the live 1.1.x binaries, no store review)

## Problem

`reconcileReferrals` qualifies a referred user for a marketer payout after writes on
`QUALIFY_DISTINCT_DAYS` (4) distinct Africa/Lagos calendar days inside the qualification
window. The day-keys come from `createdAt` on `customers` / `orders` / `measurements`,
which is a **client-set epoch-millis number** — the app sets it so offline-first writes
carry a sensible creation date, and `firestore.rules` does not constrain it.

`computeActiveDayKeys` (`functions/src/referral/reconcileReferrals.ts:61`) bounds keys by
the qualification window but **never rejects future-dated timestamps**. So the attack is
not "spread writes across days you were genuinely active" as the code comment claims:

> A colluding referred account writes 4 customers in a single session with
> `createdAt` set to attribution +0d/+1d/+2d/+3d, and qualifies **immediately**.

Today this is contained only by the 7-day hold and the manual `markReferralPaid` admin
gate. With a real marketer live (code `H2HYZNEP`) on a per-user cash bounty, this is
billable exposure.

## Non-goals

- Making `createdAt` server-authoritative. That needs a client write + a rules constraint
  (Lane B, next release train) and would not protect users on the live binaries. Tracked
  separately; this design is deliberately complementary to it, not a substitute.
- Any change to `whatsappConfirmed`. Investigated and explicitly cut — see
  "Rejected: whatsappConfirmed" below.

## Approach: credit only server-observed, completed days

The grader runs nightly at 03:30 Africa/Lagos. That cadence is itself an unforgeable
clock: a day-key is only believable if a grader run actually *observed* activity for that
day at a point in time when that day was already over.

Add a monotonically-growing `observedDayKeys` to `referrals/{uid}` and qualify on **its**
length instead of the raw recomputed set.

Per referral, per run, with `runDateKey = lagosDateKey(nowMs)`:

1. `raw = computeActiveDayKeys(...)` — unchanged, still window-bounded.
2. `futureKeys = raw.filter(k => k > runDateKey)` — a correct client can never produce a
   day-key in the future. Non-empty ⇒ record advisory flag `future_dated_activity`.
3. `eligible = raw.filter(k => k < runDateKey)` — **completed days only**. A key equal to
   the run date is for a day still in progress, so it cannot yet be trusted; it becomes
   creditable on the next run.
4. `newly = eligible.filter(k => !observed.has(k) && k >= lastObservedRunDateKey)` — a key
   older than the last run should have been seen by that run. Appearing late means it was
   backdated.
5. `observedDayKeys = sort(union(observedDayKeys, newly))`, persist
   `lastObservedRunDateKey = runDateKey`.
6. `qualifiesByActivity = observedDayKeys.length >= QUALIFY_DISTINCT_DAYS`.

`activeDayKeys` / `activeDays` keep their current meaning (the raw recomputed set) for the
dashboard and audit trail. Qualification switches to `observedDayKeys`.

### Why the comparisons are what they are

Day-keys are `'YYYY-MM-DD'`, so lexicographic `<` / `>=` is chronological.

- **`k < runDateKey`, not `<=`.** The 03:30 run on date *D* is *inside* day *D*. Crediting
  key *D* would credit a day that has barely started, letting a future-dated write land a
  free day each night.
- **`k >= lastObservedRunDateKey`, not `>`.** The run on date *D* sees activity from date
  *D-1*, and the previous run (03:30 on *D-1*) happened *before* that activity. So *D-1*
  legitimately appears one run late and must stay creditable. Using `>` would reject every
  genuine day.

### Effect on the attack

A one-session burst with future-dated timestamps now yields **at most one new credited day
per nightly run**. Reaching 4 days requires the account to survive 4 nights — the same
elapsed time an honest user takes. Forgery stops buying speed, and step 2 flags the burst
for review the moment it appears.

### First observation and in-flight referrals

- **First run for a referral** (`lastObservedRunDateKey` absent): the floor is
  `max(lagosDateKey(windowStartMs), prevDayKey(runDateKey))` — the *later* of the
  attribution date and the day before this run. **Revised from the original
  `lagosDateKey(windowStartMs)`** (Task 1 review, 2026-07-22): the attribution-date floor
  assumed a first run always lands within ~24h of attribution, but the grader **skips the
  write entirely when nothing changed** (`reconcileReferrals.ts:352-359`), so a *dormant*
  in-flight referral — attributed with no activity — persists neither `activeDayKeys` nor
  `observedDayKeys`. The migration branch cannot fire (no `priorActiveKeys`), `lastRunDateKey`
  is absent, and the attribution-date floor would then credit **every** backdated day in the
  window at once: 4 records written in one session across days 1-9 qualify on the next run —
  the exact bypass this design closes, live on ship night for every dormant referral. The
  `prevDayKey` clamp caps a first run to the single day it could legitimately have observed.
  **Accepted cost:** a multi-day grader outage spanning a *new* referral's first run loses
  the pre-yesterday completed days permanently (the user can still earn more inside the
  14-day window). This is strictly safer than the honest-user-favoring alternative and the
  correct trade for fraud-prevention code.
- **Referrals already in flight** when this deploys (`observedDayKeys` absent but
  `activeDayKeys` **non-empty**): seed `observedDayKeys` from `activeDayKeys` and set
  `lastObservedRunDateKey = runDateKey`. Without this, every legitimate in-flight referral
  loses its accrued days and can no longer qualify before its window closes.
  **The migration test must require a NON-EMPTY `activeDayKeys`, not merely a defined one**
  (found in codex review, 2026-07-22): `recordAttribution` initializes every new referral
  with `activeDayKeys: []` (`recordAttribution.ts:192`), so `activeDayKeys !== undefined` is
  true even for brand-new referrals. A `!== undefined`-only test misclassifies a fresh
  referral as a migration, seeds `observedDayKeys` from `[]`, stamps `lastObservedRunDateKey`,
  and permanently strands its signup-day activity below the next floor — an honest user
  active on exactly 4 days loses day 1 and never qualifies. Gate the branch on
  `priorActiveKeys.length > 0`. An empty `activeDayKeys` correctly falls through to the
  normal path (a brand-new or zero-activity referral has no accrued days to preserve).
  **Accepted consequence:** any days already forged by an in-flight referral are
  grandfathered in. This is bounded (a ~14-day window, one known marketer), and every
  payout still passes the 7-day hold plus the manual `markReferralPaid` gate.

### Grace-window interaction

`RECONCILE_GRACE_DAYS = 2` already keeps a referral in the scan set for 2 days past window
close. "Completed days only" delays a final-window-day credit by one run, which stays
inside that grace. This needs an explicit regression test — it is the one place the new
rule could silently cost a legitimate payout.

## Flag semantics

`future_dated_activity` is a new **advisory** flag (joins `missing_device_hash` outside
`BLOCKING_FLAGS` in `referralConstants.ts`). Advisory is correct here: the ratchet has
already denied the credit, so the payout is withheld by arithmetic rather than by
punishment. Making it blocking would let a client-clock bug permanently poison a
legitimate referral.

**Known visibility gap:** with no per-referral admin view, this flag is only readable in
the Firestore console (`referrals/{uid}.flags`). It is recorded now so the evidence exists
when a payout run looks suspicious; surfacing per-referral rows with flags in the admin
dashboard is a follow-up, not part of this work. The flag's *enforcement* value is zero —
all the protection comes from the ratchet arithmetic — so this gap does not weaken the fix.

## Components touched

| File | Change |
|---|---|
| `functions/src/referral/referralConstants.ts` | add `future_dated_activity` to `ReferralFlag`; leave `BLOCKING_FLAGS` unchanged |
| `functions/src/referral/reconcileReferrals.ts` | new exported pure `ratchetObservedDayKeys()`; `gatherSignals` returns raw keys and gates its measurement scan on the already-OBSERVED count (not the raw count — codex P2, else a measurement-only qualifying day is hidden); handler applies ratchet + persists `observedDayKeys` / `lastObservedRunDateKey`; grade on observed length |
| `functions/src/__tests__/referral/reconcileReferrals.test.ts` | new cases (below) |

`referralDashboard.ts` is deliberately **not** touched: it returns marketer-level
aggregates (`MarketerRow` / `DashboardTotals`) with no per-referral rows, so there is
nowhere for a per-referral flag or day-count to land without designing a new view.

The ratchet lands as a **pure exported function** taking
`(rawKeys, observedKeys, lastRunDateKey, runDateKey)` and returning
`{ observedDayKeys, newlyCredited, futureDated }`, mirroring how `computeActiveDayKeys` and
`gradeReferral` are already split out for testability. No Firestore access inside it.

## Test plan (TDD — pure function first)

1. Honest user, one write per day across 4 nightly runs → qualifies on run 4.
2. Burst with 4 future-dated keys in one session → 0 credited on the first run, 1 per run
   after, `futureDated` true on every run where future keys are present.
3. Backdated key older than `lastObservedRunDateKey` → not credited.
4. Key equal to `runDateKey` → not credited this run, credited next run.
5. Grader outage (last run 3 days ago) → all completed days in the gap credited, none lost.
6. First observation with no prior state → attribution-day default applies.
7. In-flight migration: `activeDayKeys` present, `observedDayKeys` absent → seeded, not
   zeroed.
8. Final-window-day activity graded within `RECONCILE_GRACE_DAYS`.
9. `observedDayKeys` never shrinks across runs (monotonicity).

## Deployment

`firebase deploy --only functions:reconcileReferrals,functions:debugReconcileReferrals`
(both already in the `deploy --only` allow-list in `functions/package.json` — per
`feedback_functions_deploy_allowlist`, anything new must be added there, but these exist).
Verify with the in-app debug menu "Run grader" against a seeded test account.

## Rejected: whatsappConfirmed

The 1.1.0 audit's LOW finding recommended making `whatsappConfirmed` a `serverOnlyField`.
Investigated and rejected for this work:

- `FirebaseUserRepository.kt:69` and `:147` write the field on both the create and update
  paths. A `serverOnlyField` rule would make every Edit Profile / Workshop Setup save fail
  with PERMISSION_DENIED on the live 1.1.x binaries — a P0 regression in service of a LOW.
- Confirmation is **entirely client-side**: `EditProfileViewModel.kt:286` generates the
  code, and `submitConfirmCode()` (`:311`) compares it in-process before writing `true`.
  There is no server-side truth for a rule to enforce.
- Real exposure is narrower than "compromised trust anchor": `resolveUniqueUid`
  (`accountLinking.ts:66`) fails closed on ambiguity, so claiming another user's number
  mostly *denies* them bot linking rather than granting takeover.

The real fix is server-issued WhatsApp confirmation over the existing Cloud API
(`whatsappWebhook` is already deployed), after which the field can become server-only.
That is feature work, backlogged.

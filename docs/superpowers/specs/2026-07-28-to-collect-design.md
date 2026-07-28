# "To Collect" — delivered/ready unpaid to-do

**Date:** 2026-07-28
**Status:** Approved design, ready for implementation plan
**Feature package:** `com.danzucker.stitchpad.feature.collection`

## Problem

When a tailor finishes a garment and hands it over (or marks it ready) without
collecting full payment, nothing in the app surfaces the outstanding money as an
actionable item. Every in-app visual surface deliberately excludes `DELIVERED`
orders:

- `feature/dashboard/domain/NbaCalculator.kt` early-returns on `DELIVERED`.
- `feature/dashboard/domain/BucketCalculator.kt` filters out `DELIVERED` before
  summing `outstandingAmount`.
- Reports `CustomerInsightsCalculator.kt` excludes `DELIVERED` from its debtors
  list.

The only current reminder is the once-a-day `dailyDigest` Cloud Function
(`TO_COLLECT` notification + push + email), which is opt-out-able and leaves no
persistent in-app task. There is no "to-do list" for money owed.

`DashboardState.outstandingAmount` / `outstandingOrderCount` are already computed
but referenced only in `@Preview` code — never rendered.

## Goal

Give the tailor a persistent, actionable **"To collect"** surface for orders that
are done-but-unpaid, plus a dashboard entry point that escalates when a debt goes
overdue.

## Key constraint that keeps this small

**No new Firestore data and no migration.** Everything derives from fields orders
already have: `payments` (→ `balanceRemaining`), `status`, and `statusHistory`
(`StatusChange(status, changedAt)`). Existing orders start appearing immediately.

## Domain model (existing, for reference)

`core/domain/model/Order.kt`:
- `status: OrderStatus` — enum `{ PENDING, IN_PROGRESS, READY, DELIVERED }`. There
  is no "quote" or "paid" status; payment state is orthogonal to lifecycle.
- `statusHistory: List<StatusChange>` where `StatusChange(status, changedAt: Long)`.
- `balanceRemaining: Double` (computed) = `payableTotal − depositPaid`, floored at 0.
- `deadline: Long?`, `createdAt: Long`, `updatedAt: Long`.

## Definitions

- **Collectible order:** `status ∈ {READY, DELIVERED}` **and** `balanceRemaining > 0`.
- **`owedSince`:** the earliest `statusHistory.changedAt` whose `status` is `READY`
  or `DELIVERED` (the moment the garment first became collectible). Fallback when
  no such entry exists (legacy orders): `updatedAt`, then `createdAt`.
- **`daysOwed`:** whole days between `owedSince` and `now`.
- **Overdue:** `daysOwed >= OVERDUE_THRESHOLD_DAYS` (**7**) and `balanceRemaining > 0`.
  `7` is a named constant; per-tailor configuration is deferred (consistent with
  the VIP-threshold precedent).

## Architecture

### Domain — `feature/collection/domain/`

`CollectionCalculator` — a pure object (matches the project's "aggregation in
`feature/x/domain` pure objects, not the ViewModel" convention). Pure, injectable
`now`, unit-testable.

- Input: `(orders: List<Order>, now: Long)`.
- Filters to collectible orders.
- Produces:
  - `CollectibleOrder(orderId, customerId, customerName, balanceRemaining,
    owedSince, daysOwed, isOverdue, status)`
  - `CollectionSummary(totalOutstanding: Double, orderCount: Int, overdueCount: Int)`
- Owns sort + filter logic (below).

`CollectionCalculator` is the **single source of truth** for "money to collect."
The dashboard card, the dashboard hero escalation, and the list all read from it,
so they never disagree.

It deliberately does **not** modify `BucketCalculator.outstandingAmount` (which
excludes `DELIVERED` and feeds other dashboard state). We add alongside rather
than change that number's meaning.

Constants live in a `CollectionDefaults` object (or equivalent):
`OVERDUE_THRESHOLD_DAYS = 7`.

### Sorts (list)

Applied after an overdue-first partition (overdue orders always rank above
non-overdue within any chosen sort):

- **Oldest owed first** (default) — ascending `owedSince`.
- **Biggest balance first** — descending `balanceRemaining`.
- **Newest first** — descending `owedSince`.
- **Customer A–Z** — `customerName`, case-insensitive.

### Filters (list)

- **Overdue only** — `isOverdue == true`.
- **By status** — `DELIVERED` or `READY`.
- **By customer** — single `customerId`.

### Presentation — `feature/collection/presentation/`

Standard MVI, following project patterns:
- `ToCollectState`, `ToCollectAction`, `ToCollectEvent` sealed types.
- `ToCollectViewModel` — observes the existing orders flow, recomputes via
  `CollectionCalculator`, holds current sort + filter selection in state.
- Root composable (owns the ViewModel via `koinViewModel()`) + stateless
  `ToCollectScreen` with a `@Preview`.
- **Row:** customer name, `daysOwed` label ("owed 12 days") or **Overdue** badge,
  `balanceRemaining` in JetBrains Mono, actions.
- **Row actions:**
  - **Tap row → order detail** (the existing payment block + "Record payment" live
    there). This is the primary/record-payment path; no inline sheet in V1.
  - **WhatsApp "chase" button** — reuses the existing WhatsApp-open mechanism used
    by the NBA collect actions.
- **Leaving the list:** an order drops off only when `balanceRemaining` reaches 0
  (recording a payment, or a discount that zeroes it). No write-off / snooze in V1.
- Empty state: friendly "You're all paid up" when no collectible orders.

### Navigation

- New `@Serializable` route object `ToCollect` in the navigation graph.
- Entered from the dashboard card tap and the escalated Focus-hero CTA.
- Cross-feature to order detail via callback (existing pattern).

### Dashboard integration — `feature/dashboard/`

**A — dedicated card.** Rendered whenever `CollectionSummary.totalOutstanding > 0`:
"You're owed ₦X across N orders" with an overdue chip when `overdueCount > 0`.
Taps into the `ToCollect` list. Sits below the Focus hero. The dashboard sources
this from `CollectionCalculator` (replacing the never-rendered
`BucketCalculator`-derived number for this card).

**B — escalation.** `FocusResolver` gains a collection branch: when
`overdueCount > 0`, collecting is promoted into the Focus hero ("₦X owed — N
overdue → Collect now"). When nothing is overdue, the hero keeps its normal
priority and the card sits below unescalated.

**NBA carousel unchanged** in V1 — the card + list are the delivered-unpaid
surface, so we avoid double-nudging the same debt.

## Out of scope (follow-ups)

- **Reports "Outstanding Balances" debtors fix.** `CustomerInsightsCalculator.kt`
  also excludes `DELIVERED` — the same underlying bug on a separate surface.
  Deferred to a follow-up; the "To collect" list is the debtors view for now.
- Per-tailor overdue threshold (currently a constant).
- Inline record-payment sheet on list rows.
- Write-off / waive and snooze mechanisms.

## Testing

- `CollectionCalculator` unit tests:
  - `owedSince` derivation from `statusHistory` (READY-then-DELIVERED picks
    earliest; DELIVERED-only; READY-only) and fallback when history is empty
    (`updatedAt` → `createdAt`).
  - Overdue boundary at exactly 7 days (6 not overdue, 7 overdue).
  - Inclusion rule (excludes PENDING/IN_PROGRESS, excludes zero-balance).
  - Each sort and each filter.
  - `CollectionSummary` aggregation (`totalOutstanding`, `orderCount`,
    `overdueCount`).
- `ToCollectViewModel` tests with a fake orders repository (Turbine,
  `UnconfinedTestDispatcher`), covering sort/filter actions and state emission.
- Screen `@Preview` for the populated and empty states.

## Success criteria

- A garment marked Delivered or Ready with a balance appears on the "To collect"
  list and contributes to the dashboard "You're owed" card.
- After 7 days unpaid it shows an Overdue badge, floats to the top, and promotes
  collection into the Focus hero.
- Recording a payment that clears the balance removes it from both surfaces.
- No new Firestore fields; existing orders populate the list without migration.

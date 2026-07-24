# Dashboard Simplification — "Day at a Glance"

**Date:** 2026-07-24
**Status:** Approved (design) — pending implementation plan
**Branch:** `feature/dashboard-simplify-day-at-a-glance`

## Problem

The dashboard has grown to ~8 stacked sections on a populated day, plus duplicate
entry points for the same actions. The two problems the tailor (end user) feels:

1. **Too many card sections** — Weekly Goal + Today's Work + NBA + Smart + Pipeline
   + Reconnect + Quick access is a long, noisy scroll.
2. **Duplicate entry points** — Inspiration is reachable from 3 places (header icon,
   FAB, Quick-access row); Measurements from 2 (FAB, Quick-access row).

## Guiding philosophy

**Dashboard = your day at a glance.** It surfaces attention items and revenue moves.
Browsing the full order book is the job of the **Orders tab** (permanent in the
bottom nav), which already groups every order into
`OVERDUE → DUE THIS WEEK → IN PROGRESS → READY FOR PICKUP → PENDING`
(`feature/order/presentation/list/TriageGroup.kt:7-13`).

We reduce noise by cutting genuine duplicates and dead weight, and by demoting the
biggest low-signal block to a one-line summary — **without moving any real
capability off the dashboard** except the browse-my-book detail, which the Orders
tab owns better.

This is a **conscious, scoped trim**, not a philosophy reversal: Pipeline stays
present (as a summary), NBA stays always-on, KPIs stay in Reports.

## Scope — the four changes

### A. Remove the bottom "Quick access" section
- **Remove:** the `QuickAccessSection` composable and its two `QuickAccessRow`s
  (Inspiration, Measurements) at the bottom of `DashboardContent`
  (`DashboardScreen.kt:1013-1118`).
- **Safe because:** Inspiration still lives in the header icon **and** the FAB
  speed-dial; Measurements still lives in the FAB speed-dial. **No entry point is
  lost.** The FAB `SpeedDialAction`s and header icon are unchanged.
- **Net:** one whole section + two rows removed; zero capability lost.

### B. Slim the Smart Suggestions card to its one working action
- **Keep:** the Smart card and its **Draft Message** tile.
- **Remove:** the two disabled "Coming soon" tiles ("Price this", "Reply helper")
  in `SmartSectionCard.kt` (`:90-107`).
- **Demote:** the card from a 3-tile `LazyRow` hub to a single plain action row
  (visual language consistent with the existing row/card patterns), so it stops
  reading as a multi-feature hub when it is really one action.
- **Must NOT remove the card outright:** the dashboard's `SmartSectionCard` is the
  **only UI entry point** to the Draft Message feature (route wired at
  `MainScreen.kt:576`, reached only via `DashboardAction.OnDraftMessageClick`).
  Removing the card would orphan a shipped feature.
- **Keep:** the free-tier quota chip behaviour (`remainingFreeQuota`) and the
  gating (rendered only when there is ≥1 customer, i.e. not BrandNew/Loading).

### C. Collapse Work Pipeline to a one-line summary
- **Replace:** the two full buckets rendered by `PipelineSection`
  (In progress + Not started, up to 3 preview rows each + headers + totals) with a
  **single summary row**.
- **Copy:** two-count form — e.g. "In the workshop · {N} in progress · {M} not
  started →" (preserves the in-progress vs. not-started signal that made Pipeline
  useful). Reuse existing row visual language.
- **Tap target:** opens the **Orders tab** (existing `OnViewAllOrdersClick` →
  `NavigateToOrders`, or equivalent). One tap to the full, better-grouped book.
- **Data:** already in state — `pipelineInProgressTotal` + `pipelinePendingTotal`.
  The detailed `pipelineInProgress` / `pipelinePending` row lists are no longer
  rendered on the dashboard.
- **Benefit:** reclaims ~80% of the section's height and **sidesteps the
  NBA↔Pipeline double-render** — today NBA order IDs are deduped out of Today's Work
  (`DashboardScreen.kt:898-904`) but **not** out of Pipeline, so an NBA card and the
  same order's Pipeline row can both show. A counts-only summary shows no rows, so
  the double-render disappears.
- **Visibility:** keep the existing empty gating — the summary hides when the
  in-progress + not-started total is 0. First-order onboarding
  (`firstOrderSetup != null`) still suppresses it, unchanged.

### D. Keep Today's Work unchanged
- The urgent-attention safety net stays. It only renders in `BusyDay`, is deduped
  against NBA, and self-limits — it is the dashboard's "what's on fire now" surface
  and carries genuine unique value (urgent orders NBA doesn't turn into an action:
  fully-paid overdue, no-phone customers, in-progress due-today).

## Resulting dashboard shape (populated day)

Header → Focus card → (promoted NBA) → Weekly Goal → Today's Work → NBA/empty →
**Smart (slim row)** → **Pipeline summary (one row)** → Reconnect

(Was: … → Smart (3-tile hub) → Pipeline (two full buckets) → Reconnect →
**Quick access (2 rows)**.)

## Explicitly out of scope (later passes)

- Transient banners: Community invite strip, Welcome-ending banner (remote-config /
  time-limited — different lifecycle).
- Trimming the header icons (e.g. dropping the Inspiration header icon).
- Merging `PipelineSteady` into `QuietDay` (they become visually similar once the
  Pipeline card is a summary, but that is a state-machine change with its own risk).
- NBA promotion ordering above/below Weekly Goal.

## Regression risks & how we manage them

1. **Shared calculator.** `BucketCalculator` feeds **both** Today's Work (triage
   buckets) **and** Pipeline. We only stop *rendering* Pipeline's row lists; the
   triage half must remain intact for Today's Work. Removing the pipeline row lists
   from the UI should not require changing the calculator — prefer leaving
   `BucketCalculator` and state fields in place and only changing the composable, to
   minimise blast radius. (Field/model cleanup, if any, is a follow-up.)
2. **Draft Message orphaning.** Verified the dashboard is the only entry point —
   change B must keep the Draft Message action reachable.
3. **Order-creation affordance.** In states where Pipeline hosted an "Add first
   order" empty CTA, order creation still has the FAB (New order) and the focus-card
   CTA. No dead ends — confirm with a smoke test.
4. **Tests.** Dashboard tests asserting Quick-access rows, the 3-tile Smart layout,
   and full Pipeline bucket rendering will need updating. Existing tests for
   `BucketCalculator`, `NbaCalculator`, Today's Work, and Reconnect should remain
   green (behaviour unchanged).

## Testing

- Unit/UI: update dashboard screen tests for the removed Quick-access section, the
  slimmed Smart row, and the Pipeline summary row (renders counts, hides at 0, taps
  through to Orders).
- Keep calculator tests unchanged and green.
- Manual smoke test (Daniel is QA), per repo convention — one pass per dashboard
  state (BrandNew, FirstCustomer, QuietDay, PipelineSteady, NbaActive, BusyDay,
  ReadyForPickup) confirming: no lost entry points, Draft Message reachable, Pipeline
  summary taps to Orders, order creation reachable.
- iOS compile check before "done" (KMP), and Detekt.

## Success criteria

- Populated-day dashboard drops from ~8 stacked blocks to ~5.
- The tallest low-signal block (Pipeline) becomes one line.
- No action loses its entry point; the Draft Message feature stays reachable.
- No order renders twice on the same screen.

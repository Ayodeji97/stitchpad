# Dashboard Measurements Speed-Dial Action — Design Spec

**Date:** 2026-07-09 · **Status:** Approved direction (Option A) · **Mockups:** `preview/dashboard-measurements-quick-access.html`

## Context

PR 3 (#261) put the "Measurements" quick-access row at the bottom of the dashboard scroll (deliberately below the revenue cards, mirroring Inspiration). Daniel's QA: you have to scroll far to reach it. Three affordances were mocked — extending the existing speed-dial FAB (A), a pinned chips strip under the header (B), a header tape icon (C). Daniel picked **A** after seeing mockups: B permanently costs vertical space on the revenue-first screen; C is icon-only (rejected twice for ambiguity) and crowds a header already carrying Inspiration + bell + avatar.

## Design

One addition to `DashboardScreen.kt`'s existing `speedDialActions` list (currently New customer, New order, Inspiration):

- `SpeedDialAction(label = stringResource(Res.string.dashboard_measurements_card_title), icon = Icons.Default.Straighten, contentDescription = <new string>, onClick = { collapseFab(); onAction(DashboardAction.OnMeasurementsShortcutClick) })` — placed AFTER Inspiration (bottom of the expanded stack, nearest the main FAB, per mockup).
- New string: `dashboard_fab_measurements_cd` = "Open measurements picker".
- Everything downstream already exists and is tested (PR 3): `OnMeasurementsShortcutClick` → customer picker (counts, timeouts, unknown-count handling) → routing → `measurement_detail_viewed(source=dashboard)`.
- The bottom Quick access "Measurements" row STAYS — dual placement, exactly like Inspiration (dial + section).
- FAB visibility rules unchanged (`showFab` gating): on states where the dial is hidden, the Quick access row and the Customers-tab actions sheet remain the entry points.

## Out of scope

Pinned chip strips, header icons, reordering existing dial actions, any ViewModel/state/nav change.

## Testing

No new VM behavior (the action is PR 3's, already unit-tested). Gate: compile Android + iOS test compile + detekt + existing Dashboard suite. QA: expand FAB → labeled Measurements action visible (light+dark) → opens picker; Inspiration/New order/New customer unchanged.

## Delivery

One commit on `feat/measurement-entrypoints` (PR #261 — the PR is titled "dashboard + actions-sheet entry points" and unmerged; this completes its dashboard half).

# Measurement Empty State — View Measurements for Zero-Measurement Customers

**Date:** 2026-07-10
**Status:** Approved design, pending implementation plan

## Problem

When a customer has no measurements and the user taps "View Measurements" (customer actions sheet or dashboard measurements picker), the app routes them away from measurements entirely:

- **Customer actions sheet** (`MeasurementEntryResolver.kt:26-34`): zero measurements falls back to Customer Detail.
- **Dashboard picker** (`DashboardViewModel.kt:411-426`): confirmed zero jumps straight to the Add Measurement form.

Neither lands where the user asked to go. The two paths also disagree with each other, and the routing rule is duplicated across the resolver and the dashboard ViewModel.

## Decision

"View Measurements" always lands on the Measurement Detail screen when the measurement situation is known. For a customer with zero measurements, the detail screen renders an **empty state** with an "Add measurement" CTA that opens the create form. The rule is unified across all entry points.

## Routing rule (unified)

One pure decision function on `MeasurementEntryResolver`, e.g. `destinationFor(count: Int?, singleMeasurementId: String?)`:

| Situation | Destination |
|---|---|
| Exactly one measurement | Measurement Detail with that ID (unchanged) |
| Confirmed zero | **Measurement Detail in empty mode** (`measurementId = null`) — new |
| Several | Customer Detail (unchanged, out of scope) |
| Unknown count (fetch error, timeout, signed out) | Customer Detail (safe fallback, unchanged) |

- The actions-sheet path keeps fetching a first snapshot in the resolver, then applies the shared decision function. An **empty snapshot list means confirmed zero**; a `null` snapshot (error/timeout/signed-out) means unknown.
- The dashboard picker already fetches per-row counts (`DashboardViewModel.kt:369-393`); it applies the same decision function to its existing data instead of duplicating the `when` block. No double-fetching.
- `DashboardEvent.NavigateToAddMeasurement` for the zero case is removed; the zero case emits `NavigateToMeasurementDetail(customerId, measurementId = null)`. (The event type stays if other callers use it.)

## Route and ViewModel changes

`MeasurementDetailRoute` (`navigation/Routes.kt:55-61`): `measurementId` becomes `String? = null`. `customerId`, `source`, and `fromSave` are unchanged.

`MeasurementDetailViewModel`:

- `customerId == null` from `SavedStateHandle` → `NavigateBack` (unchanged guard).
- `measurementId == null` → **empty mode**. The ViewModel already observes `measurementRepository.observeMeasurements(userId, customerId)`; in empty mode:
  - Non-empty list → display the **most recent** measurement (self-heal: a measurement synced from another device, or created and returned to, upgrades the screen in place).
  - Empty list → state flags empty (e.g., `isEmpty = true` alongside `measurement = null`, `isLoading = false`).
- The existing "requested measurement not found → navigate back" guard applies **only when a non-null ID was requested** (the `fromSave` write-lag exception is unchanged).
- Customer observation (for the top-bar name and lock state) is unchanged and also feeds the empty state.

## Screen (empty mode)

Centered hero empty state, consistent with `CustomerEmptyState` / customer detail's `MeasurementsEmptyState` pattern:

- `Straighten` icon inside a `primaryContainer` circle.
- Title: "No measurements yet".
- One supporting line using the customer's first name (e.g., "Record Fola's measurements to reuse on orders").
- Filled "Add measurement" button.

Chrome: top bar keeps back navigation and the customer name; **share, edit, delete, and rename actions are hidden** in empty mode (no share sheet, no delete dialog reachable).

Standard M3 color tokens only, so light and dark mode are both defined by construction. All strings via compose resources (positional args for the name substitution). The empty variant gets a `@Preview`.

## CTA and back stack

- CTA emits a new event (e.g., `MeasurementDetailEvent.NavigateToAdd(customerId)`) → `MeasurementFormRoute(customerId)` in create mode, wired in `MainScreen.kt`.
- On save, the form already navigates to `MeasurementDetailRoute(..., source = POST_SAVE, fromSave = true)` with `popUpTo` logic (`MainScreen.kt:321-339`). Verify/extend that logic so the **empty-mode detail instance is popped**: back from the freshly saved detail returns to where the user started (customer list or dashboard), never to a stale empty screen and never through the form.

## Gating

Locked (read-only) customers: the "Add measurement" CTA goes through the same `requireUnlocked` gate as the other detail actions (`MeasurementDetailViewModel.kt:170-176`) → Upgrade navigation instead of the form.

## Analytics

Existing `source` values (`DASHBOARD`, `CUSTOMER_ACTIONS_SHEET`, …) carry through unchanged on the empty-mode route, so the entry-point dimension is preserved. No new events required.

## Testing

Unit tests (JUnit5 + Turbine + fakes, `:composeApp:testDebugUnitTest`):

- Resolver decision function: 0 / 1 / many / unknown-count cases.
- Resolver snapshot interpretation: empty list = confirmed zero → empty-mode detail; null snapshot → customer detail.
- Dashboard picker row routing through the shared decision function.
- ViewModel empty mode: empty list → empty state; measurement appears → displayed; CTA emits `NavigateToAdd`; locked customer → Upgrade event; non-null-ID not-found guard still navigates back.

Platform checks before done: iOS compile (KMP), detekt, full `testDebugUnitTest`.

Manual smoke test steps for the PR (Daniel is QA):

1. Customer with zero measurements → actions sheet → View Measurements → empty state shown, correct name in top bar, no share/edit/delete actions.
2. Same via dashboard measurements picker.
3. Tap Add measurement → form → save → detail shows new measurement; back returns to the originating screen (not the empty state, not the form).
4. Customer with exactly one measurement → unchanged (straight to detail).
5. Customer with several → unchanged (Customer Detail).
6. Locked customer with zero measurements → CTA routes to Upgrade.
7. Repeat 1–3 in dark mode.

## Out of scope

- Multi-measurement case (several → Customer Detail stays; a picker or per-customer list screen is a possible follow-up).
- Any changes to the Customer Detail measurements section.

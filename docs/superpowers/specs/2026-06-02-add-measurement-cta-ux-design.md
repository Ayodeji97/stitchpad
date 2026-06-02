# Add-Measurement CTA & Control UX — Design Spec

**Date:** 2026-06-02
**Branch:** `worktree-measurement-cta-ux`
**Status:** Approved design, pending implementation plan

## Problem

When creating a customer, the "add measurements next" option is a bare `Checkbox`
row (`MeasurementsToggleRow`) sitting just above a fixed "Save Customer" CTA. Two
problems:

1. The checkbox has no visual weight, so tailors miss it and don't realise they can
   flow straight into measurements.
2. The CTA always reads "Save Customer" even when the box is ticked, so it gives no
   hint that tapping it both saves the customer *and* moves to a measurement screen.
   The downstream measurement screen likewise reads "Save Measurements" with no
   awareness that it was reached as the second step of customer creation.

## Goals

- Make the "add measurements next" choice deliberate and hard to overlook.
- Make the customer-form CTA copy reflect what tapping it actually does.
- On the measurement screen, when reached from customer creation, adapt the CTA and
  offer a graceful escape hatch (the customer is already saved by then).

## Non-goals

- No change to the underlying save logic, repositories, or measurement data model.
- No change to the measurement screen when reached outside the create flow (editing
  an existing customer's measurements, or the order "link measurement" flow).
- No change to edit-customer mode (the control only ever showed in add mode).

## Current state (verified)

- `feature/customer/presentation/form/CustomerFormScreen.kt`
  - `MeasurementsToggleRow` — `Checkbox` + label `customer_form_add_measurements_next`,
    shown only when `!state.isEditMode`.
  - `SaveButton(label = customer_form_save_button)` — always "Save Customer".
- `CustomerFormState.addMeasurementsNext: Boolean = true`.
- `CustomerFormViewModel` emits `CustomerFormEvent.NavigateToNewCustomerMeasurement(customerId)`
  when `!isEditMode && addMeasurementsNext`, else `NavigateBack`.
- `MainScreen.kt` `onNavigateToCustomerWithMeasurement` pushes
  `CustomerDetailRoute` (popping the form) then `MeasurementFormRoute(customerId)`.
- `navigation/Routes.kt` — `MeasurementFormRoute(customerId, measurementId? , linkToOrderId?)`.
- `feature/.../MeasurementFormScreen.kt` — single `Button` labelled
  `measurement_save_button` ("Save Measurements"); `MeasurementFormViewModel` reads
  args from `SavedStateHandle`; emits `NavigateBack` on save. No source awareness.

## Design

### 1. Prominent measurement card (replaces the checkbox row)

Replace `MeasurementsToggleRow`'s bare checkbox with a bordered, tappable card —
still `toggleable` with `Role.Checkbox`, still bound to
`state.addMeasurementsNext`, still rendered only when `!state.isEditMode`.

Layout (single row inside the card):
- Leading icon tile (~34dp, rounded, tinted container) — a measurement/ruler glyph.
- Column: title "Add measurements next" + helper line "We'll save them, then take
  you to their measurements."
- Trailing `Checkbox` (`onCheckedChange = null`; the whole card is the toggle target).

Card styling uses existing `DesignTokens` (radius, spacing) and theme colors; the
border emphasises when checked vs unchecked. Defined for **both light and dark mode**
at spec time.

### 2. Adaptive customer-form CTA

`SaveButton` label and leading icon become a function of state:

| Condition | Label | Icon |
|---|---|---|
| add mode AND `addMeasurementsNext == true` | "Save & Add Measurements" | forward arrow |
| add mode AND `addMeasurementsNext == false` | "Save Customer" | check |
| edit mode | "Save Customer" | check |

No change to `OnSaveClick` handling or the existing navigation event logic.

### 3. Measurement screen knows its source

Add a `fromCustomerCreation: Boolean = false` field to `MeasurementFormRoute`.

- `MainScreen.onNavigateToCustomerWithMeasurement` pushes
  `MeasurementFormRoute(customerId = newId, fromCustomerCreation = true)`.
- All other call sites keep the default `false`.
- `MeasurementFormViewModel` reads the flag from `SavedStateHandle` into
  `MeasurementFormState` (e.g. `fromCustomerCreation: Boolean`).

Measurement screen behaviour:

| `fromCustomerCreation` | Primary CTA | Secondary |
|---|---|---|
| `true` | "Save" | "Skip for now" text button |
| `false` | "Save Measurements" | none (unchanged) |

- "Skip for now" emits a new event (e.g. `MeasurementFormEvent.SkipMeasurements`)
  that navigates back to the customer detail already on the back stack — the same
  destination a successful save lands on — **without writing an empty measurement**.
- "Save" keeps the existing save + `NavigateBack` behaviour.

### 4. Strings

All copy via `compose.resources` string resources — no hardcoded strings, and
apostrophes use `&apos;` / typographic `’`, never `\'`.

- `customer_form_save_button` — "Save Customer" (unchanged, used when unticked / edit).
- New: customer-form CTA when ticked — "Save & Add Measurements".
- Card title reuses existing `customer_form_add_measurements_next`.
- New: card helper line key — "We'll save them, then take you to their measurements."
- `measurement_save_button` — "Save Measurements" (unchanged, default).
- New: create-flow primary CTA — "Save".
- New: create-flow skip link — "Skip for now".

## Affected files

- `feature/customer/presentation/form/CustomerFormScreen.kt` — new card composable + CTA conditional.
- `navigation/Routes.kt` — add `fromCustomerCreation` to `MeasurementFormRoute`.
- `navigation/MainScreen.kt` — pass `fromCustomerCreation = true` on the create-flow push.
- `feature/.../MeasurementFormScreen.kt` — adaptive CTA + skip button.
- `MeasurementFormState` / `MeasurementFormViewModel` — read flag, add skip event.
- `MeasurementFormEvent` — add `SkipMeasurements`.
- `composeResources/values/strings.xml` (+ any locale variants) — new/updated strings.

## Testing / QA

- ViewModel unit tests (`:composeApp:testDebugUnitTest`, kotlin.test + Turbine):
  - `MeasurementFormViewModel` exposes `fromCustomerCreation` from `SavedStateHandle`.
  - `SkipMeasurements` emits the navigate-back event and writes no measurement.
- Compose previews for the customer-form card (checked + unchecked, light + dark) and
  the measurement screen CTA in both source modes.
- Manual smoke test (Daniel is QA), to be enumerated in the PR:
  1. New customer, leave box ticked → CTA reads "Save & Add Measurements" → tap → lands on measurement screen showing "Save" + "Skip for now".
  2. On measurement screen, "Skip for now" → lands on the new customer's detail, no measurement saved.
  3. On measurement screen, "Save" → measurement saved, lands on customer detail.
  4. New customer, untick the box → CTA reads "Save Customer" → tap → lands on customer detail, no measurement screen.
  5. Edit an existing customer → no card shown, CTA reads "Save Customer".
  6. Open measurements from customer detail / order link flow → CTA still "Save Measurements", no skip link.
  7. Verify light + dark mode for the card and both CTA states.
- Run iOS compile before declaring done (KMP gotcha): `./gradlew :composeApp:assembleDebug` for Android and an iOS build, since route-arg + Compose changes are cross-platform.

## Open questions

- Final word on the create-flow measurement CTA: "Save" (recommended) vs "Complete".

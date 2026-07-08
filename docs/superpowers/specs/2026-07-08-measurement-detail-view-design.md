# Measurement Detail View, Quick Access & Sharing — Design Spec

**Date:** 2026-07-08 · **Status:** Approved direction (V1 sectioned-note) · **Mockups:** `Preview/measurement-visibility-redesign.html`

## Context

Tester feedback (WhatsApp): *"After saving the measurements… make it easier not to go directly to the editing Mode, it can just be saved as a note or table. Bcs if it go to edit mode u can accidentally edit it."* Today, tapping a saved measurement anywhere (customer detail, order detail, post-save) opens `MeasurementFormScreen` — an always-editable wizard with no read-only mode. Measurements are also invisible at app level (only an inline section on customer detail) and cannot be shared. Measurements are a core feature for tailors; the app is live on both stores, so all changes must be additive and sliced into independently shippable PRs.

## Decisions (brainstormed with Daniel, 2026-07-08)

1. **No Measurements bottom tab.** Measurements stay customer-anchored.
2. **New read-only Measurement Detail screen** — the centerpiece. Layout: **V1 "sectioned note"** (card per body section, label left / JetBrains Mono value right, sticky bottom bar with primary *Edit measurement* + square *Share* button; Rename/Delete in app-bar overflow).
3. **Row overflow on customer detail is removed** — measurement rows become purely tappable; all manage actions live on the detail screen.
4. **Locked (over-cap free-tier) customers get the view too** (read-only-visible principle); Edit/Share/Delete gated behind upgrade.
5. **Post-save:** Save navigates to the detail view (with "Measurement saved" snackbar), not straight back.
6. **Quick access (single entry point):** Dashboard Quick-access row → customer picker sheet. *(Customer-list tape chips and search sub-results were mocked but cut on 2026-07-08 — ambiguous icon-only affordance, inconsistent rows, and the tape chips would have required a denormalized measurement count + backfill, all to save one tap each. The labeled dashboard entry point answers the discoverability feedback on its own.)*
7. **Sharing:** share as image, PDF, or WhatsApp plain text.

## Architecture

New feature package: `feature/measurement/presentation/detail/` following MVI + Root/Screen split.

### Route & navigation

- New `@Serializable` route `MeasurementDetailRoute(customerId: String, measurementId: String)` in `navigation/Routes.kt`, registered in `MainScreen.kt`.
- Rerouted taps (all currently open `MeasurementFormRoute` with `measurementId`):
  - Customer detail measurement row → detail view.
  - `AddMeasurementSheet` existing-measurement rows → detail view.
  - Order detail "view measurement" → detail view.
- `MeasurementFormViewModel.save()` in edit **and** create mode → emits event navigating to detail view (replacing form in back stack so Back → customer detail). `fromCustomerCreation` flow keeps its existing continue/skip behavior.
- Edit button on detail view → existing `MeasurementFormRoute(customerId, measurementId)` — the form is untouched.

### Detail screen (MVI)

- `MeasurementDetailViewModel` observes `MeasurementRepository.observeMeasurements(userId, customerId)` (existing Flow) filtered to `measurementId` — live updates after edits, no new repository methods needed for display.
- State: measurement + customer name (for share payloads) + locked flag + sheet/dialog visibility. Actions: Edit, Share (+3 share options), Rename, Delete, ConfirmDelete, Back. Events: NavigateBack, NavigateToEdit, ShareLaunched, Error(UiText).
- Sections rebuilt presentation-side from `BodyProfileTemplate.sectionsFor(gender)` + custom fields (`CustomMeasurementFieldRepository`), same as the form does; only fields present in `measurement.fields` render. Values via existing `formatMeasurementValue`; title via `measurementDisplayName`.
- Rename dialog and delete confirm dialog move here (logic mirrors `CustomerDetailViewModel.deleteMeasurement()` / rename — reuse repository calls; fire-and-forget per GitLive offline rule, trust the listener).
- If the observed measurement disappears (deleted on another device), emit NavigateBack.
- Typography: values use `JetBrainsMonoFamily()` (as documented in `Type.kt`); light + dark per mockup.

### Sharing

- New `core/sharing/MeasurementSharer` (expect/actual, Android + iOS) mirroring `OrderReceiptSharer`: `shareAsImage(data)`, `shareAsPdf(data)` rendering the branded "measurement card" (paper-light always), via FileProvider/ACTION_SEND on Android and UIActivityViewController on iOS.
- `MeasurementShareData` model + `MeasurementShareFormatter` (commonMain) producing both the card content and the WhatsApp text (bold markers, sections, footer "Sent from StitchPad · getstitchpad.com").
- WhatsApp option uses existing `WhatsAppLauncher` with the customer's `whatsappNumber` when present, else generic share sheet with the text.
- Share sheet composable mirrors `ShareReceiptBottomSheet` (3 options per mockup).

### Entry point

- **Dashboard:** second row in existing `QuickAccessSection` (`DashboardScreen.kt`) — icon `Straighten`, "Measurements / Find a customer's numbers fast" → customer-picker `ModalBottomSheet` (search + customer rows with measurement counts; no-measurement rows offer "+ Add"). Picking → detail view if the customer has exactly one measurement, customer detail (measurements section) if more. Counts come from the picker's own on-demand queries when the sheet opens — no denormalized fields, no changes to the customer list or search.

### Gating (locked customers)

Detail view opens for locked customers (replacing today's inert `ReadOnlyMeasurementItem` dead-end); Edit/Share/Rename/Delete show the existing upgrade affordance used elsewhere in freemium (locked action → upgrade sheet).

## Error handling

Existing patterns: repository `Result<T, DataError>`; `toMeasurementUiText()` mapping; share failures surface snackbar via Event(UiText). No new error types expected beyond a `ShareError` case if the platform render fails.

## Analytics

`MeasurementDetailViewed` (source: customer_detail | order_detail | post_save | dashboard), `MeasurementShared` (format: image | pdf | whatsapp_text) — added alongside existing `AnalyticsEvent.MeasurementAdded`.

## Slicing (independently shippable PRs)

1. **PR 1 — core fix:** detail screen + route + rerouted taps + post-save landing + rename/delete relocation + locked-customer view. *(Fixes the tester complaint end-to-end.)*
2. **PR 2 — sharing:** `MeasurementSharer`, formatter, share sheet, analytics.
3. **PR 3 — entry point:** dashboard quick-access row + customer picker sheet.

## Testing

- ViewModel unit tests (`:composeApp:testDebugUnitTest`, kotlin.test + Turbine): load/observe, delete flow, rename, locked gating, post-save navigation event, share option → correct payload/event.
- `MeasurementShareFormatter` pure-function tests (text output, section grouping, custom fields, unit suffix).
- iOS compile + clean Xcode build before done (KMP rules); detekt; manual QA smoke steps in each PR description (Daniel is QA).
- Debug-menu evaluation per feature rule: PR 3 could add a "seed measurements" helper — evaluate in plan.

## Out of scope

Measurement history/versioning, per-field units, custom measurement template editing (separate backlog), same-screen inline editing.

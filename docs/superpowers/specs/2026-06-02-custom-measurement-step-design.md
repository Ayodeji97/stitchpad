# Custom measurements as a first-class wizard step

**Date:** 2026-06-02
**Branch context:** feature/ptsp-13-style-fabric-grouping (custom-field work originally PTSP-12)
**Screen:** `feature/measurement/presentation/form/MeasurementFormScreen.kt`

## Problem

On the Add/Edit Measurements screen, the "+ Add custom field" entry point lives at the
bottom of the **scrolling** middle zone of the last default section — below every default
field, the "Show less" toggle, and any existing custom rows. The fixed footer (Previous/Next,
"Add a note", Save) is always visible, but the Add-field affordance is not: a tailor must
scroll past everything to reach it, and nothing on the other section steps signals that custom
measurements exist at all. The feature is effectively hidden.

## Goal

1. Make the custom-measurements feature **structurally discoverable** — promote it to a
   first-class step in the section wizard rather than an appendix to the last section.
2. Make the entry point **always reachable** from any step — without crowding the already-busy
   fixed footer.

## Approach (chosen: A — tappable "Custom" pill in the progress row)

The progress row is the one piece of always-visible chrome that is **not** the footer (it sits
in the pinned header). Putting the custom-step entry there satisfies both goals with a single
mechanism: the pill is the always-visible cross-step entry **and** the first-class step
indicator. No new footer actions, no competition with the Save button.

Two rejected alternatives:

- **B — Persistent "+ Add custom field" button pinned in the footer.** The most literal
  "always-visible CTA," but stacks four actions in the footer (Add custom / Previous-Next /
  Add note / Save) and pits Add against Save for prominence.
- **C — Dedicated step only, no global entry.** Clean, but drops the cross-step entry point.

## Design

### 1. Pager gains a trailing "Custom" page

- `HorizontalPager` `pageCount` becomes `state.sections.size + 1` (only when `sections` is
  non-empty — i.e. a gender is selected; unchanged behavior when empty). The custom page index
  is `state.sections.size`.
- `CustomFieldsSection` is **removed** from the bottom of the last default section
  (the `if (pageIndex == state.sections.lastIndex)` block) and rendered only on the new custom
  page (`pageIndex == state.sections.size`).
- Within the custom page, the **"+ Add custom field" button moves to the top** — directly under
  the "Custom / Add your own measurements…" header and pill — with existing custom fields listed
  beneath it. This keeps the primary action visible without scrolling even when a tailor has
  many custom fields.

### 2. Progress row gains a trailing tappable pill

`SectionProgressRow` (pinned header, already always visible) renders the N section dots and then
a trailing `✛ Custom` pill.

- **Tap target:** the pill fires `OnSectionChange(state.sections.size)` to jump to the custom
  page. The section **dots also become tappable** (each fires `OnSectionChange(index)`) for
  consistency.
- **Pill states:**
  - *Inactive* — outlined primary, shown while on a default step.
  - *Active* — filled primary, shown while the custom page is open
    (`currentSectionIndex == sections.size`).
  - *Has data* — filled, when any custom field has a value that parses to a positive double
    (mirrors the existing rule that lights a section dot when it holds persistable data).
  - *Locked* — lock glyph + muted outline, when `!canUseCustomMeasurements`.
- **Counter text:** the existing "X of N" counter keeps counting only the default sections
  (`N == sections.size`). On the custom page the label reads **"Custom"** instead of a number.

### 3. Navigation wiring

- `MeasurementFormViewModel`: `OnNextSection` coerce bound changes from
  `(currentSectionIndex + 1).coerceAtMost(sections.size - 1)` to `...coerceAtMost(sections.size)`
  so Next walks onto the custom page. `OnPreviousSection` is unchanged (still
  `coerceAtLeast(0)`).
- `SectionNavigation` receives `totalSections = state.sections.size + 1`, so Next stays enabled
  through the last default section and disables on the custom page; Previous disables on
  section 0.
- The two existing sync effects — `LaunchedEffect(pagerState.currentPage)` →
  `OnSectionChange(currentPage)` and `LaunchedEffect(currentSectionIndex)` →
  `animateScrollToPage` — continue to work unchanged because both indices now range
  `0..sections.size`.

### 4. Locked behavior

The locked pill navigates to the custom step like any other; it never short-circuits to the
upgrade screen. The step's existing locked content is unchanged: the locked Add button (lock
icon + `LOCKED` badge), the locked caption, and `onLockedAddClick → NavigateToUpgrade`.

### 5. Unchanged rules (carried with the moved section)

- **Edit-mode visibility:** past measurements with recorded custom-field values stay visible on
  every tier, forever — the existing `visibleFields` logic moves verbatim onto the new page.
- **Gender filtering** of custom fields stays in the ViewModel.
- The numeric-only input filter and long-press-to-manage gesture on custom rows are unchanged.

## Components / files touched

- `MeasurementFormScreen.kt`
  - `MeasurementFormScreen`: pager `pageCount = sections.size + 1`; render
    `CustomFieldsSection` on `pageIndex == sections.size` instead of on the last default
    section; pass `totalSections = sections.size + 1` to `SectionNavigation`.
  - `SectionProgressRow`: add trailing tappable `CustomStepPill`, make dots tappable, take an
    `onJumpToSection: (Int) -> Unit` callback and a "has custom data" / "is locked" / "is
    active" signal; switch counter label to "Custom" on the custom page.
  - `CustomFieldsSection`: move `AddCustomFieldButton` above the field list.
- `MeasurementFormViewModel.kt`: `OnNextSection` coerce bound `sections.size - 1` →
  `sections.size`.
- State/Action: **no new members expected** — `OnSectionChange(index)` already exists and
  carries the target index. Confirm during implementation that nothing else assumes
  `currentSectionIndex <= sections.size - 1`.
- String resources: pill label ("Custom"), custom-page counter label ("Custom"), and the
  entitled-empty caption if reworded (`custom_field_empty_caption` already exists — reuse if
  copy still fits).

## Testing

- **ViewModel unit tests:**
  - `OnNextSection` from the last default section lands on `sections.size` (the custom page),
    and a further `OnNextSection` does not overshoot.
  - `OnSectionChange(sections.size)` sets `currentSectionIndex` to the custom page.
  - `OnPreviousSection` from the custom page returns to the last default section.
- **Compose previews** (each Screen composable must have one per project rule):
  - Custom step — entitled, with a field.
  - Custom step — entitled, empty.
  - Custom step — locked.
  - A default step showing the pill in inactive state.
- **Manual smoke test (Daniel is QA):**
  1. New measurement, select a gender → confirm the `✛ Custom` pill appears in the header on
     section 1 without scrolling.
  2. Tap the pill from section 1 → lands on the custom step; Add button is at the top.
  3. Add a custom field → confirm the pill fills (has-data state) and the field appears below
     the button.
  4. Step through with Next/Previous → Next reaches the custom step and disables there;
     Previous returns.
  5. Free tier past welcome window → pill shows locked glyph; tapping opens the custom step with
     the locked Add button + upgrade CTA (not a direct jump to upgrade).
  6. Edit a past measurement that has a recorded custom value on Free post-welcome → value still
     visible on the custom step.
- Run iOS compile before declaring done (KMP — JVM-only API trap); the pager/`combinedClickable`
  paths already compile on iOS, but verify.

## Out of scope

- No change to custom-field creation/edit/archive sheets (`AddCustomFieldSheet`,
  `ConfirmArchiveDialog`).
- No change to entitlement logic, gender filtering, or persistence.
- No change to the default-section field layout or the "Show more/less" behavior.

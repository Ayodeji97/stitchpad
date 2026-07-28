# Custom Measurement — Bug Fixes & UX Polish

**Date:** 2026-07-28
**Branch base:** `fix/measurement-input-punctuation`
**Feature area:** `feature/measurement/presentation/form/`
**Status:** Approved design, ready for implementation plan

## Context

The custom-measurement editing flow (the "Edit Measurements" wizard) is a core
StitchPad feature. Before we extend it, we are fixing four issues surfaced during
testing on the measurement form. All four live in the measurement form package;
none change the domain or data layers.

This work follows PR #297 (measurement value String model — `fields: Map<String,
String>`, `sanitizeMeasurementInput`, `isPersistableMeasurementValue`).

## Scope

Four independent changes, shippable together:

1. **Cursor-jump fix** on every text input in the measurement form.
2. **Value-field punctuation** — confirm alignment (already functional; covered by #1).
3. **"Field added" confirmation** — informational snackbar after creating a custom field.
4. **Step indicator** — distinct current/completed/unvisited states + section-name heading.

Out of scope: restyling the sheet Value field, editing-confirmation snackbars,
any domain/data/repository change, the custom-measurement *feature* expansion
(separate future work).

---

## 1 · Cursor-jump fix

### Problem
Every text field in the form binds a raw `String` that round-trips through the
ViewModel `StateFlow`. Because no `TextFieldValue`/`TextRange` is kept locally,
Compose loses the caret and snaps it to the end mid-edit. This is worst on the
value fields, where `sanitizeMeasurementInput` also mutates the string. Matches
the known-issue note *"Compose TextFieldValue cursor — VM String direct desyncs
cursor; bind local TextFieldValue."*

### Affected inputs (all four)
- Measurement name — `OutlinedTextField` bound to `state.name` (`MeasurementFormScreen.kt` ~255).
- Custom field name — `OutlinedTextField` bound to `draft.label` (`AddCustomFieldSheet.kt:102`).
- Sheet Value — `OutlinedTextField` bound to `draft.initialValue` (`AddCustomFieldSheet.kt:112`).
- Template + custom value fields — `MeasurementTextField` (`BasicTextField`, `MeasurementFormScreen.kt:819`).

### Design
Introduce one shared, caret-preserving state helper (new file under
`feature/measurement/presentation/form/components/`, e.g. `CaretPreservingText.kt`,
commonMain) used by all four sites. It holds a local `TextFieldValue` and
reconciles it against the hoisted `String`.

**Contract:**
- Hold local `TextFieldValue` in `remember`.
- **External sync:** when the incoming `value: String` differs from local `.text`
  (programmatic changes only — value seeding, gender switch, load), reset local to
  `TextFieldValue(value, TextRange(value.length))` (caret to end is correct here).
- **User edit:** given the raw `TextFieldValue` from the field:
  - `sanitizedText = sanitize(raw.text)` (identity for the two name fields)
  - `newCaret = sanitize(raw.text.take(raw.selection.end)).length` — maps the caret
    precisely because `sanitize` only *removes* characters. Inserting a comma or
    decimal mid-string keeps the caret in place.
  - set local `= TextFieldValue(sanitizedText, TextRange(newCaret))`
  - if `sanitizedText != value`, call `onValueChange(sanitizedText)`.

**Pure-function extraction for testability:** the caret math is a pure function
`sanitizedCaret(rawText, selectionEnd, sanitize): Int` (commonMain) that can be
unit-tested without Compose.

**Wiring:**
- `MeasurementTextField` gains an optional `sanitize: (String) -> String = { it }`
  param and drives its `BasicTextField` from the helper. `MeasurementFieldInput`
  and the custom-field value input pass `sanitize = ::sanitizeMeasurementInput` and
  drop the now-redundant inline sanitize in `onValueChange`.
- The three `OutlinedTextField` sites bind the helper's `TextFieldValue` /
  handler. Name fields pass identity sanitize; sheet Value passes
  `sanitizeMeasurementInput`. The 30-char label cap stays (applied inside the
  edit handler before propagating).

### iOS note
`TextFieldValue` is CMP-safe; the helper is pure Kotlin. Verify on the iOS
simulator (cursor stability differs between platforms).

---

## 2 · Value-field punctuation

No functional change. The sheet Value field already shares
`sanitizeMeasurementInput` + `KeyboardType.Text`, so commas, decimals, and
multi-stage values ("58, 30, 45") already work identically to the form fields.
This item is satisfied by the #1 caret fix. The field keeps its current
`OutlinedTextField` styling (no restyle — confirmed with user).

---

## 3 · "Field added" confirmation

### Behavior (informational snackbar, no action — decision (b))
On **create only**, after the field persists and the sheet closes, show a snackbar:

- **Field shows on the current gender** (current gender ∈ `field.genders`, which
  includes every "Both" field): *"'Sleeve' added"*. The field (and any seeded
  value) is already visible on-screen.
- **Field is scoped to the other gender only**: *"'Sleeve' added to your Male
  measurements"* — informational. No View/switch action: a field scoped to the
  other gender is for the tailor's *other* customers, so silently flipping this
  measurement's gender would be surprising.

Edit and archive flows are unchanged (no new snackbar).

### Implementation
- **Event (one-shot, not state)** — add to `MeasurementFormEvent`:
  ```kotlin
  data class CustomFieldAdded(
      val label: String,
      val otherGenderLabel: UiText?, // null when the field shows on the current gender
  ) : MeasurementFormEvent
  ```
  One-shot event (via the existing `_events` channel) so it doesn't re-fire on
  recomposition — unlike the error path which lives in `state.errorMessage`.
- **ViewModel** (`saveCustomField`, success branch, `isCreate` only): compute
  `shownHere = current.gender == null || current.gender in field.genders`. If
  `shownHere`, emit `CustomFieldAdded(label, null)`; else the field's `genders` is
  the single opposite gender — emit `CustomFieldAdded(label, <that gender's UiText>)`.
  Emit after the `_state.update` that closes the sheet.
- **Screen** (`ObserveAsEvents` in the Root): resolve the message
  (`otherGenderLabel == null` → `custom_field_added`; else
  `custom_field_added_other_gender`) and `snackbarHostState.showSnackbar(msg)`.
  Reuses the existing `SnackbarHost` (`MeasurementFormScreen.kt:142/241`).

### New string resources (positional args only — per house rule)
- `custom_field_added` → `"'%1$s' added"`
- `custom_field_added_other_gender` → `"'%1$s' added to your %2$s measurements"`
- Gender display names for the `%2$s` slot — reuse existing gender labels if
  present, else add `gender_name_male` / `gender_name_female`.

---

## 4 · Step indicator

### Problem
Today a dot is `primary` when it is the current step **or** persistably filled,
otherwise `primary @ 30% alpha`. Current and completed collapse into the same
solid color, so you cannot tell which page you are on
(`SectionProgressRow`, `MeasurementFormScreen.kt:536`).

### Design (approved: ring/check states + section-name heading)
Three visually distinct dot states in `SectionProgressRow`:
- **Completed** (persistably filled, not current): solid primary dot with a small
  check glyph.
- **Current** (`index == currentIndex`): hollow ring (2.dp primary border) with a
  soft halo (an outer primary-alpha circle behind it). If the current step is also
  filled, keep the ring and show the check inside it.
- **Unvisited**: faint dot (`primary @ 30% alpha`) — unchanged.

"Filled" continues to use `isPersistableMeasurementValue` (unchanged predicate).
Dots stay tappable with their existing small target; content descriptions gain the
state ("completed" / "current" / "not started") for screen readers.

**Section-name heading** (approved placement: page heading, counter unchanged):
- On each section page (`page < sections.size`), render the section title as a
  bold heading above that page's fields. The "X of N" counter in
  `SectionProgressRow` stays as-is.
- On the custom page, the heading reads "Custom".
- Reuse the detail screen's title resolver: extract
  `MeasurementDetailScreen.kt`'s private `sectionTitle(titleKey)` (~558) into a
  shared `@Composable fun measurementSectionTitle(titleKey: String): String`
  (commonMain, e.g. `feature/measurement/presentation/`) resolving
  `section_upper_body` / `section_body_lengths` / `section_trouser`, with the same
  raw-key fallback. Both detail and form use it.

**Custom pill** (`CustomStepPill`) keeps its current active/hasData/locked design;
no change beyond what's above (avoids scope creep).

### Previews
Add/refresh `@Preview`s for `SectionProgressRow` covering: a completed + current +
unvisited mix, and the custom page active. Every Screen composable keeps its
`@Preview` (house rule).

---

## Testing

**Unit (commonTest — kotlin.test + Turbine + AssertK):**
- `sanitizedCaret(...)` pure function: caret preserved when inserting comma/decimal
  mid-string; caret unchanged when a disallowed char is rejected; caret clamps to
  sanitized length.
- `MeasurementFormViewModel.saveCustomField`: emits `CustomFieldAdded(label, null)`
  for same-gender and "Both" fields; emits `CustomFieldAdded(label, <gender>)` for
  an opposite-gender-only field; existing value-seeding behavior unchanged; no
  event on edit/archive.

**Manual smoke (Daniel = QA; per QA-smoke-tests rule), Android + iOS:**
1. In a value field with "58, 30, 45", place the caret between "30," and "45",
   type a digit — caret stays put (both platforms).
2. Add a custom field on the current gender → *"'X' added"*; field visible.
3. While editing a Female measurement, add a Male-only field → *"'X' added to your
   Male measurements"*; field does **not** appear here.
4. Swipe/step through sections — current step shows the ring, completed shows the
   check, and the section name heading updates ("Upper Body" → "Body Lengths" →
   "Trouser" → "Custom").

**Quality gates:** `./gradlew detekt`; iOS compile (`compileTestKotlinIosSimulatorArm64`)
before "done" — the form touches text input and previews.

## Risk / notes
- Purely presentation-layer; no domain/data/DTO/migration impact.
- Caret helper is the highest-risk change (input handling on two platforms) — keep
  it small, pure-testable, and verify on the iOS simulator.
- No new dependencies; no R8/proguard impact.

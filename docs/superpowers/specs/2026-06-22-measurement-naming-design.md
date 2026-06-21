# Measurement naming + smart add-flow — design

**Status:** Design approved (2026-06-22)
**Branch:** `feat/measurement-naming`
**Source:** tester pain point — a customer can hold several measurements that all
render as the identical generic label ("Women's Measurements" / "FEMALE · 2 fields"),
so they're impossible to tell apart, and tailors create accidental duplicates because
editing an existing one isn't discoverable.

## Goals
1. **Name every measurement** so they're distinguishable everywhere. Name is
   **mandatory** but the UX never blocks: the field is **pre-filled with an editable,
   distinct default** the tailor can keep or rename.
2. **Smart add-flow:** tapping "+" when the customer already has measurements offers a
   bottom sheet to *edit an existing one* or *create a new one* — killing the duplicate
   problem (editing already works via tapping a row; it just wasn't discoverable).

## Out of scope
Quick-rename on the row (renaming happens in the edit form). Name uniqueness is NOT
enforced (two "Agbada" allowed). No data backfill of existing records.

---

## 1. Data model

Add `name: String` (non-null, default `""` for legacy) to:

- **`core/domain/model/Measurement.kt`** — add `val name: String = ""`.
- **`core/data/dto/MeasurementDto.kt`** — add `val name: String = ""`. Existing
  Firestore docs lacking the field deserialize to `""` (handled by §4 fallback).
- **`core/data/mapper/MeasurementMapper.kt`** — `toMeasurement`: `name = name`;
  `toMeasurementDto`: `name = name`.

---

## 2. Measurement form — mandatory, pre-filled name

### State (`MeasurementFormState`)
Add:
```kotlin
    val name: String = "",
    /** True once the tailor edits the name; stops the gender-driven default from overwriting it. */
    val isNameUserEdited: Boolean = false,
    /** 1-based position used to build the distinct default name. */
    val nameOrdinal: Int = 1,
```
Extend `canSave` with `&& name.isNotBlank()` (mandatory).

### Actions (`MeasurementFormAction`)
- `data class OnNameChange(val name: String)` — user typed: sets `name`, `isNameUserEdited = true`.
- `data class OnNameDefaultApplied(val name: String)` — the Root applies a computed
  default: sets `name`, leaves `isNameUserEdited = false`.

### ViewModel (`MeasurementFormViewModel`)
- **Ordinal:** in `onStart`, for **create** mode (`measurementId == null`), load the
  customer's measurements once to size the default **before** the `onGenderChange(FEMALE)`
  call, so the first default the Root applies already uses the final ordinal (avoids a
  race where the effect fires with ordinal 1, fills the name, then can't refresh):
  ```kotlin
  val existing = measurementRepository.observeMeasurements(userId, customerId).first()
  val count = (existing as? Result.Success)?.data?.size ?: 0
  _state.update { it.copy(nameOrdinal = count + 1) }
  onGenderChange(CustomerGender.FEMALE)   // existing call — now runs AFTER nameOrdinal is set
  ```
  (Non-fatal on error — default ordinal stays 1. The Root effect's `state.name.isBlank()`
  guard means a loaded real name in edit mode is never overwritten.)
- **Edit / legacy:** in `loadMeasurement`, set `name = measurement.name` in the state
  copy. Also compute `nameOrdinal` = this measurement's 1-based position in
  `result.data.sortedBy { it.createdAt }` so a *legacy blank-name* edit still gets a
  sensible default. `isNameUserEdited` stays false; if the loaded name is non-blank the
  Root's default effect is a no-op (see below), so a real name is preserved.
- **Handlers:** `OnNameChange` → `name = action.name, isNameUserEdited = true`.
  `OnNameDefaultApplied` → `name = action.name` (don't touch `isNameUserEdited`).
- **save():** add `name = s.name.trim()` to the `Measurement(...)` constructor. The
  `canSave` entry-gate already blocks blank names.

### Root composable — compute the localized default (keeps strings in resources)
The VM can't read string resources, so the Root resolves the default name and feeds it
back. Only when the name hasn't been user-edited AND is currently blank (covers create
and legacy-edit; a loaded real name is non-blank so this won't fire):
```kotlin
LaunchedEffect(state.gender, state.nameOrdinal, state.isNameUserEdited, state.name.isBlank()) {
    if (!state.isNameUserEdited && state.name.isBlank() && state.gender != null) {
        val res = if (state.gender == CustomerGender.FEMALE)
            Res.string.measurement_name_default_female else Res.string.measurement_name_default_male
        onAction(MeasurementFormAction.OnNameDefaultApplied(getString(res, state.nameOrdinal)))
    }
}
```
When the tailor switches gender before typing, `state.name` is the previous default
(non-blank) so this won't auto-replace it — to keep the gender word in sync, the
`OnGenderChange` handler clears `name` back to `""` **only when `!isNameUserEdited`**, so
the effect recomputes with the new gender word. (Add that one line to `onGenderChange`.)

### Screen UI
A `Measurement name` `OutlinedTextField` at the **top of the form**, above the
`GenderSelector`:
- value `state.name`, `onValueChange = { onAction(OnNameChange(it)) }`.
- label `measurement_name_label`, placeholder `measurement_name_placeholder`.
- single line; `isError = state.name.isBlank()` with supporting text
  `measurement_name_required_hint` when blank.
- The form's scroll container already has `dismissKeyboardOnScroll` from the prior PR.

### Strings (`strings.xml`, positional args)
```xml
    <string name="measurement_name_label">Measurement name</string>
    <string name="measurement_name_placeholder">e.g. Wedding Agbada, School uniform</string>
    <string name="measurement_name_required_hint">Name your measurement</string>
    <string name="measurement_name_default_female">Women&apos;s measurement %1$d</string>
    <string name="measurement_name_default_male">Men&apos;s measurement %1$d</string>
```

---

## 3. Smart add-flow — "edit vs create" bottom sheet (customer detail)

### State (`CustomerDetailState`)
Add `val showAddMeasurementSheet: Boolean = false`.

### Actions (`CustomerDetailAction`)
Change `OnAddMeasurementClick` handling; add:
- `data object OnDismissAddMeasurementSheet`
- `data object OnCreateNewMeasurementClick` (the sheet's "Create new" button).

### ViewModel (`CustomerDetailViewModel`)
- `OnAddMeasurementClick`: **if `state.measurements.isNotEmpty()`** →
  `_state.update { it.copy(showAddMeasurementSheet = true) }`; **else** → existing
  behavior, send `NavigateToAddMeasurement(customerId)` (no needless sheet on an empty list).
- `OnCreateNewMeasurementClick`: clear the sheet, then send
  `NavigateToAddMeasurement(customerId)`.
- `OnDismissAddMeasurementSheet`: `showAddMeasurementSheet = false`.
- Sheet rows reuse the existing `OnMeasurementClick(measurement)` (clears the sheet +
  sends `NavigateToEditMeasurement`). Add `showAddMeasurementSheet = false` to that
  handler so the sheet closes on selection.

### Screen — `AddMeasurementSheet` (new composable, `customer/presentation/detail/components/`)
A `ModalBottomSheet` (`skipPartiallyExpanded = true`) shown when
`state.showAddMeasurementSheet`:
- Title `measurement_add_sheet_title` ("Add a measurement").
- A section listing each `state.measurements` as a tappable row: the measurement's
  **display name** (§4) as the title, subtitle = gender word · unit · date (reuse the
  detail row's subtitle formatting). Tap → `OnMeasurementClick(measurement)`.
- A prominent **"Create new measurement"** `Button` at the bottom →
  `OnCreateNewMeasurementClick`.
- `onDismissRequest` → `OnDismissAddMeasurementSheet`.

### Strings
```xml
    <string name="measurement_add_sheet_title">Add a measurement</string>
    <string name="measurement_add_sheet_existing">Edit an existing measurement</string>
    <string name="measurement_add_sheet_create_new">Create new measurement</string>
```

---

## 4. Display the name everywhere (with legacy fallback)

A single fallback rule wherever a measurement is labelled: **`name` if non-blank, else
the numbered gender default**. New measurements always have a stored name, so the
fallback only affects un-migrated legacy records (and they migrate the next time they're
edited and saved).

Provide a small Compose helper so the three sites stay consistent — e.g. in
`feature/measurement/presentation/`:
```kotlin
@Composable
fun measurementDisplayName(measurement: Measurement, position: Int): String =
    measurement.name.ifBlank {
        stringResource(
            if (measurement.gender == CustomerGender.FEMALE)
                Res.string.measurement_name_default_female else Res.string.measurement_name_default_male,
            position,
        )
    }
```
`position` = the measurement's 1-based index in the list being rendered.

Apply at:
- **Customer-detail row** (`CustomerDetailScreen.MeasurementListItem`, ~L912): title =
  `measurementDisplayName(measurement, index + 1)`; gender word moves into the subtitle
  (subtitle becomes `<genderWord> · <unit> · <date>`).
- **Order-form picker** (`OrderFormScreen` ~L923-927 + the dropdown item ~L950-954):
  show the display name in place of `"${gender.name} - ${fields.size} fields"`; keep
  `· N fields` as a secondary detail if it reads well. Pass `index + 1` as position.
- **The new `AddMeasurementSheet` rows** — same helper.

(Legacy fallback uses display-time position, which can renumber on delete; this only
affects un-named legacy records and self-resolves once they're edited+named. Stored
names never renumber.)

---

## 5. Navigation
No route change — `MeasurementFormRoute` already carries the optional `measurementId`,
so the sheet's edit rows reuse the existing edit path and "Create new" reuses the add path.

---

## 6. Testing

`MeasurementFormViewModel` is cleanly unit-testable in commonTest (existing
`MeasurementFormViewModelTest` + `FakeMeasurementRepository` capturing
`lastCreatedMeasurement`/`lastUpdatedMeasurement`):
- **name required blocks save:** blank `name` → `canSave == false`; save does nothing.
- **name persists on create:** set fields + name → `OnSaveClick` → captured created
  measurement has the trimmed `name`.
- **name persists on update (edit):** load → change name → save → captured updated
  measurement has the new `name`.
- **edit prefill:** loading a named measurement sets `state.name` to it; the default
  effect won't overwrite (name non-blank).
- **ordinal default:** with 2 existing measurements, a create-mode VM exposes
  `nameOrdinal == 3`.

Mapper test (`MeasurementMapper`): `name` round-trips; a legacy `MeasurementDto` (no
`name`) maps to `name == ""`.

The bottom-sheet branch (show vs navigate) and all UI are verified by manual smoke
(below). If `CustomerDetailViewModel` is constructible in commonTest, add a test that
`OnAddMeasurementClick` sets `showAddMeasurementSheet` when measurements exist and emits
`NavigateToAddMeasurement` when empty; if it has the Coil/PlatformContext constraint,
keep it to smoke.

## 7. Manual smoke test (device — Daniel is QA)
1. Customer with **no** measurements → "+" → goes straight to a blank form. The name is
   pre-filled "Women's measurement 1"; switch gender → "Men's measurement 1"; type a
   name → it sticks; clear it → Save disables with the hint.
2. Save it. Customer with **≥1** measurement → "+" → **bottom sheet**: lists the existing
   one(s) by name (tap → opens for edit) + "Create new measurement" (→ blank form,
   pre-filled "… measurement 2").
3. Detail list + order-form picker now show the names, so two measurements are
   distinguishable.
4. A **legacy** un-named measurement shows "Women's measurement N"; open it → name field
   pre-filled with that default → Save persists it as a real name.

## 8. Self-review checks
- Name mandatory (`canSave` gate) but never blocking (pre-filled distinct default). ✓
- Strings resolved in Compose (Root effect + display helper), positional `%1$d`. ✓
- Legacy records: fallback label + migrate-on-edit; no backfill. ✓
- Edit already wired; sheet only adds discoverability + a create path. ✓
- iOS test compile in every gate; form VM tests in commonTest. ✓

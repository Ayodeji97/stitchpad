# Measurement: drop auto-name + per-row rename/delete menu — design

**Status:** Design approved (2026-06-22)
**Branch:** `feat/measurement-naming` (folded into the open PR #207)
**Source:** follow-up to the measurement-naming feature. Two tester-driven refinements:
(1) the auto-pre-filled "Women's measurement N" name is unwanted — tailors should type
their own name from an empty field with a hint; (2) tailors should be able to rename a
measurement (and delete it) directly from its row via a 3-dot menu, without opening the
full measurement form.

## Goals
1. **Remove the auto-prefill.** The name stays REQUIRED, but the field opens empty with a
   placeholder hint; the tailor types the name. No more auto-filled "Women's measurement N".
2. **Per-row 3-dot menu** on the measurement row with **Rename** + **Delete**, mirroring
   the customer overflow already in this screen. Remove the swipe-to-delete gesture and the
   standalone trash icon. Tapping the row still opens the full measurement form.
3. **Rename dialog** — a small AlertDialog with a single name field, so renaming needs no
   trip into the full form.

## Out of scope
Changing the display fallback (`measurementDisplayName` keeps its legacy behavior). Name
uniqueness still not enforced.

---

## 1. Form — drop the auto-prefill (name stays required)

These were added in PR #207 Task 2 and are now REMOVED:
- `MeasurementFormScreen.kt`: the Root `LaunchedEffect` that called
  `OnNameDefaultApplied(getString(...))` — delete it and its `getString` + default-name
  `Res.string.*` imports (the `measurement_name_default_*` strings STAY — the display
  helper still uses them).
- `MeasurementFormAction.kt`: delete `OnNameDefaultApplied`.
- `MeasurementFormState.kt`: delete `isNameUserEdited` and `nameOrdinal`.
- `MeasurementFormViewModel.kt`:
  - `onAction`: delete the `OnNameDefaultApplied` arm; `OnNameChange` becomes just
    `_state.update { it.copy(name = action.name) }` (drop `isNameUserEdited = true`).
  - `onStart` create branch: delete the existing-measurement count load + `nameOrdinal`
    set; restore it to simply `onGenderChange(CustomerGender.FEMALE)`.
  - `onGenderChange`: delete the `name = if (current.isNameUserEdited) current.name else ""`
    line (gender change no longer touches the name).
  - `loadMeasurement`: delete the `position`/`nameOrdinal` computation; KEEP
    `name = measurement.name` (edit still pre-fills the existing name).

What STAYS:
- `MeasurementFormState.name`, the `canSave` gate (`name.isNotBlank()`), `OnNameChange`,
  and `save()` persisting `name = s.name.trim()`.
- The Screen's name `OutlinedTextField` (now simply an empty field on create) with its
  `label` (`measurement_name_label`), `placeholder` (`measurement_name_placeholder`), and
  `isError`/`supportingText` (`measurement_name_required_hint`) when blank.

Result: **create** → empty name field showing the hint, Save disabled until typed;
**edit** → pre-filled with the existing name.

---

## 2. Measurement row — 3-dot overflow menu

In `CustomerDetailScreen.kt`:
- **Remove the `SwipeToDismissBox` wrapper.** `SwipeableMeasurementItem` collapses to a
  plain row (rename it `MeasurementRow` or keep the name but drop the swipe). Tapping the
  row keeps its existing `onClick` → `OnMeasurementClick` → full edit form.
- **Replace the trash `IconButton`** inside `MeasurementListItem` with a `MoreVert`
  `IconButton` (content description `cd_measurement_overflow`) that opens a `DropdownMenu`
  anchored to it. The menu's open/close is local ephemeral UI state:
  `var menuExpanded by remember { mutableStateOf(false) }` (same pattern as the order
  picker's dropdown — allowed Compose-internal state).
- **Menu items:**
  - `DropdownMenuItem` "Rename" (`measurement_menu_rename`, leading `Icons.Default.Edit`)
    → `menuExpanded = false; onAction(OnRenameMeasurementClick(measurement))`.
  - `DropdownMenuItem` "Delete" (`measurement_menu_delete`, leading `Icons.Default.Delete`)
    → `menuExpanded = false; onAction(OnDeleteMeasurementClick(measurement))` (existing
    delete-confirmation flow).
- The locked/read-only path (`ReadOnlyMeasurementItem`) keeps NO actions (no menu) — it's
  inert as today.

Strings:
```xml
    <string name="cd_measurement_overflow">Measurement options</string>
    <string name="measurement_menu_rename">Rename</string>
    <string name="measurement_menu_delete">Delete</string>
```

---

## 3. Rename dialog

A new screen-level `AlertDialog` (consistent with the existing delete dialog), shown when
`state.measurementToRename != null`:
- Title `measurement_rename_dialog_title` ("Rename measurement").
- Body = an `OutlinedTextField`: value `state.renameDraft`, `onValueChange` →
  `OnRenameDraftChange`, label `measurement_name_label`, placeholder
  `measurement_name_placeholder`, single line, `isError = renameDraft.isBlank()`.
- Confirm button "Save" (`measurement_rename_save`), `enabled = renameDraft.isNotBlank()`
  → `OnConfirmRename`. Dismiss button "Cancel" (reuse an existing cancel string, e.g.
  `customer_delete_cancel`, or add `measurement_rename_cancel`).
- `onDismissRequest` → `OnDismissRenameDialog`.

Strings:
```xml
    <string name="measurement_rename_dialog_title">Rename measurement</string>
    <string name="measurement_rename_save">Save</string>
    <string name="measurement_rename_cancel">Cancel</string>
```

### State (`CustomerDetailState`)
```kotlin
    val measurementToRename: Measurement? = null,
    val renameDraft: String = "",
```

### Actions (`CustomerDetailAction`)
```kotlin
    data class OnRenameMeasurementClick(val measurement: Measurement) : CustomerDetailAction
    data class OnRenameDraftChange(val name: String) : CustomerDetailAction
    data object OnConfirmRename : CustomerDetailAction
    data object OnDismissRenameDialog : CustomerDetailAction
```

### ViewModel (`CustomerDetailViewModel`)
- `OnRenameMeasurementClick`: `_state.update { it.copy(measurementToRename = action.measurement, renameDraft = action.measurement.name) }` (prefills the current name; blank for legacy).
- `OnRenameDraftChange`: `_state.update { it.copy(renameDraft = action.name) }`.
- `OnDismissRenameDialog`: `_state.update { it.copy(measurementToRename = null, renameDraft = "") }`.
- `OnConfirmRename` → `renameMeasurement()`:
```kotlin
    private fun renameMeasurement() {
        val measurement = _state.value.measurementToRename ?: return
        val newName = _state.value.renameDraft.trim()
        if (newName.isBlank()) return
        val customerId = customerId ?: return
        _state.update { it.copy(measurementToRename = null, renameDraft = "") }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = measurementRepository.updateMeasurement(
                userId, customerId, measurement.copy(name = newName),
            )
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toMeasurementUiText()) }
            }
            // Success: the observed measurements list re-emits with the new name.
        }
    }
```
(`measurementRepository`, `authRepository`, `customerId`, `Result`, `toMeasurementUiText`
are already wired into this VM.)

---

## 4. Display fallback — unchanged

`measurementDisplayName(measurement, position)` keeps rendering the name, falling back to
"Women's measurement N" only for genuinely un-named legacy records. The
`measurement_name_default_female/male` strings stay (the helper uses them). Renaming a
legacy record persists a real name, ending its fallback.

---

## 5. Testing

`MeasurementFormViewModelTest`:
- DELETE the now-removed tests `ordinalDefault_isCountPlusOne` (and any test asserting the
  auto-default / `nameOrdinal`). KEEP `blankName_blocksSave`, `namePersists_onCreate`,
  `namePersists_onEdit`, `editPrefill_setsName`. Confirm a fresh create-mode VM now has
  `state.name == ""` (no auto-fill) — add/adjust a test for that.

`CustomerDetailViewModelTest` (cleanly constructible in commonTest):
- `renameClick_prefillsDraftWithCurrentName`: `OnRenameMeasurementClick(m)` →
  `measurementToRename == m`, `renameDraft == m.name`.
- `confirmRename_blankName_doesNothing`: draft "" → `OnConfirmRename` → no
  `updateMeasurement` call (assert via `FakeMeasurementRepository.lastUpdatedMeasurement == null`), dialog still arms? (it returns early; the dialog stays — acceptable, Save is disabled in UI anyway).
- `confirmRename_persistsTrimmedName_andClosesDialog`: seed a measurement, rename click,
  draft "  Wedding Agbada  " → `OnConfirmRename` → `lastUpdatedMeasurement!!.name == "Wedding Agbada"`; `measurementToRename == null`.
- `dismissRename_clearsState`: open → dismiss → `measurementToRename == null`, `renameDraft == ""`.

## 6. Manual smoke test (device — Daniel is QA)
1. New measurement → name field is EMPTY with the hint; Save disabled until typed; no
   "Women's measurement N" auto-fill.
2. Measurement row → no swipe, no trash icon; a **3-dot** menu with **Rename** + **Delete**.
3. Rename → dialog pre-filled with the current name → change it → Save → the row title
   updates (and persists; reopen to confirm). Blank name → Save disabled.
4. Delete (from the menu) → existing confirmation flow.
5. Tapping the row still opens the full measurement form.

## 7. Self-review checks
- Auto-prefill fully removed (Root effect, action, ordinal, gender-clear, isNameUserEdited);
  name stays required (empty + hint). ✓
- Swipe + trash removed; 3-dot menu (Rename + Delete); row-tap → full edit. ✓
- Rename persists via `updateMeasurement`; required-non-blank; observed list re-emits. ✓
- Display fallback + its strings untouched. ✓
- Folded into PR #207 (open); iOS test compile in every gate; VM tests in commonTest. ✓

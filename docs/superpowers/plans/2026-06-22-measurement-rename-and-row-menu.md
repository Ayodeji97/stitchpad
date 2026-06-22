# Measurement: drop auto-name + per-row rename/delete menu — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Remove the measurement name auto-prefill (name stays required, opens empty with a hint), and add a per-row 3-dot menu (Rename + Delete) with a quick rename dialog — replacing the swipe-to-delete + trash icon.

**Architecture:** Both changes are on `feat/measurement-naming` (folded into open PR #207). Task 1 strips the auto-default machinery from the measurement form. Task 2 swaps the row's trash/swipe for a `MoreVert` → `DropdownMenu` and adds an AlertDialog rename flow persisted via `updateMeasurement`. ViewModel logic is unit-tested; UI is manual-smoke-tested.

**Tech Stack:** KMP, Compose Multiplatform, Koin, GitLive Firestore, JUnit5 + Turbine.

**Spec:** `docs/superpowers/specs/2026-06-22-measurement-rename-and-row-menu-design.md`.

---

## Task 1: Remove the name auto-prefill (keep required)

**Files:**
- `feature/measurement/presentation/form/MeasurementFormState.kt`
- `feature/measurement/presentation/form/MeasurementFormAction.kt`
- `feature/measurement/presentation/form/MeasurementFormViewModel.kt`
- `feature/measurement/presentation/form/MeasurementFormScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

READ each file first — these are surgical removals of code added in PR #207's form commit (e0d657e3).

- [ ] **Step 1: State** — in `MeasurementFormState`, DELETE `isNameUserEdited` and `nameOrdinal`. KEEP `name`, and keep `canSave`'s `name.isNotBlank()` clause.

- [ ] **Step 2: Action** — in `MeasurementFormAction`, DELETE `OnNameDefaultApplied`. KEEP `OnNameChange`.

- [ ] **Step 3: ViewModel** — in `MeasurementFormViewModel`:
  - In `onAction`: DELETE the `is MeasurementFormAction.OnNameDefaultApplied -> ...` arm. Change `OnNameChange` to just `_state.update { it.copy(name = action.name) }` (drop `isNameUserEdited = true`).
  - In `onStart` create branch: DELETE the existing-measurement count load and the `_state.update { it.copy(nameOrdinal = count + 1) }`; the create branch becomes simply `onGenderChange(CustomerGender.FEMALE)` (as it was before PR #207). Remove the now-unused local `existing`/`count` and any import only they used (e.g. keep `first`/`Result` only if still used elsewhere — check).
  - In `onGenderChange`: DELETE the `name = if (current.isNameUserEdited) current.name else ""` line from the `current.copy(...)` (gender change no longer touches name).
  - In `loadMeasurement`: DELETE the `position`/`nameOrdinal` computation; KEEP `name = measurement.name` in the state copy.

- [ ] **Step 4: Screen** — in `MeasurementFormScreen.kt`, DELETE the Root `LaunchedEffect` that dispatched `OnNameDefaultApplied(getString(...))`, and remove its now-unused imports: `getString`, `Res.string.measurement_name_default_female`, `Res.string.measurement_name_default_male`, and `LaunchedEffect`/`CustomerGender` IF nothing else in the file uses them (grep before removing each). KEEP the name `OutlinedTextField` exactly as-is (label/placeholder/isError/supportingText) — it now just renders an empty field on create.
  NOTE: do NOT remove the `measurement_name_default_female/male` STRINGS from `strings.xml` — the display helper (`measurementDisplayName`) still uses them.

- [ ] **Step 5: Tests** — in `MeasurementFormViewModelTest.kt`:
  - DELETE `ordinalDefault_isCountPlusOne` and any test referencing `nameOrdinal` / the auto-default.
  - KEEP `blankName_blocksSave`, `namePersists_onCreate`, `namePersists_onEdit`, `editPrefill_setsName`.
  - ADD `create_startsWithEmptyName`: a fresh create-mode VM has `state.value.name == ""` (no auto-fill).

- [ ] **Step 6: Verify**
  Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Measurement*' -q`. All green. (Watch detekt for newly-unused imports.)

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(measurement): remove name auto-prefill — empty required field with hint"
```

---

## Task 2: Per-row 3-dot menu (Rename + Delete) + rename dialog

**Files:**
- `feature/customer/presentation/detail/CustomerDetailState.kt`
- `feature/customer/presentation/detail/CustomerDetailAction.kt`
- `feature/customer/presentation/detail/CustomerDetailViewModel.kt`
- `feature/customer/presentation/detail/CustomerDetailScreen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailViewModelTest.kt`

READ the current `SwipeableMeasurementItem` / `MeasurementListItem` (the swipe wrapper + trash icon), the existing customer overflow `DropdownMenu` (to mirror), and the existing delete `AlertDialog` (to mirror for rename).

- [ ] **Step 1: State** — in `CustomerDetailState`, add:
```kotlin
    val measurementToRename: Measurement? = null,
    val renameDraft: String = "",
```

- [ ] **Step 2: Actions** — in `CustomerDetailAction`, add:
```kotlin
    data class OnRenameMeasurementClick(val measurement: Measurement) : CustomerDetailAction
    data class OnRenameDraftChange(val name: String) : CustomerDetailAction
    data object OnConfirmRename : CustomerDetailAction
    data object OnDismissRenameDialog : CustomerDetailAction
```

- [ ] **Step 3: ViewModel handlers** — in `CustomerDetailViewModel.onAction`, add:
```kotlin
            is CustomerDetailAction.OnRenameMeasurementClick -> {
                _state.update {
                    it.copy(measurementToRename = action.measurement, renameDraft = action.measurement.name)
                }
            }
            is CustomerDetailAction.OnRenameDraftChange -> {
                _state.update { it.copy(renameDraft = action.name) }
            }
            CustomerDetailAction.OnConfirmRename -> renameMeasurement()
            CustomerDetailAction.OnDismissRenameDialog -> {
                _state.update { it.copy(measurementToRename = null, renameDraft = "") }
            }
```
And the private function (mirror the existing `deleteMeasurement`/persistence idiom in this VM):
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
(`measurementRepository`, `authRepository`, the `customerId` field, `Result`, and `toMeasurementUiText` are already imported/wired in this VM — confirm and add imports only if missing.)

- [ ] **Step 4: Strings** — add to `strings.xml`:
```xml
    <string name="cd_measurement_overflow">Measurement options</string>
    <string name="measurement_menu_rename">Rename</string>
    <string name="measurement_menu_delete">Delete</string>
    <string name="measurement_rename_dialog_title">Rename measurement</string>
    <string name="measurement_rename_save">Save</string>
    <string name="measurement_rename_cancel">Cancel</string>
```

- [ ] **Step 5: Row — remove swipe + trash, add 3-dot menu** — in `CustomerDetailScreen.kt`:
  - In `SwipeableMeasurementItem`: REMOVE the `SwipeToDismissBox` wrapper (and its `rememberSwipeToDismissBoxState` + the swipe background with the trash icon). The active row becomes just the `MeasurementListItem` with its `onClick`/`onDelete` callbacks. (Drop now-unused `SwipeToDismissBox`/`SwipeToDismissBoxValue`/`rememberSwipeToDismissBoxState` imports if no other usage in the file — grep.) You may rename the function `MeasurementRow` for clarity, or keep the name.
  - Thread a rename callback down: `SwipeableMeasurementItem`/`MeasurementListItem` get an `onRename: () -> Unit` param. At the `itemsIndexed` call site, pass `onRename = { onAction(CustomerDetailAction.OnRenameMeasurementClick(measurement)) }` (alongside the existing `onClick`/`onDelete`).
  - In `MeasurementListItem`: REPLACE the trailing trash `IconButton` with:
```kotlin
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.cd_measurement_overflow),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.measurement_menu_rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.measurement_menu_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete?.invoke() },
                )
            }
        }
```
  (Imports `MoreVert`, `Edit`, `DropdownMenu`, `DropdownMenuItem`, `remember`, `mutableStateOf`, `getValue`, `setValue`, `Box` are already present in this file from the customer overflow — confirm. Do NOT use `it` as a lambda param anywhere; keep imports lexicographically ordered — detekt enforces both.)
  - `ReadOnlyMeasurementItem` (locked path): leave inert — pass a no-op/omit `onRename`; it renders no menu (keep its current no-action behavior).

- [ ] **Step 6: Rename dialog** — in `CustomerDetailScreen.kt`, where the delete `AlertDialog` is hosted, add (mirroring its structure):
```kotlin
    if (state.measurementToRename != null) {
        AlertDialog(
            onDismissRequest = { onAction(CustomerDetailAction.OnDismissRenameDialog) },
            title = { Text(stringResource(Res.string.measurement_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = { onAction(CustomerDetailAction.OnRenameDraftChange(it)) },
                    label = { Text(stringResource(Res.string.measurement_name_label)) },
                    placeholder = { Text(stringResource(Res.string.measurement_name_placeholder)) },
                    singleLine = true,
                    isError = state.renameDraft.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(CustomerDetailAction.OnConfirmRename) },
                    enabled = state.renameDraft.isNotBlank(),
                ) { Text(stringResource(Res.string.measurement_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CustomerDetailAction.OnDismissRenameDialog) }) {
                    Text(stringResource(Res.string.measurement_rename_cancel))
                }
            },
        )
    }
```
Add imports `OutlinedTextField`, `measurement_name_label`, `measurement_name_placeholder`, and the three `measurement_rename_*` strings (label/placeholder already exist from PR #207).

- [ ] **Step 7: Tests** — in `CustomerDetailViewModelTest.kt`, add:
  - `renameClick_prefillsDraftWithCurrentName`: seed a named measurement; `OnRenameMeasurementClick(m)` → `state.measurementToRename == m`, `state.renameDraft == m.name`.
  - `confirmRename_persistsTrimmedName_andClosesDialog`: open rename, `OnRenameDraftChange("  Wedding Agbada  ")`, `OnConfirmRename` → `FakeMeasurementRepository.lastUpdatedMeasurement!!.name == "Wedding Agbada"`; `state.measurementToRename == null`.
  - `confirmRename_blankDraft_noUpdate`: open rename, draft "" → `OnConfirmRename` → `lastUpdatedMeasurement == null`.
  - `dismissRename_clearsState`: open → `OnDismissRenameDialog` → `measurementToRename == null`, `renameDraft == ""`.
  (Check `FakeMeasurementRepository` exposes `lastUpdatedMeasurement` — it does, per PR #207. Seed measurements via `measurementRepository.measurementsList` + `customersList` + auth as the existing tests do.)

- [ ] **Step 8: Verify**
  Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Customer*' --tests '*Measurement*' -q`. All green. Watch detekt for ImportOrdering / ExplicitItLambdaParameter / unused imports (swipe).

- [ ] **Step 9: Commit**
```bash
git add -A
git commit -m "feat(measurement): per-row 3-dot menu (rename + delete) on customer detail"
```

---

## Manual smoke test (device — Daniel is QA)
1. New measurement → name field EMPTY with hint; Save disabled until typed; no auto-fill.
2. Measurement row → no swipe, no trash; a 3-dot menu with Rename + Delete.
3. Rename → dialog pre-filled with current name → edit → Save → row title updates + persists (reopen to confirm); blank → Save disabled.
4. Delete from the menu → existing confirmation flow.
5. Tap the row → full measurement form opens (unchanged).

## Self-review notes
- Task 1 strips auto-prefill (state/action/VM/screen + tests); name stays required (empty + hint). ✓
- Task 2 removes swipe + trash, adds 3-dot menu (Rename + Delete) + rename AlertDialog persisted via updateMeasurement. ✓
- Display helper + default strings untouched. ✓
- iOS test compile in every gate; VM tests in commonTest; folded into PR #207. ✓

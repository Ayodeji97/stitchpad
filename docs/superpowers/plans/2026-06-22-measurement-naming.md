# Measurement Naming + Smart Add-Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Every measurement gets a mandatory-but-pre-filled name so they're distinguishable everywhere, and tapping "+" on a customer with existing measurements offers an "edit existing vs create new" bottom sheet.

**Architecture:** Add `name: String` through domain/DTO/mapper. The measurement form gets a required name field pre-filled with a distinct editable default ("Women's measurement N"); the localized default is resolved in the Root composable (VMs can't read resources) and fed back via an action. A shared `measurementDisplayName` helper renders name-or-fallback at every site. Customer-detail "+" branches to a bottom sheet when measurements exist. ViewModel logic is unit-tested (the measurement form VM is cleanly testable in commonTest); UI is manual-smoke-tested.

**Tech Stack:** KMP, Compose Multiplatform, Koin, GitLive Firestore, JUnit5 + Turbine + AssertK.

**Spec:** `docs/superpowers/specs/2026-06-22-measurement-naming-design.md`.

---

## Task 1: Data model — `name` through domain, DTO, mapper

**Files:**
- Modify: `core/domain/model/Measurement.kt`
- Modify: `core/data/dto/MeasurementDto.kt`
- Modify: `core/data/mapper/MeasurementMapper.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/MeasurementMapperTest.kt` (create if absent)

- [ ] **Step 1: Add `name` to the domain model**

`core/domain/model/Measurement.kt` — add `val name: String = ""` (default keeps every existing `Measurement(...)` call site compiling):
```kotlin
data class Measurement(
    val id: String,
    val customerId: String,
    val gender: CustomerGender,
    val name: String = "",
    val fields: Map<String, Double>,
    val unit: MeasurementUnit,
    val notes: String?,
    val dateTaken: Long,
    val createdAt: Long
)
```

- [ ] **Step 2: Add `name` to the DTO**

`core/data/dto/MeasurementDto.kt` — add `val name: String = ""` (default makes legacy Firestore docs without the field deserialize cleanly):
```kotlin
    val id: String = "",
    val gender: String = "FEMALE",
    val name: String = "",
    // ...rest unchanged
```

- [ ] **Step 3: Map `name` both directions**

`core/data/mapper/MeasurementMapper.kt`:
- In `toMeasurement(...)`: add `name = name,` (DTO's `name`).
- In `toMeasurementDto()`: add `name = name,` (domain's `name`).

- [ ] **Step 4: Write the mapper test**

Open `MeasurementMapperTest.kt` (create it next to `CustomMeasurementFieldMapperTest.kt` if it doesn't exist; mirror that file's package + imports + `kotlin.test` + assertk style). Add:
```kotlin
    @Test
    fun name_roundTrips_throughDto() {
        val m = Measurement(
            id = "m1", customerId = "c1", gender = CustomerGender.FEMALE,
            name = "Wedding Agbada", fields = mapOf("bust" to 36.0),
            unit = MeasurementUnit.INCHES, notes = null, dateTaken = 1L, createdAt = 1L,
        )
        assertThat(m.toMeasurementDto().toMeasurement("c1").name).isEqualTo("Wedding Agbada")
    }

    @Test
    fun legacyDto_withoutName_mapsToEmptyName() {
        val dto = MeasurementDto(id = "m1", gender = "FEMALE", fields = mapOf("bust" to 36.0))
        assertThat(dto.toMeasurement("c1").name).isEqualTo("")
    }
```
(Adjust `Measurement(...)`/`MeasurementDto(...)` arg names to the actual signatures.)

- [ ] **Step 5: Verify + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*MeasurementMapper*' -q`. Both green.
```bash
git add -A
git commit -m "feat(measurement): add name field to model, DTO, mapper"
```

---

## Task 2: Measurement form — mandatory, pre-filled name

**Files:**
- Modify: `feature/measurement/presentation/form/MeasurementFormState.kt`
- Modify: `feature/measurement/presentation/form/MeasurementFormAction.kt`
- Modify: `feature/measurement/presentation/form/MeasurementFormViewModel.kt`
- Modify: `feature/measurement/presentation/form/MeasurementFormScreen.kt` (the Root composable + the form UI)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

- [ ] **Step 1: State**

`MeasurementFormState` — add fields + extend `canSave`:
```kotlin
    val name: String = "",
    /** True once the tailor edits the name; stops the gender-driven default from overwriting it. */
    val isNameUserEdited: Boolean = false,
    /** 1-based position used to build the distinct default name. */
    val nameOrdinal: Int = 1,
```
In `canSave`, add the name gate:
```kotlin
    val canSave: Boolean
        get() = gender != null &&
            name.isNotBlank() &&
            fields.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 } &&
            !isLoading
```

- [ ] **Step 2: Actions**

`MeasurementFormAction` — add:
```kotlin
    data class OnNameChange(val name: String) : MeasurementFormAction
    data class OnNameDefaultApplied(val name: String) : MeasurementFormAction
```

- [ ] **Step 3: ViewModel — handlers**

In `onAction`, add:
```kotlin
            is MeasurementFormAction.OnNameChange ->
                _state.update { it.copy(name = action.name, isNameUserEdited = true) }
            is MeasurementFormAction.OnNameDefaultApplied ->
                _state.update { it.copy(name = action.name) }
```

- [ ] **Step 4: ViewModel — ordinal load before gender init (create mode)**

In `onStart`, the create branch currently calls `onGenderChange(CustomerGender.FEMALE)`. Load the count and set `nameOrdinal` FIRST so the Root's first default uses the final ordinal (avoids the fill-at-1-then-can't-refresh race):
```kotlin
                if (measurementId != null) {
                    loadMeasurement(measurementId)
                } else {
                    val existing = measurementRepository.observeMeasurements(userId, customerId).first()
                    val count = (existing as? Result.Success)?.data?.size ?: 0
                    _state.update { it.copy(nameOrdinal = count + 1) }
                    onGenderChange(CustomerGender.FEMALE)
                }
```
(`userId` is resolved just above in `onStart`; reuse it. `first()` + `Result` are already imported.)

- [ ] **Step 5: ViewModel — gender change clears the auto-name; edit prefill sets name + position**

In `onGenderChange(...)`, in the `_state.update { current -> ... current.copy(...) }`, clear the name back to blank ONLY when the tailor hasn't typed one, so the Root recomputes the default with the new gender word:
```kotlin
            current.copy(
                gender = gender,
                name = if (current.isNameUserEdited) current.name else "",
                // ...rest unchanged (sections, currentSectionIndex, fields, customFields)
            )
```
In `loadMeasurement(...)`, in the `_state.update { it.copy(...) }` that populates the loaded measurement, add the name + its position so a legacy blank-name edit still gets a sensible default:
```kotlin
                        val position = result.data.sortedBy { it.createdAt }
                            .indexOfFirst { it.id == id } + 1
                        _state.update {
                            it.copy(
                                gender = measurement.gender,
                                name = measurement.name,
                                nameOrdinal = if (position > 0) position else 1,
                                // ...rest unchanged
                            )
                        }
```

- [ ] **Step 6: ViewModel — persist name in save()**

In `save()`, add `name = s.name.trim()` to the `Measurement(...)` constructor:
```kotlin
            val measurement = Measurement(
                id = effectiveId,
                customerId = customerId,
                gender = gender,
                name = s.name.trim(),
                fields = parsedFields,
                // ...rest unchanged
            )
```

- [ ] **Step 7: Strings**

Add to `strings.xml` (positional args, `&apos;` not `\'`):
```xml
    <string name="measurement_name_label">Measurement name</string>
    <string name="measurement_name_placeholder">e.g. Wedding Agbada, School uniform</string>
    <string name="measurement_name_required_hint">Name your measurement</string>
    <string name="measurement_name_default_female">Women&apos;s measurement %1$d</string>
    <string name="measurement_name_default_male">Men&apos;s measurement %1$d</string>
```

- [ ] **Step 8: Root composable — apply the localized default**

In `MeasurementFormScreen.kt`'s Root composable (the one with the ViewModel + state collection), add a `LaunchedEffect` that resolves the default name and feeds it back (uses the suspend `getString`):
```kotlin
    LaunchedEffect(state.gender, state.nameOrdinal, state.isNameUserEdited, state.name.isBlank()) {
        if (!state.isNameUserEdited && state.name.isBlank() && state.gender != null) {
            val res = if (state.gender == CustomerGender.FEMALE) {
                Res.string.measurement_name_default_female
            } else {
                Res.string.measurement_name_default_male
            }
            onAction(MeasurementFormAction.OnNameDefaultApplied(getString(res, state.nameOrdinal)))
        }
    }
```
Imports: `androidx.compose.runtime.LaunchedEffect`, `org.jetbrains.compose.resources.getString`, the two `Res.string.*`, `CustomerGender`. (Applying the default leaves `isNameUserEdited` false and makes `name` non-blank, so the effect's keys don't retrigger it — no loop. A loaded real name in edit mode is non-blank, so the `state.name.isBlank()` guard prevents overwrite.)

- [ ] **Step 9: Screen — the name field**

In the stateless form `Screen` composable, add a `Measurement name` `OutlinedTextField` at the TOP of the form, above the `GenderSelector` (~line 246):
```kotlin
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(MeasurementFormAction.OnNameChange(it)) },
            label = { Text(stringResource(Res.string.measurement_name_label)) },
            placeholder = { Text(stringResource(Res.string.measurement_name_placeholder)) },
            singleLine = true,
            isError = state.name.isBlank(),
            supportingText = if (state.name.isBlank()) {
                { Text(stringResource(Res.string.measurement_name_required_hint)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
```
(Match the file's existing import set + spacing conventions; place inside the same scroll Column as the other fields.)

- [ ] **Step 10: Tests**

In `MeasurementFormViewModelTest.kt` (build the VM as the existing tests do — `SavedStateHandle` with `customerId`/`measurementId`, `FakeMeasurementRepository`, etc.), add:
- `blankName_blocksSave`: create-mode VM, set a positive field, then `OnNameChange("")` → `state.canSave == false`; `OnSaveClick` leaves `FakeMeasurementRepository.lastCreatedMeasurement == null`.
- `namePersists_onCreate`: set a positive field + `OnNameChange("Wedding Agbada")` → `OnSaveClick` → `lastCreatedMeasurement!!.name == "Wedding Agbada"`.
- `namePersists_onEdit`: seed the fake with a named measurement, build edit-mode VM (`measurementId` set), `OnNameChange("Renamed")` → `OnSaveClick` → `lastUpdatedMeasurement!!.name == "Renamed"`.
- `editPrefill_setsName`: seed a measurement named "X", edit-mode VM → `state.name == "X"`.
- `ordinalDefault_isCountPlusOne`: seed the fake with 2 existing measurements for the customer, create-mode VM → `state.nameOrdinal == 3`.
Run FIRST (fail), then implement above, then PASS. (The default-name string itself is applied in the Root effect, not the VM, so VM tests assert `nameOrdinal`/`name` mechanics, not the localized string.)

- [ ] **Step 11: Verify + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Measurement*' -q`. Green.
```bash
git add -A
git commit -m "feat(measurement): mandatory pre-filled name on the measurement form"
```

---

## Task 3: Display the name everywhere (with fallback)

**Files:**
- Create: `feature/measurement/presentation/MeasurementDisplayName.kt`
- Modify: `feature/customer/presentation/detail/CustomerDetailScreen.kt` (~L912 `MeasurementListItem`, and its list `items(...)` call ~L305 to pass an index)
- Modify: `feature/order/presentation/form/OrderFormScreen.kt` (~L923-927 label + ~L950-954 dropdown item)
- Modify: `strings.xml` (short gender words for the subtitle)

- [ ] **Step 1: The shared helper**

Create `MeasurementDisplayName.kt`:
```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.measurement_name_default_female
import stitchpad.composeapp.generated.resources.measurement_name_default_male

/** Display label for a measurement: its name, or a distinct numbered gender default for legacy un-named records. */
@Composable
fun measurementDisplayName(measurement: Measurement, position: Int): String =
    measurement.name.ifBlank {
        stringResource(
            if (measurement.gender == CustomerGender.FEMALE) {
                Res.string.measurement_name_default_female
            } else {
                Res.string.measurement_name_default_male
            },
            position,
        )
    }
```

- [ ] **Step 2: Short gender strings (subtitle)**

Add to `strings.xml`:
```xml
    <string name="measurement_gender_women">Women&apos;s</string>
    <string name="measurement_gender_men">Men&apos;s</string>
```

- [ ] **Step 3: Customer-detail row**

In `CustomerDetailScreen.kt`: the list `items(items = state.measurements, key = { it.id })` (~L305) → switch to `itemsIndexed(...)` so the row gets its index. Pass `position = index + 1` down through `SwipeableMeasurementItem`/`ReadOnlyMeasurementItem` to `MeasurementListItem`. In `MeasurementListItem` (~L912), replace the gender-profile title:
```kotlin
        val title = measurementDisplayName(measurement, position)
```
and move the gender word into the subtitle: build it as
`"${genderWord} · ${unit} · ${date}"` where `genderWord = stringResource(if FEMALE measurement_gender_women else measurement_gender_men)`. Keep the existing date/unit formatting; just prepend the gender word. Add the `itemsIndexed` + `measurementDisplayName` imports.

- [ ] **Step 4: Order-form picker**

In `OrderFormScreen.kt`, the selected-label (~L923-927) and the dropdown items (~L950-954) currently use `"${measurement.gender.name} - ${measurement.fields.size} fields"`. Replace with the display name; the dropdown maps over the customer's measurements, so use the loop index for `position`:
```kotlin
        // selected label:
        val measurementLabel = if (selectedMeasurement != null) {
            measurementDisplayName(selectedMeasurement, /* position */ measurements.indexOf(selectedMeasurement) + 1)
        } else {
            stringResource(Res.string.order_form_no_measurement)
        }
        // dropdown item (inside a forEachIndexed over the measurements list):
        Text(measurementDisplayName(measurement, index + 1))
```
(Find the actual measurements list variable in scope; convert its `forEach`/`items` to indexed so `position` is available. Keep `· N fields` appended only if it reads well — optional.)

- [ ] **Step 5: Verify + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Order*' --tests '*Customer*' -q`. Green.
```bash
git add -A
git commit -m "feat(measurement): show measurement name across detail + order picker"
```

---

## Task 4: Smart add-flow — "edit vs create" bottom sheet

**Files:**
- Modify: `feature/customer/presentation/detail/CustomerDetailState.kt`
- Modify: `feature/customer/presentation/detail/CustomerDetailAction.kt`
- Modify: `feature/customer/presentation/detail/CustomerDetailViewModel.kt`
- Create: `feature/customer/presentation/detail/components/AddMeasurementSheet.kt`
- Modify: `feature/customer/presentation/detail/CustomerDetailScreen.kt` (host the sheet)
- Modify: `strings.xml`

- [ ] **Step 1: State + actions**

`CustomerDetailState` — add `val showAddMeasurementSheet: Boolean = false`.
`CustomerDetailAction` — add:
```kotlin
    data object OnDismissAddMeasurementSheet : CustomerDetailAction
    data object OnCreateNewMeasurementClick : CustomerDetailAction
```

- [ ] **Step 2: ViewModel branch**

In `CustomerDetailViewModel.onAction`:
- Change `OnAddMeasurementClick`: if `_state.value.measurements.isNotEmpty()` →
  `_state.update { it.copy(showAddMeasurementSheet = true) }`; else keep the current
  behavior (the `withCustomerId { ... send(NavigateToAddMeasurement(it)) }`).
- Add `OnCreateNewMeasurementClick`: `_state.update { it.copy(showAddMeasurementSheet = false) }` then the same `withCustomerId { ... NavigateToAddMeasurement(it) }`.
- Add `OnDismissAddMeasurementSheet`: `_state.update { it.copy(showAddMeasurementSheet = false) }`.
- In the existing `OnMeasurementClick` handler, also clear the sheet: add `_state.update { it.copy(showAddMeasurementSheet = false) }` before sending `NavigateToEditMeasurement` (so picking a row from the sheet closes it).

- [ ] **Step 3: Strings**

```xml
    <string name="measurement_add_sheet_title">Add a measurement</string>
    <string name="measurement_add_sheet_existing">Edit an existing measurement</string>
    <string name="measurement_add_sheet_create_new">Create new measurement</string>
```

- [ ] **Step 4: The sheet composable**

Create `AddMeasurementSheet.kt`: a `ModalBottomSheet` (`sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `onDismissRequest = onDismiss`). Params:
```kotlin
fun AddMeasurementSheet(
    measurements: List<Measurement>,
    onEditMeasurement: (Measurement) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
)
```
Content: a title (`measurement_add_sheet_title`), a small section label (`measurement_add_sheet_existing`), then each `measurements` rendered as a tappable row — title `measurementDisplayName(m, index + 1)`, subtitle gender · unit · date (reuse the detail row's subtitle helper or inline it) — `onClick = { onEditMeasurement(m) }`; then a prominent `Button(onClick = onCreateNew)` with `measurement_add_sheet_create_new`. Use `itemsIndexed`/`forEachIndexed` for `position`. Apply `LoadingDots`-free simple rows (no images). Match design-system spacing.

- [ ] **Step 5: Host the sheet**

In `CustomerDetailScreen.kt`, where other sheets/dialogs are hosted, add:
```kotlin
    if (state.showAddMeasurementSheet) {
        AddMeasurementSheet(
            measurements = state.measurements,
            onEditMeasurement = { onAction(CustomerDetailAction.OnMeasurementClick(it)) },
            onCreateNew = { onAction(CustomerDetailAction.OnCreateNewMeasurementClick) },
            onDismiss = { onAction(CustomerDetailAction.OnDismissAddMeasurementSheet) },
        )
    }
```

- [ ] **Step 6: Verify + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Customer*' --tests '*Measurement*' -q`. Green. (If `CustomerDetailViewModel` is constructible in commonTest, add a test: `OnAddMeasurementClick` sets `showAddMeasurementSheet` when measurements exist, navigates when empty. If not, note it and rely on smoke.)
```bash
git add -A
git commit -m "feat(measurement): edit-vs-create bottom sheet on customer detail (smart add-flow)"
```

---

## Manual smoke test (device — Daniel is QA)
1. Customer with NO measurements → "+" → straight to a blank form; name pre-filled "Women's measurement 1"; switch to Men's → "Men's measurement 1"; type a name → sticks; clear it → Save disabled + hint.
2. Save it. Same customer → "+" → **bottom sheet**: lists the existing measurement by name (tap → opens for edit) + "Create new measurement" (→ blank form, pre-filled "… measurement 2").
3. Detail list + order-form picker show the names → two measurements are now distinguishable.
4. A legacy un-named measurement shows "Women's measurement N"; open it → name pre-filled → Save persists a real name.

## Self-review notes
- Data (Task 1) → form name (Task 2) → display (Task 3) → sheet (Task 4); Task 4 uses Task 3's helper. ✓
- Name mandatory (`canSave`) but pre-filled distinct default; resolved in Compose (Root effect + helper), positional `%1$d`, `&apos;`. ✓
- Ordinal loaded before gender init (no race); edit/legacy prefill + position. ✓
- Legacy fallback + migrate-on-edit; no backfill. ✓
- iOS test compile in every gate; form VM + mapper tests in commonTest. ✓

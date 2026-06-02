# Add-Measurement CTA & Control UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the "add measurements next" choice on the customer form deliberate (prominent card), adapt the customer-form CTA copy to reflect that it both saves the customer and continues, and make the measurement screen aware when it was reached from the create flow (CTA reads "Save" + a "Skip for now" escape hatch).

**Architecture:** Pure presentation-layer change. One new nav-route boolean arg (`MeasurementFormRoute.fromCustomerCreation`) threaded into `MeasurementFormState` via `SavedStateHandle`, a new skip action/event pair, and Compose-only swaps on two screens. No repository, domain, or data-model changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MVI (State/Action/Event + ViewModel), Koin, `compose.resources` strings, kotlin.test + UnconfinedTestDispatcher for ViewModel tests.

---

## File Structure

- `composeApp/src/commonMain/composeResources/values/strings.xml` — 4 new strings.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt` — add `fromCustomerCreation` to `MeasurementFormRoute`.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` — pass `fromCustomerCreation = true` on the create-flow push; wire the skip callback.
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt` — add `fromCustomerCreation` field.
- `.../measurement/presentation/form/MeasurementFormEvent.kt` — add `SkipMeasurements`.
- `.../measurement/presentation/form/MeasurementFormAction.kt` — add `OnSkipClick`.
- `.../measurement/presentation/form/MeasurementFormViewModel.kt` — read arg, handle `OnSkipClick`.
- `.../measurement/presentation/form/MeasurementFormScreen.kt` — adaptive CTA label + skip button + Root wiring + previews.
- `.../customer/presentation/form/CustomerFormScreen.kt` — prominent card replaces checkbox row; adaptive Save button.
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt` — 2 new tests.

Naming note: `customer_form_add_measurements_next` (existing, "Add measurements next") is reused as the card **title**. All other copy is new. Apostrophes use `&apos;` (never `\'`) per project convention.

---

### Task 1: Add string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (near lines 234–235 and 328)

- [ ] **Step 1: Add the customer-form strings**

Find (line 234–235):

```xml
    <string name="customer_form_add_measurements_next">Add measurements next</string>
    <string name="customer_form_save_button">Save Customer</string>
```

Replace with:

```xml
    <string name="customer_form_add_measurements_next">Add measurements next</string>
    <string name="customer_form_add_measurements_helper">We&apos;ll save them, then take you to their measurements.</string>
    <string name="customer_form_save_button">Save Customer</string>
    <string name="customer_form_save_and_measure_button">Save &amp; Add Measurements</string>
```

- [ ] **Step 2: Add the measurement-screen strings**

Find (line 328):

```xml
    <string name="measurement_save_button">Save Measurements</string>
```

Replace with:

```xml
    <string name="measurement_save_button">Save Measurements</string>
    <string name="measurement_create_flow_save_button">Save</string>
    <string name="measurement_skip_for_now">Skip for now</string>
```

- [ ] **Step 3: Generate the resource accessors**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL. This regenerates the `Res.string.*` accessors so the new keys resolve in later tasks.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(strings): add measurement CTA + skip copy"
```

---

### Task 2: Add `fromCustomerCreation` route arg + wire navigation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt:38-43`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt:225-227`

- [ ] **Step 1: Add the route field**

In `Routes.kt`, find:

```kotlin
@Serializable
data class MeasurementFormRoute(
    val customerId: String,
    val measurementId: String? = null,
    val linkToOrderId: String? = null,
)
```

Replace with:

```kotlin
@Serializable
data class MeasurementFormRoute(
    val customerId: String,
    val measurementId: String? = null,
    val linkToOrderId: String? = null,
    val fromCustomerCreation: Boolean = false,
)
```

- [ ] **Step 2: Pass the flag on the create-flow push only**

In `MainScreen.kt`, inside `onNavigateToCustomerWithMeasurement` (line 225), find:

```kotlin
                    navController.navigate(MeasurementFormRoute(customerId = newId)) {
                        launchSingleTop = true
                    }
```

Replace with:

```kotlin
                    navController.navigate(
                        MeasurementFormRoute(customerId = newId, fromCustomerCreation = true),
                    ) {
                        launchSingleTop = true
                    }
```

Leave the three other `MeasurementFormRoute(...)` call sites (lines 183, 197, 200, 281) untouched — they keep the default `false`.

- [ ] **Step 3: Compile to verify the new arg threads through**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(nav): add fromCustomerCreation flag to MeasurementFormRoute"
```

---

### Task 3: Surface `fromCustomerCreation` in state + add skip action/event (TDD)

**Files:**
- Modify: `.../measurement/presentation/form/MeasurementFormState.kt:10-32`
- Modify: `.../measurement/presentation/form/MeasurementFormAction.kt:14-16`
- Modify: `.../measurement/presentation/form/MeasurementFormEvent.kt`
- Modify: `.../measurement/presentation/form/MeasurementFormViewModel.kt:42-44,53,80-83,153-156`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

In `MeasurementFormViewModelTest.kt`, first update the `createViewModel` helper (lines 65–84) to accept the new arg. Find:

```kotlin
    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
        measurementId: String? = null,
    ): MeasurementFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (measurementId != null) put("measurementId", measurementId)
        }
```

Replace with:

```kotlin
    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
        measurementId: String? = null,
        fromCustomerCreation: Boolean = false,
    ): MeasurementFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (measurementId != null) put("measurementId", measurementId)
            put("fromCustomerCreation", fromCustomerCreation)
        }
```

Then add these two tests just after `onNotesChange_updatesNotes` (after line 337):

```kotlin
    // --- Create-flow source awareness (PTSP measurement CTA UX) ---

    @Test
    fun fromCustomerCreation_arg_surfacesInState() = runTest {
        val vm = createViewModel(fromCustomerCreation = true)
        assertTrue(vm.state.value.fromCustomerCreation)
    }

    @Test
    fun fromCustomerCreation_defaultsFalse_whenArgAbsent() = runTest {
        val vm = createViewModel()
        assertFalse(vm.state.value.fromCustomerCreation)
    }

    @Test
    fun onSkipClick_emitsSkipMeasurements_andSavesNothing() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel(fromCustomerCreation = true)
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))

        vm.onAction(MeasurementFormAction.OnSkipClick)

        assertIs<MeasurementFormEvent.SkipMeasurements>(vm.events.first())
        assertNull(measurementRepository.lastCreatedMeasurement)
        assertNull(measurementRepository.lastUpdatedMeasurement)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementFormViewModelTest*"`
Expected: FAIL — `fromCustomerCreation` is not a member of `MeasurementFormState`; `OnSkipClick` / `SkipMeasurements` unresolved.

- [ ] **Step 3: Add the state field**

In `MeasurementFormState.kt`, find:

```kotlin
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val customFieldSheet: CustomFieldSheet? = null,
) {
```

Replace with:

```kotlin
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val customFieldSheet: CustomFieldSheet? = null,
    // True when this form was reached as the second step of customer creation
    // (CustomerForm → "Save & Add Measurements"). Drives the "Save" CTA copy and
    // the "Skip for now" escape hatch. False for edit / order-link / detail entry.
    val fromCustomerCreation: Boolean = false,
) {
```

- [ ] **Step 4: Add the action**

In `MeasurementFormAction.kt`, find:

```kotlin
    data object OnSaveClick : MeasurementFormAction
    data object OnNavigateBack : MeasurementFormAction
```

Replace with:

```kotlin
    data object OnSaveClick : MeasurementFormAction
    data object OnSkipClick : MeasurementFormAction
    data object OnNavigateBack : MeasurementFormAction
```

- [ ] **Step 5: Add the event**

In `MeasurementFormEvent.kt`, find:

```kotlin
sealed interface MeasurementFormEvent {
    data object NavigateBack : MeasurementFormEvent
```

Replace with:

```kotlin
sealed interface MeasurementFormEvent {
    data object NavigateBack : MeasurementFormEvent

    /**
     * Emitted when a tailor taps "Skip for now" on the measurement form during
     * customer creation. The customer is already saved by this point, so the
     * Root navigates back (to the new customer's detail) WITHOUT writing a
     * measurement. Same destination as a successful save.
     */
    data object SkipMeasurements : MeasurementFormEvent
```

- [ ] **Step 6: Read the arg and handle the action in the ViewModel**

In `MeasurementFormViewModel.kt`, find (lines 42–44):

```kotlin
    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]
```

Replace with:

```kotlin
    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]
    private val fromCustomerCreation: Boolean = savedStateHandle["fromCustomerCreation"] ?: false
```

Find (line 53):

```kotlin
    private val _state = MutableStateFlow(MeasurementFormState(isEditMode = measurementId != null))
```

Replace with:

```kotlin
    private val _state = MutableStateFlow(
        MeasurementFormState(
            isEditMode = measurementId != null,
            fromCustomerCreation = fromCustomerCreation,
        ),
    )
```

Find (lines 79–83), the `stateIn` initial value:

```kotlin
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementFormState(isEditMode = measurementId != null)
        )
```

Replace with:

```kotlin
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementFormState(
                isEditMode = measurementId != null,
                fromCustomerCreation = fromCustomerCreation,
            )
        )
```

Find (lines 153–156):

```kotlin
            MeasurementFormAction.OnSaveClick -> save()
            MeasurementFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateBack) }
            }
```

Replace with:

```kotlin
            MeasurementFormAction.OnSaveClick -> save()
            MeasurementFormAction.OnSkipClick -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.SkipMeasurements) }
            }
            MeasurementFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateBack) }
            }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementFormViewModelTest*"`
Expected: PASS (all existing tests + the 3 new ones).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement
git commit -m "feat(measurement): source-aware state + skip action"
```

---

### Task 4: Measurement screen — adaptive CTA + skip button + Root wiring + previews

**Files:**
- Modify: `.../measurement/presentation/form/MeasurementFormScreen.kt:124-129` (Root events), `336-367` (footer button), previews at end.

- [ ] **Step 1: Wire the skip event in the Root**

Find (lines 124–129):

```kotlin
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementFormEvent.NavigateBack -> onNavigateBack()
            MeasurementFormEvent.NavigateToUpgrade -> onNavigateToUpgrade()
        }
    }
```

Replace with:

```kotlin
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementFormEvent.NavigateBack -> onNavigateBack()
            // Skip lands on the same destination as a successful save — the new
            // customer's detail screen already sitting below on the back stack.
            MeasurementFormEvent.SkipMeasurements -> onNavigateBack()
            MeasurementFormEvent.NavigateToUpgrade -> onNavigateToUpgrade()
        }
    }
```

- [ ] **Step 2: Make the footer CTA label adaptive and add the skip button**

Find (lines 335–368):

```kotlin
                Spacer(Modifier.height(DesignTokens.space2))
                Button(
                    onClick = { onAction(MeasurementFormAction.OnSaveClick) },
                    enabled = canSave,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(Res.string.measurement_save_button),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(DesignTokens.space4))
```

Replace with:

```kotlin
                Spacer(Modifier.height(DesignTokens.space2))
                Button(
                    onClick = { onAction(MeasurementFormAction.OnSaveClick) },
                    enabled = canSave,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (state.fromCustomerCreation) {
                                    stringResource(Res.string.measurement_create_flow_save_button)
                                } else {
                                    stringResource(Res.string.measurement_save_button)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (state.fromCustomerCreation) {
                    TextButton(
                        onClick = { onAction(MeasurementFormAction.OnSkipClick) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(Res.string.measurement_skip_for_now),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(DesignTokens.space4))
```

(`TextButton` is already imported at line 54; `Res.string.measurement_create_flow_save_button` and `Res.string.measurement_skip_for_now` need import statements — add them in the next step.)

- [ ] **Step 3: Add the new string imports**

Find (line 108):

```kotlin
import stitchpad.composeapp.generated.resources.measurement_save_button
```

Replace with:

```kotlin
import stitchpad.composeapp.generated.resources.measurement_create_flow_save_button
import stitchpad.composeapp.generated.resources.measurement_save_button
import stitchpad.composeapp.generated.resources.measurement_skip_for_now
```

(Keep imports alphabetically ordered to satisfy detekt — `measurement_create_flow_save_button` sorts before `measurement_edit_title`; if detekt complains, place it right after `measurement_add_title` instead. The simplest detekt-clean placement: put `measurement_create_flow_save_button` immediately before `measurement_edit_title` (line 102) and `measurement_skip_for_now` immediately after `measurement_section_of` (line 109). Verify with detekt in Step 5.)

- [ ] **Step 4: Add a create-flow preview**

After `MeasurementFormScreenMalePreview` (end of file, after line 1055), add:

```kotlin
@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenCreateFlowPreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    val allKeys = sections.flatMap { it.fields }.map { it.key }
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                fromCustomerCreation = true,
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = 0,
                fields = allKeys.associateWith { "" } + mapOf("bust_circumference" to "36"),
                unit = MeasurementUnit.INCHES,
            ),
            onAction = {}
        )
    }
}
```

- [ ] **Step 5: Compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL, detekt clean. If detekt flags import ordering, reorder per Step 3's note and re-run.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(measurement): adaptive Save CTA + Skip for now in create flow"
```

---

### Task 5: Customer form — prominent measurement card + adaptive Save button

**Files:**
- Modify: `.../customer/presentation/form/CustomerFormScreen.kt` — imports, call site (259–271), `SaveButton` (380–418), `MeasurementsToggleRow` → card (420–447), previews.

- [ ] **Step 1: Add new imports**

Find (lines 3–9):

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
```

Replace with:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
```

Find (lines 21–23):

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
```

Replace with:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Straighten
```

Find (line 47, the `graphics.SolidColor` import area):

```kotlin
import androidx.compose.ui.graphics.SolidColor
```

Replace with:

```kotlin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
```

Find (line 67):

```kotlin
import stitchpad.composeapp.generated.resources.customer_form_add_measurements_next
```

Replace with:

```kotlin
import stitchpad.composeapp.generated.resources.customer_form_add_measurements_helper
import stitchpad.composeapp.generated.resources.customer_form_add_measurements_next
```

Find (line 75):

```kotlin
import stitchpad.composeapp.generated.resources.customer_form_save_button
```

Replace with:

```kotlin
import stitchpad.composeapp.generated.resources.customer_form_save_and_measure_button
import stitchpad.composeapp.generated.resources.customer_form_save_button
```

- [ ] **Step 2: Update the call site — card + adaptive button**

Find (lines 259–271):

```kotlin
            if (!state.isEditMode) {
                MeasurementsToggleRow(
                    checked = state.addMeasurementsNext,
                    onToggle = { onAction(CustomerFormAction.OnToggleAddMeasurementsNext) },
                    enabled = !state.isLoading,
                )
            }

            SaveButton(
                isLoading = state.isLoading,
                label = stringResource(Res.string.customer_form_save_button),
                onClick = { onAction(CustomerFormAction.OnSaveClick) }
            )
```

Replace with:

```kotlin
            if (!state.isEditMode) {
                MeasurementsToggleCard(
                    checked = state.addMeasurementsNext,
                    onToggle = { onAction(CustomerFormAction.OnToggleAddMeasurementsNext) },
                    enabled = !state.isLoading,
                )
            }

            val showMeasureCta = !state.isEditMode && state.addMeasurementsNext
            SaveButton(
                isLoading = state.isLoading,
                label = if (showMeasureCta) {
                    stringResource(Res.string.customer_form_save_and_measure_button)
                } else {
                    stringResource(Res.string.customer_form_save_button)
                },
                leadingIcon = if (showMeasureCta) {
                    Icons.AutoMirrored.Filled.ArrowForward
                } else {
                    Icons.Default.Check
                },
                onClick = { onAction(CustomerFormAction.OnSaveClick) }
            )
```

- [ ] **Step 3: Add `leadingIcon` param to `SaveButton`**

Find (lines 379–409):

```kotlin
@Composable
private fun SaveButton(
    isLoading: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
```

Replace with:

```kotlin
@Composable
private fun SaveButton(
    isLoading: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Check,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
```

(The rest of `SaveButton` — the `Text(text = label …)` and closing braces — is unchanged.)

- [ ] **Step 4: Replace `MeasurementsToggleRow` with `MeasurementsToggleCard`**

Find the entire composable (lines 420–447):

```kotlin
@Composable
private fun MeasurementsToggleRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(vertical = DesignTokens.space2)
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.customer_form_add_measurements_next),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

Replace with:

```kotlin
@Composable
private fun MeasurementsToggleCard(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Border emphasises when checked (the default + recommended path) so the card
    // reads as an active, intentional step rather than ignorable fine print.
    val borderColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(DesignTokens.space3),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusMd))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(DesignTokens.iconList),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(Res.string.customer_form_add_measurements_next),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.customer_form_add_measurements_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
```

The existing `CustomerFormScreenAddMeasurementsCheckedPreview` and `...UncheckedPreview` (lines 476–506) already exercise both states — leave them. They now render the card automatically.

- [ ] **Step 5: Compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL, detekt clean. If detekt flags `Row`'s `.width` import (`androidx.compose.foundation.layout.width`) as now-unused, remove that import line.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormScreen.kt
git commit -m "feat(customer): prominent measurement card + adaptive Save CTA"
```

---

### Task 6: Full verification (Android + iOS + tests)

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Assemble Android debug**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Compile iOS (KMP gotcha — JVM-only APIs / Native skew surface only here)**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (No JVM-only APIs were introduced — `border`/`clip`/`Straighten` are all common Compose — but compile to confirm per project rule "always run iOS compile before declaring done".)

- [ ] **Step 4: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL.

---

## Manual smoke test (Daniel is QA — include in PR description)

1. New customer, leave the measurement card ticked → CTA reads **"Save & Add Measurements"** (forward-arrow icon) → tap → lands on measurement screen showing **"Save"** + **"Skip for now"**.
2. On that measurement screen, tap **"Skip for now"** → lands on the new customer's detail; reopen measurements and confirm none was saved.
3. New customer, ticked, fill measurements, tap **"Save"** → measurement saved, lands on customer detail.
4. New customer, untick the card → CTA reads **"Save Customer"** (check icon) → tap → lands on customer detail, no measurement screen.
5. Edit an existing customer → no card shown, CTA reads **"Save Customer"**.
6. Open measurements from customer detail and from an order's "link measurement" flow → CTA still reads **"Save Measurements"**, no skip link.
7. Toggle the card off then on → border/weight changes; verify in **light and dark** mode.

---

## Self-review notes

- **Spec coverage:** §1 card → Task 5; §2 adaptive form CTA → Task 5; §3 source-aware measurement screen → Tasks 2–4; §4 strings → Task 1 + per-task imports. All covered.
- **Type consistency:** `fromCustomerCreation: Boolean` is named identically across `MeasurementFormRoute`, `SavedStateHandle["fromCustomerCreation"]`, `MeasurementFormState`, and the test helper. New symbols `OnSkipClick` / `SkipMeasurements` are defined (Task 3) before first use (Task 4). `MeasurementsToggleCard` replaces `MeasurementsToggleRow` at its only call site.
- **Open question:** create-flow measurement CTA copy is "Save" (`measurement_create_flow_save_button`); to switch to "Complete", change only that one string value.

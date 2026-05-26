# PTSP-4 / PTSP-6 — Add Customer Measurement Option Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional "Add measurements next" checkbox to the customer add form that, on successful save, routes the tailor into the existing measurement form pre-bound to the new customer id. Bundle PTSP-6 by gating the measurement Save button until at least one figure is entered.

**Architecture:** Two-step flow reusing `MeasurementFormScreen` verbatim. Client-mint customer id via `kotlin.uuid.Uuid.random()` (existing pattern in `OrderFormViewModel`). New event `NavigateToNewCustomerMeasurement(customerId)` routes the form Root into a chained `navigate(CustomerDetail) { popUpTo<CustomerForm> { inclusive = true } } + navigate(MeasurementForm)` so back from the measurement form lands on customer detail. PTSP-6 promotes `canSave` to a derived val on `MeasurementFormState` so the predicate is unit-testable without Compose UI test infrastructure.

**Tech Stack:** Kotlin Multiplatform (Kotlin 2.3.21), Compose Multiplatform, Koin, kotlinx.coroutines (UnconfinedTestDispatcher), kotlin.test + Turbine-style channel reads.

**Spec:** `docs/superpowers/specs/2026-05-25-customer-add-measurement-option-design.md`

**Branch:** `feature/ptsp-4-add-customer-measurement-option` (off `origin/main`; already created with spec commit `c6ae95f`).

---

## File map

**PTSP-4 — Customer form measurement option:**

| File | Change |
|------|--------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormState.kt` | Add `addMeasurementsNext: Boolean = true`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormAction.kt` | Add `OnToggleAddMeasurementsNext`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormEvent.kt` | Add `NavigateToNewCustomerMeasurement(customerId)`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt` | Handle toggle action; in `save()` mint UUID, route event based on edit mode + checkbox. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormScreen.kt` | Add `MeasurementsToggleRow` composable + render in add mode + new previews + new `onNavigateToCustomerWithMeasurement` callback on Root. |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Add `customer_form_add_measurements_next` string. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` | Wire the new callback in the `CustomerFormRoute` composable. |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt` | Add 6 new tests covering toggle + save routing. |

**PTSP-6 — Measurement Save gate:**

| File | Change |
|------|--------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt` | Add `val canSave: Boolean` computed property. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt` | Replace inline `canSave` at line 135 with `state.canSave`. |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormStateTest.kt` | New file — 4 tests for the predicate. |

---

## Task 1: Add `addMeasurementsNext` state field + toggle action

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Append to the bottom of `CustomerFormViewModelTest.kt`, before the final closing brace:

```kotlin
    // --- addMeasurementsNext ---

    @Test
    fun initialState_addMode_addMeasurementsNextIsTrue() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.addMeasurementsNext)
    }

    @Test
    fun initialState_editMode_addMeasurementsNextIsTrue() = runTest {
        // Default is true in both modes; the screen hides the row in edit mode,
        // and save() short-circuits to NavigateBack when isEditMode is true.
        val viewModel = createViewModel(customerId = "customer-123")
        assertTrue(viewModel.state.value.addMeasurementsNext)
    }

    @Test
    fun onToggleAddMeasurementsNext_flipsFlag() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.addMeasurementsNext)

        viewModel.onAction(CustomerFormAction.OnToggleAddMeasurementsNext)
        assertFalse(viewModel.state.value.addMeasurementsNext)

        viewModel.onAction(CustomerFormAction.OnToggleAddMeasurementsNext)
        assertTrue(viewModel.state.value.addMeasurementsNext)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelTest.initialState_addMode_addMeasurementsNextIsTrue"`
Expected: FAIL — `state.addMeasurementsNext` doesn't exist (compile error or unresolved reference). Same for the other two tests.

- [ ] **Step 3: Add state field**

Edit `CustomerFormState.kt`:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.form

import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerFormState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val deliveryPreference: DeliveryPreference = DeliveryPreference.PICKUP,
    val notes: String = "",
    val createdAt: Long = 0L,
    val nameError: UiText? = null,
    val phoneError: UiText? = null,
    val emailError: UiText? = null,
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null,
    val addMeasurementsNext: Boolean = true,
)
```

- [ ] **Step 4: Add action**

Edit `CustomerFormAction.kt` — add one line in the sealed interface body:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.form

import com.danzucker.stitchpad.core.domain.model.DeliveryPreference

sealed interface CustomerFormAction {
    data class OnNameChange(val name: String) : CustomerFormAction
    data class OnPhoneChange(val phone: String) : CustomerFormAction
    data class OnEmailChange(val email: String) : CustomerFormAction
    data class OnAddressChange(val address: String) : CustomerFormAction
    data class OnDeliveryPreferenceChange(val preference: DeliveryPreference) : CustomerFormAction
    data class OnNotesChange(val notes: String) : CustomerFormAction
    data object OnNameBlur : CustomerFormAction
    data object OnPhoneBlur : CustomerFormAction
    data object OnEmailBlur : CustomerFormAction
    data object OnSaveClick : CustomerFormAction
    data object OnNavigateBack : CustomerFormAction
    data object OnErrorDismiss : CustomerFormAction
    data object OnToggleAddMeasurementsNext : CustomerFormAction
}
```

- [ ] **Step 5: Handle action in ViewModel**

In `CustomerFormViewModel.kt`, inside the `onAction` `when` block, add the new case before `CustomerFormAction.OnErrorDismiss`:

```kotlin
            CustomerFormAction.OnToggleAddMeasurementsNext ->
                _state.update { it.copy(addMeasurementsNext = !it.addMeasurementsNext) }
            CustomerFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelTest"`
Expected: PASS for all three new tests **and** all existing tests in the class (no regression).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt
git commit -m "feat(customers): add 'addMeasurementsNext' state + toggle action (PTSP-4)"
```

---

## Task 2: Route save() to new measurement form via UUID-minted id

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormEvent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `CustomerFormViewModelTest.kt`:

```kotlin
    // --- Save routing: addMeasurementsNext ---

    @Test
    fun save_addMode_checkboxChecked_emitsNavigateToNewCustomerMeasurement() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        // addMeasurementsNext defaults to true
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        val event = viewModel.events.first()
        assertIs<CustomerFormEvent.NavigateToNewCustomerMeasurement>(event)
        // The id emitted in the event must match the id passed to createCustomer
        val createdId = customerRepository.lastCreatedCustomer?.id
        assertNotNull(createdId)
        assertTrue(createdId.isNotBlank())
        assertEquals(createdId, event.customerId)
    }

    @Test
    fun save_addMode_checkboxUnchecked_emitsNavigateBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnToggleAddMeasurementsNext)  // → false
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertIs<CustomerFormEvent.NavigateBack>(viewModel.events.first())
    }

    @Test
    fun save_editMode_checkboxChecked_stillEmitsNavigateBack() = runTest {
        // Edit mode wins regardless of addMeasurementsNext value.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = Customer(
            id = "customer-123",
            userId = "test-uid",
            name = "Old Name",
            phone = "+2340000000000",
        )
        val viewModel = createViewModel(customerId = "customer-123")
        viewModel.onAction(CustomerFormAction.OnNameChange("New Name"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        // addMeasurementsNext is true by default; edit mode must still NavigateBack
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertIs<CustomerFormEvent.NavigateBack>(viewModel.events.first())
    }

    @Test
    fun save_addMode_passesNonBlankIdToRepository() = runTest {
        // Independent of routing — the id is minted client-side so the screen
        // can forward the new customer's id to the measurement form.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        val created = customerRepository.lastCreatedCustomer
        assertNotNull(created)
        assertTrue(created.id.isNotBlank())
    }

    @Test
    fun save_capReached_emitsShowCapReachedSheet_andNoCustomerCreated() = runTest {
        // Cap-reached must still take precedence over the new addMeasurementsNext
        // routing — no navigation to the measurement form, and the create call
        // is short-circuited by the repository so no customer is persisted.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.CAP_REACHED
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        // addMeasurementsNext is true by default
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertIs<CustomerFormEvent.ShowCapReachedSheet>(viewModel.events.first())
        assertNull(customerRepository.lastCreatedCustomer)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelTest.save_addMode_checkboxChecked_emitsNavigateToNewCustomerMeasurement"`
Expected: FAIL — `CustomerFormEvent.NavigateToNewCustomerMeasurement` doesn't exist (unresolved reference).

- [ ] **Step 3: Add the event**

Edit `CustomerFormEvent.kt`:

```kotlin
package com.danzucker.stitchpad.feature.customer.presentation.form

sealed interface CustomerFormEvent {
    data object NavigateBack : CustomerFormEvent

    /**
     * Emitted when createCustomer fails with CAP_REACHED so the screen can
     * show the cap-reached ModalBottomSheet instead of a generic snackbar.
     * Carries the current active count and the cap so the sheet can render
     * "X of Y active customers" without re-fetching from Firestore.
     */
    data class ShowCapReachedSheet(
        val activeCount: Int,
        val customerCap: Int,
    ) : CustomerFormEvent

    /**
     * Emitted on a successful add-mode save when the user chose
     * "Add measurements next". Carries the newly-minted customer id so the
     * Root can chain navigate(CustomerDetail) + navigate(MeasurementForm).
     */
    data class NavigateToNewCustomerMeasurement(
        val customerId: String,
    ) : CustomerFormEvent
}
```

- [ ] **Step 4: Modify `save()` in the ViewModel**

In `CustomerFormViewModel.kt`, add these imports near the existing imports:

```kotlin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
```

Replace the existing `save()` function with this version. The two behaviour changes are (a) minting `newId` client-side before the create call, and (b) routing to the new event on add-mode success when `addMeasurementsNext` is true:

```kotlin
    @OptIn(ExperimentalUuidApi::class)
    private fun save() {
        val nameValid = validateName()
        val phoneValid = validatePhone()
        val emailValid = validateEmail()
        if (!nameValid || !phoneValid || !emailValid) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val s = _state.value
            val newId = customerId ?: Uuid.random().toString()
            val customer = Customer(
                id = newId,
                userId = userId,
                name = s.name.trim(),
                phone = s.phone.trim(),
                email = s.email.trim().ifBlank { null },
                address = s.address.trim().ifBlank { null },
                deliveryPreference = s.deliveryPreference,
                notes = s.notes.trim().ifBlank { null },
                createdAt = s.createdAt
            )
            val result = if (customerId != null) {
                customerRepository.updateCustomer(userId, customer)
            } else {
                customerRepository.createCustomer(userId, customer)
            }
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    val event = if (!s.isEditMode && s.addMeasurementsNext) {
                        CustomerFormEvent.NavigateToNewCustomerMeasurement(customerId = newId)
                    } else {
                        CustomerFormEvent.NavigateBack
                    }
                    _events.send(event)
                }
                is Result.Error -> {
                    if (result.error == DataError.Network.CAP_REACHED) {
                        // Cap-reached is the only error with a dedicated upgrade-pitch
                        // bottom sheet; everything else routes through the generic
                        // snackbar via errorMessage. activeCount == cap here by
                        // definition (we just failed because we're AT the cap).
                        val cap = entitlements.current().customerCap
                        _events.send(
                            CustomerFormEvent.ShowCapReachedSheet(
                                activeCount = cap,
                                customerCap = cap,
                            )
                        )
                    } else {
                        _state.update {
                            it.copy(errorMessage = result.error.toCustomerUiText())
                        }
                    }
                }
            }
        }
    }
```

- [ ] **Step 5: Refactor one existing test that the new default breaks**

The existing `save_createMode_callsCreateCustomer_andNavigatesBack` test asserts `NavigateBack` on add-mode save success. With the new default `addMeasurementsNext = true`, that path now emits `NavigateToNewCustomerMeasurement`. Update the existing test to explicitly uncheck the flag so it continues to assert the vanilla save-and-leave path:

In `CustomerFormViewModelTest.kt`, replace the entire `save_createMode_callsCreateCustomer_andNavigatesBack` test body with:

```kotlin
    @Test
    fun save_createMode_callsCreateCustomer_andNavigatesBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(CustomerFormAction.OnToggleAddMeasurementsNext)  // uncheck → vanilla save-and-leave path
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        val created = customerRepository.lastCreatedCustomer
        assertNotNull(created)
        assertEquals("Ade Fashions", created.name)
        assertEquals("+2348012345678", created.phone)
        assertEquals("ade@gmail.com", created.email)
        assertNull(customerRepository.lastUpdatedCustomer)

        assertIs<CustomerFormEvent.NavigateBack>(viewModel.events.first())
    }
```

The new add-mode happy-path with the checkbox CHECKED is already covered by `save_addMode_checkboxChecked_emitsNavigateToNewCustomerMeasurement` from Step 1.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelTest"`
Expected: PASS — all 5 new tests + the refactored existing test + every other previously-passing test.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormEvent.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt
git commit -m "feat(customers): route save to measurement form with minted UUID (PTSP-4)"
```

---

## Task 3: Add string resource

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add the string**

In `strings.xml`, find the existing `<string name="customer_form_save_button">` entry. Add this new string adjacent to the other `customer_form_*` strings:

```xml
    <string name="customer_form_add_measurements_next">Add measurements next</string>
```

(No backslash escapes; the string contains no apostrophes or quotes, so [[feedback_strings_no_backslash_escape]] is not at risk here.)

- [ ] **Step 2: Verify the resource compiles**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL. A new constant `Res.string.customer_form_add_measurements_next` is now available.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(customers): add 'Add measurements next' string (PTSP-4)"
```

---

## Task 4: Render the checkbox row in CustomerFormScreen + new previews

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormScreen.kt`

- [ ] **Step 1: Add imports**

At the top of `CustomerFormScreen.kt`, add these imports (sorted alphabetically with the existing ones):

```kotlin
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
```

Also add the new generated-resource import alongside the other `stitchpad.composeapp.generated.resources.*` imports:

```kotlin
import stitchpad.composeapp.generated.resources.customer_form_add_measurements_next
```

- [ ] **Step 2: Add the `MeasurementsToggleRow` composable**

Insert this new private composable into `CustomerFormScreen.kt`, just below the existing `SaveButton` composable (around the bottom of the file, before the preview composables):

```kotlin
@Composable
private fun MeasurementsToggleRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(vertical = DesignTokens.space2)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.customer_form_add_measurements_next),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

- [ ] **Step 3: Render the row in the form (add mode only)**

In `CustomerFormScreen` composable body, between the existing `Spacer(Modifier.height(DesignTokens.space2))` (the one immediately before the `SaveButton`) and the `SaveButton` call, insert the conditional row:

Find this existing block:

```kotlin
            Spacer(Modifier.height(DesignTokens.space2))

            SaveButton(
                isLoading = state.isLoading,
                label = stringResource(Res.string.customer_form_save_button),
                onClick = { onAction(CustomerFormAction.OnSaveClick) }
            )
```

Replace with:

```kotlin
            if (!state.isEditMode) {
                MeasurementsToggleRow(
                    checked = state.addMeasurementsNext,
                    onToggle = { onAction(CustomerFormAction.OnToggleAddMeasurementsNext) }
                )
            }

            Spacer(Modifier.height(DesignTokens.space2))

            SaveButton(
                isLoading = state.isLoading,
                label = stringResource(Res.string.customer_form_save_button),
                onClick = { onAction(CustomerFormAction.OnSaveClick) }
            )
```

- [ ] **Step 4: Add new previews**

Append these three previews after the existing `CustomerFormScreenEditPreview`, before the file's final closing brace:

```kotlin
@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenAddMeasurementsCheckedPreview() {
    StitchPadTheme {
        CustomerFormScreen(
            state = CustomerFormState(
                name = "Amina Bello",
                phone = "+234 801 234 5678",
                addMeasurementsNext = true,
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerFormScreenAddMeasurementsUncheckedPreview() {
    StitchPadTheme {
        CustomerFormScreen(
            state = CustomerFormState(
                name = "Amina Bello",
                phone = "+234 801 234 5678",
                addMeasurementsNext = false,
            ),
            onAction = {}
        )
    }
}
```

(The existing `CustomerFormScreenEditPreview` already covers "edit mode hides the row" because `isEditMode = true` will skip the new conditional render.)

- [ ] **Step 5: Build and verify previews compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL with no warnings about unresolved references.

Open `CustomerFormScreen.kt` in Android Studio, render the previews. Confirm:
- `CustomerFormScreenAddMeasurementsCheckedPreview` shows the checkbox row above the Save button, **checked**.
- `CustomerFormScreenAddMeasurementsUncheckedPreview` shows the same row, **unchecked**.
- `CustomerFormScreenEditPreview` does **not** show the row.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormScreen.kt
git commit -m "feat(customers): render 'Add measurements next' checkbox in add mode (PTSP-4)"
```

---

## Task 5: Wire CustomerFormRoot callback + Nav graph

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormScreen.kt` (Root only)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: Add the new Root callback parameter**

In `CustomerFormScreen.kt`, update the `CustomerFormRoot` function signature and event handling. Find:

```kotlin
@Composable
fun CustomerFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: CustomerFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Sheet visibility is held local to the composable rather than in
    // ViewModel state — the sheet is purely a presentation concern that
    // doesn't need to survive process death (the underlying CAP_REACHED
    // result will fire again on retry).
    var capSheet by remember { mutableStateOf<CustomerFormEvent.ShowCapReachedSheet?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerFormEvent.NavigateBack -> onNavigateBack()
            is CustomerFormEvent.ShowCapReachedSheet -> capSheet = event
        }
    }
```

Replace with:

```kotlin
@Composable
fun CustomerFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToCustomerWithMeasurement: (customerId: String) -> Unit,
) {
    val viewModel: CustomerFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Sheet visibility is held local to the composable rather than in
    // ViewModel state — the sheet is purely a presentation concern that
    // doesn't need to survive process death (the underlying CAP_REACHED
    // result will fire again on retry).
    var capSheet by remember { mutableStateOf<CustomerFormEvent.ShowCapReachedSheet?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerFormEvent.NavigateBack -> onNavigateBack()
            is CustomerFormEvent.ShowCapReachedSheet -> capSheet = event
            is CustomerFormEvent.NavigateToNewCustomerMeasurement ->
                onNavigateToCustomerWithMeasurement(event.customerId)
        }
    }
```

- [ ] **Step 2: Update the Nav graph caller in MainScreen**

In `MainScreen.kt`, find the existing `composable<CustomerFormRoute> { ... }` block (around line 199):

```kotlin
        composable<CustomerFormRoute> {
            CustomerFormRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToUpgrade = { navController.navigate(UpgradeRoute) },
            )
        }
```

Replace with:

```kotlin
        composable<CustomerFormRoute> {
            CustomerFormRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToUpgrade = { navController.navigate(UpgradeRoute) },
                onNavigateToCustomerWithMeasurement = { newId ->
                    // Chain: pop the form, push customer detail, then push the
                    // measurement form. Back from the measurement form lands
                    // on detail; back from detail lands on the list.
                    navController.navigate(CustomerDetailRoute(customerId = newId)) {
                        popUpTo<CustomerFormRoute> { inclusive = true }
                    }
                    navController.navigate(MeasurementFormRoute(customerId = newId))
                },
            )
        }
```

- [ ] **Step 3: Build to verify nav wiring compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. No unresolved references for `CustomerDetailRoute`, `MeasurementFormRoute`, or `popUpTo<CustomerFormRoute>`.

- [ ] **Step 4: Run iOS compile check**

Per [[feedback_kmp_jvm_only_apis]] / [[feedback_kotlin_native_epoch_days]], run an iOS compile before declaring done. The `Uuid.random()` call is already proven on iOS by `OrderFormViewModel` (which uses the same pattern), so this is a defensive habit check:

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full Customer form test suite**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelTest"`
Expected: PASS — all tests including the 7 new ones added in Tasks 1 & 2.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(customers): wire customer form → detail → measurement form nav (PTSP-4)"
```

---

## Task 6: PTSP-6 — Promote `canSave` to MeasurementFormState and gate empty saves

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create the new test file:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementFormStateTest {

    @Test
    fun canSave_isFalse_whenGenderIsNull() {
        val state = MeasurementFormState(
            gender = null,
            fields = mapOf("chest" to "38"),
        )
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_isFalse_whenGenderSet_butAllFieldsBlank() {
        // PTSP-6: this is the regression we're fixing. Today Save is enabled
        // with no figures entered; after this change it must be disabled.
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "", "waist" to "", "hip" to ""),
        )
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_isTrue_whenGenderSet_andAtLeastOneFieldNonBlank() {
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "38", "waist" to "", "hip" to ""),
        )
        assertTrue(state.canSave)
    }

    @Test
    fun canSave_isFalse_whenLoading_evenIfFieldsValid() {
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "38"),
            isLoading = true,
        )
        assertFalse(state.canSave)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormStateTest"`
Expected: FAIL — `state.canSave` doesn't exist (unresolved reference).

- [ ] **Step 3: Add the computed property**

Edit `MeasurementFormState.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementSection
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementFormState(
    val gender: CustomerGender? = null,
    val sections: List<MeasurementSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val isCurrentSectionExpanded: Boolean = true,
    val isNotesExpanded: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
    val unit: MeasurementUnit = MeasurementUnit.INCHES,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null,
    val originalCreatedAt: Long = 0L,
    val originalDateTaken: Long = 0L,
) {
    /**
     * PTSP-6: Save is gated until at least one figure is entered.
     * Edit-mode entries pre-populate `fields` from the existing measurement,
     * so the gate naturally allows resaves of an existing record.
     */
    val canSave: Boolean
        get() = gender != null && fields.values.any { it.isNotBlank() } && !isLoading
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormStateTest"`
Expected: PASS — all 4 tests pass.

- [ ] **Step 5: Update the screen to use `state.canSave`**

In `MeasurementFormScreen.kt`, find line 135:

```kotlin
    val canSave = state.gender != null && !state.isLoading
```

Replace with:

```kotlin
    val canSave = state.canSave
```

- [ ] **Step 6: Run the full measurement test suite + verify the screen compiles**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.*"`
Expected: PASS — existing `MeasurementFormViewModelTest` is unaffected; new `MeasurementFormStateTest` passes.

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormStateTest.kt
git commit -m "fix(measurements): disable Save until at least one figure entered (PTSP-6)"
```

---

## Task 7: Cross-platform compile + full-suite verification

**Files:** None (verification only)

- [ ] **Step 1: Android compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: iOS simulator compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. Confirms the UUID + new event types link cleanly on iOS Native per [[feedback_kmp_jvm_only_apis]].

- [ ] **Step 3: Full unit test suite**

Run: `./gradlew :composeApp:allTests`
Expected: All tests pass. (Or `:composeApp:jvmTest` if `allTests` is wired differently in this project.)

- [ ] **Step 4: Detekt**

Run: `./gradlew detekt`
Expected: No new issues. If new findings, run the `format` skill to auto-fix.

- [ ] **Step 5: Open `format` skill if detekt reported anything**

Per project convention, format Kotlin sources via the `/format` skill before pushing.

---

## Task 8: Manual smoke test (Daniel / QA)

**Files:** None (manual verification per the spec's smoke test section)

Run through every step in §6.3 of `docs/superpowers/specs/2026-05-25-customer-add-measurement-option-design.md` on **Android** and **iOS** (iPhone 17 Pro sim per [[reference_test_environment]]), in **light + dark** mode. Use the Fola account for the happy path and a cap-reached account for step 5.

- [ ] PTSP-4 step 1: default-check happy path
- [ ] PTSP-4 step 2: abandon measurement → still lands on customer detail
- [ ] PTSP-4 step 3: skip path (uncheck → list)
- [ ] PTSP-4 step 4: edit mode hides the checkbox
- [ ] PTSP-4 step 5: cap-reached sheet still fires; swap + retry → measurement form
- [ ] PTSP-4 step 6: string renders cleanly on iOS (no `\'` artifacts)
- [ ] PTSP-6 step 7: Save disabled at entry
- [ ] PTSP-6 step 8: Save enables on first field
- [ ] PTSP-6 step 9: Save disables when cleared
- [ ] PTSP-6 step 10: edit existing measurement → Save enabled on entry

---

## Task 9: Push branch and open PR

**Files:** None

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feature/ptsp-4-add-customer-measurement-option
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(customers): optional 'Add measurements next' in customer form (PTSP-4 / PTSP-6)" --body "$(cat <<'EOF'
## Summary
- PTSP-4: customer add form gains an optional 'Add measurements next' checkbox (default checked, hidden in edit mode); successful save routes into the existing measurement form pre-bound to the new customer id, landing on customer detail either way.
- PTSP-6: measurement form Save is now disabled until at least one figure is entered (promoted to `MeasurementFormState.canSave` for unit-testability).

## Test plan
- [ ] Android: PTSP-4 steps 1–6 in the spec smoke test
- [ ] iOS: PTSP-4 steps 1, 3, 5 in the spec smoke test
- [ ] Android: PTSP-6 steps 7–10
- [ ] iOS: PTSP-6 step 7
- [ ] Light + dark mode for new checkbox row and disabled-Save styling
- [ ] Cursor review
- [ ] codex review

Spec: docs/superpowers/specs/2026-05-25-customer-add-measurement-option-design.md
EOF
)"
```

- [ ] **Step 3: Cursor review + codex review**

Per [[feedback_review_rotation]] / [[feedback_cursor_review_patterns]], run both Cursor and `codex review` on the PR before merging. Address any findings.

---

## Notes

- **No detekt suppression should be needed.** The added code follows existing patterns.
- **`@OptIn(ExperimentalUuidApi::class)`** is required on `save()` only — the import scope is method-local. `OrderFormViewModel` uses the same pattern (see line 259 / 305).
- **iOS UUID** — `kotlin.uuid.Uuid.random()` is multiplatform from Kotlin 2.0.20+ and is already proven on iOS in `OrderFormViewModel`. No platform-specific code needed.
- **Deferred items** (from spec §8): offline `createCustomer` hang, strict `> 0` validation for measurements, process-death survival of `CustomerFormState` — NOT addressed in this plan.

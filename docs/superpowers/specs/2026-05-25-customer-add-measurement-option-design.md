# Add-customer + measurement option — PTSP-4 / PTSP-6

**Date:** 2026-05-25
**Status:** Approved (brainstorm) — awaiting implementation plan
**Tickets:** PTSP-4 (primary), PTSP-6 (bundled fix)
**Branch:** `feature/ptsp-4-add-customer-measurement-option`

---

## 1. Context

Today, capturing a measurement requires three navigations after a fitting:
Customer list → FAB → Customer form (save) → list → tap customer →
detail → "Add measurement" → measurement form. PTSP-4 collapses that
path so a tailor creating a customer at fitting time can land directly
in the measurement form with the new customer pre-bound.

The option must be **optional** — a tailor capturing a phone-list of
clients without measurements should be able to skip with one tap.

PTSP-6 is a small correctness fix in the same touch-area: the measurement
form currently allows saving an empty measurement (gender + 0 fields). It
should require at least one figure before Save is tappable.

## 2. Approach

Two-step flow, reusing the existing `MeasurementFormScreen` verbatim:

- The customer form gains one new control — a checkbox **"Add
  measurements next"** (checked by default in add mode, hidden in edit
  mode).
- On successful customer save with the checkbox checked, the user is
  navigated to the existing measurement form pre-bound to the new
  customer's id.
- Whether they save the measurement or back out, they land on **Customer
  detail** for the new customer.
- With the checkbox unchecked, save behaviour is unchanged from today.

Inline-expand and post-save-prompt alternatives were considered and
rejected — the existing measurement form has a paged, gender-gated
layout that doesn't compose cleanly inside the customer form, and a
post-save prompt adds a confirmation step without UX gain.

## 3. UX flow

### 3.1 Add mode

```
List
 └─ Customer form (add)
     ├─ Name / Phone / Email / Address / Delivery / Notes  (unchanged)
     ├─ ☑ Add measurements next        ← NEW row, checked by default
     └─ [ Save ]
         │
         ├─ checkbox UNCHECKED → navigate back to List (today's behaviour)
         │
         └─ checkbox CHECKED → on save success:
             nav.navigate(CustomerDetail(newId)) {
                 popUpTo<CustomerFormRoute> { inclusive = true }
             }
             nav.navigate(MeasurementForm(newId))
```

After the user finishes the measurement form:

- **Save measurement** → `navigateUp()` → Customer detail.
- **Back without saving** → `navigateUp()` → Customer detail.
- **Back from Customer detail** → Customer list.

### 3.2 Edit mode

The checkbox row is **not rendered**. Save behaviour is identical to
today. The existing "Add measurement" CTA on customer detail remains the
entry point for measurements on existing customers.

### 3.3 Failure paths

- Validation failure (name / phone / email) → no save attempted; field
  errors shown as today. Checkbox state preserved.
- `CAP_REACHED` → existing `CustomerCapReachedSheet` shows; no
  navigation to measurement form. Checkbox state preserved so a swap
  + retry reaches the measurement form.
- Other `DataError.Network` → existing snackbar via `errorMessage`. No
  navigation.

## 4. Architecture changes

### 4.1 `CustomerFormState`

```kotlin
data class CustomerFormState(
    // existing fields unchanged
    val addMeasurementsNext: Boolean = true,  // NEW; hidden in edit mode
)
```

### 4.2 `CustomerFormAction`

```kotlin
data object OnToggleAddMeasurementsNext : CustomerFormAction  // NEW
```

### 4.3 `CustomerFormEvent`

```kotlin
data class NavigateToNewCustomerMeasurement(  // NEW
    val customerId: String,
) : CustomerFormEvent
```

`NavigateBack` and `ShowCapReachedSheet` unchanged.

### 4.4 `CustomerFormViewModel`

- `OnToggleAddMeasurementsNext` → flip the flag.
- `save()`:
  - Mint id with `kotlin.uuid.Uuid.random().toString()` **before** the
    create call. `FirebaseCustomerRepository.createCustomer` already
    uses a supplied id when non-blank (see `FirebaseCustomerRepository.kt`
    line 147) — no repository signature change needed.
  - On `Result.Success`:
    - `isEditMode` → `NavigateBack` (unchanged).
    - else if `state.addMeasurementsNext` → `NavigateToNewCustomerMeasurement(newId)`.
    - else → `NavigateBack`.

### 4.5 `CustomerFormRoot`

Add one callback:

```kotlin
fun CustomerFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToCustomerWithMeasurement: (customerId: String) -> Unit,  // NEW
)
```

The Root observes the new event and invokes the new callback.

### 4.6 Nav wiring (`MainScreen.kt`)

```kotlin
composable<CustomerFormRoute> {
    CustomerFormRoot(
        onNavigateBack = { navController.navigateUp() },
        onNavigateToUpgrade = { navController.navigate(UpgradeRoute) },
        onNavigateToCustomerWithMeasurement = { newId ->
            navController.navigate(CustomerDetailRoute(customerId = newId)) {
                popUpTo<CustomerFormRoute> { inclusive = true }
            }
            navController.navigate(MeasurementFormRoute(customerId = newId))
        },
    )
}
```

### 4.7 UI — `MeasurementsToggleRow`

Private composable in `CustomerFormScreen.kt`, placed between the Notes
field and the Save button, rendered only when `!state.isEditMode`:

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
            .padding(vertical = DesignTokens.space2),
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

- Whole row is tappable via `Modifier.toggleable` + `Role.Checkbox`.
- `Checkbox.onCheckedChange = null` because the parent owns the click.
- Not disabled while `isLoading` — toggling intent for the next attempt
  is harmless; the Save button is the gated control.

### 4.8 New string

```xml
<string name="customer_form_add_measurements_next">Add measurements next</string>
```

Single string addition. Save button label unchanged.

### 4.9 Previews

Three new previews on `CustomerFormScreen.kt`:

- Add mode, checkbox checked (default).
- Add mode, checkbox unchecked.
- Edit mode (verifies the row is hidden).

## 5. PTSP-6 — measurement Save gate

In `MeasurementFormScreen.kt` (currently line 135):

```kotlin
// before
val canSave = state.gender != null && !state.isLoading

// after
val hasAnyFigure = state.fields.values.any { it.isNotBlank() }
val canSave = state.gender != null && hasAnyFigure && !state.isLoading
```

No state-class change. The `fields` map already drives recomposition on
every keystroke, so the Save button enables/disables in real time.

Edit-mode safety: loading an existing measurement pre-populates
`state.fields`, so Save is enabled on entry as it should be.

## 6. Testing

### 6.1 Unit tests — `CustomerFormViewModelTest`

Pattern follows [[android-testing]] (JUnit5, Turbine, AssertK,
`UnconfinedTestDispatcher`, fake `CustomerRepository`).

1. Default state has `addMeasurementsNext = true` in add mode.
2. `OnToggleAddMeasurementsNext` flips the flag.
3. Save success, add mode, checkbox checked → emits
   `NavigateToNewCustomerMeasurement(id)`, where `id` matches the id
   passed to `createCustomer`.
4. Save success, add mode, checkbox unchecked → emits `NavigateBack`.
5. Save success, **edit mode**, checkbox checked → emits `NavigateBack`
   (edit mode wins).
6. Save fails with `CAP_REACHED` → emits `ShowCapReachedSheet`, no
   navigation event.
7. Save fails with other `DataError.Network` → `state.errorMessage`
   set, no navigation event.
8. Customer passed to `createCustomer` has a non-blank id in add mode.

### 6.2 UI tests — `MeasurementFormScreenTest`

PTSP-6 coverage via Compose UI test:

9. State with gender selected and all fields blank → Save button
   `assertIsNotEnabled()`.
10. State with gender selected and one non-blank field → Save button
    `assertIsEnabled()`.
11. Non-blank then cleared → Save returns to disabled.

### 6.3 Smoke test (Daniel as QA, Android + iOS, light + dark)

**Setup:** Fola (existing customers, not at cap) + a cap-reached test
account.

**PTSP-4 — checkbox flow**

1. **Default-check happy path.** Customers → FAB → Add customer → fill
   name + phone → confirm checkbox reads "Add measurements next" and is
   **checked**. Tap Save.
   - ✅ Lands on measurement form.
   - ✅ Pick gender, type chest = 38, Save measurement.
   - ✅ Lands on customer detail with the new customer + measurement
     visible.
   - ✅ System back → customer list (NOT customer form).
2. **Default-check, abandon measurement.** As #1 but back out of the
   measurement form without typing.
   - ✅ Customer is still created (visible in list/detail).
   - ✅ Lands on customer detail with no measurement attached.
3. **Skip path.** Add customer, **uncheck** the checkbox, Save.
   - ✅ Lands on customer list. No measurement form.
4. **Edit mode.** Tap a customer → Edit → confirm the checkbox row is
   **not shown**. Edit a field, Save.
   - ✅ No regression vs. today's edit flow.
5. **Cap reached.** Switch to cap-reached account. Add customer, leave
   checkbox checked, Save.
   - ✅ `CustomerCapReachedSheet` shows. No navigation to measurement
     form.
   - ✅ "Swap a customer" → swap one → re-Save → reaches measurement
     form on the second attempt.
6. **String renders.** Confirm "Add measurements next" displays without
   missing-resource fallback or `\'` artifacts (per
   [[feedback_strings_no_backslash_escape]]).

**PTSP-6 — measurement Save gate**

7. **Disabled at entry.** From customer detail of a measurement-less
   customer, tap "Add measurement" → pick gender → Save button is
   **disabled**.
8. **Enables on first field.** Type a value into any one field.
   - ✅ Save becomes enabled.
9. **Disables when cleared.** Clear that field back to blank.
   - ✅ Save returns to disabled.
10. **Edit existing measurement.** Open an existing measurement to edit
    → Save is enabled on entry (pre-populated fields).

**Cross-platform**

- Run #1, #3, #5, #7 on both Android and iOS
  ([[reference_test_environment]] sim UDIDs).
- Light **and** dark mode for the new checkbox row and the disabled-Save
  styling (per [[feedback_spec_both_color_modes]]).
- iOS compile check before declaring done (UUID is multiplatform but
  habit per [[feedback_kmp_jvm_only_apis]]).

## 7. Review

- Cursor + `codex review` before merge per
  [[feedback_review_rotation]].

## 8. Deferred / out of scope

- **Offline `createCustomer` hang.** Per
  [[feedback_gitlive_firestore_set_awaits_server_ack]], `docRef.set()`
  awaits server ACK. An offline tailor saving customers will hang
  whether or not they tick the checkbox — pre-existing, no worse here.
  Tracked under [[project_workshop_setup_offline_gap]] family of work.
- **PTSP-6 strict `> 0` validation.** Current fix accepts `"0"` as a
  figure (`"0".isNotBlank() == true`). Backlog candidate if a
  later policy requires positive-only values.
- **Process-death survival of the customer form.** `CustomerFormState`
  is not persisted to `SavedStateHandle`; backgrounding mid-form loses
  input. Pre-existing across the form; out of scope.

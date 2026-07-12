# Chained-Create Celebration Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a first-customer create chains into Add Measurements, the celebration card's body/CTA hand the user into the measurement task instead of interrupting it.

**Architecture:** Presentation-only: one new defaulted field on `Milestone.FirstCustomer`, passed from the existing state toggle, branched on in the overlay's copy helpers. No flag/analytics/timing changes.

**Tech Stack:** KMP + Compose Multiplatform; kotlin.test.

**Spec:** `docs/superpowers/specs/2026-07-12-celebration-chained-create-design.md`

## Global Constraints

- Branch: `feat/milestone-celebrations` (PR #264), working directly in /Users/danzucker/Desktop/Project/StitchPad. The working tree has an UNRELATED uncommitted change to `web/public/logo.svg` — never stage, commit, or revert it; `git add` named paths only.
- Strings: `’` never `\'`; positional args.
- Imports strictly alphabetical (manual check; detekt won't catch it).
- `Milestone.FirstCustomer.key` must remain `"first_customer"`.
- Gates before commit: full `./gradlew :composeApp:testDebugUnitTest`, `./gradlew detekt`, `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64`.
- Commit trailer: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

---

### Task 1: Chained-create celebration card

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/celebration/Milestone.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/CelebrationOverlay.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelCelebrationTest.kt`

**Interfaces:**
- Consumes: `CustomerFormState.addMeasurementsNext` (existing toggle), `CelebrationController.trigger`.
- Produces: `Milestone.FirstCustomer(customerFirstName: String, addingMeasurementsNext: Boolean = false)` — the default keeps every existing construction/equality site compiling unchanged.

- [ ] **Step 1: Write the failing test**

Add to `CustomerFormViewModelCelebrationTest.kt` (after the existing positive test):

```kotlin
    @Test
    fun `chained create carries addingMeasurementsNext`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnToggleAddMeasurementsNext)
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertEquals(
            Milestone.FirstCustomer("Adaeze", addingMeasurementsNext = true),
            celebrations.current.value,
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormViewModelCelebrationTest"`
Expected: FAIL — no `addingMeasurementsNext` parameter on `Milestone.FirstCustomer`.

- [ ] **Step 3: Add the field**

In `Milestone.kt`, replace the `FirstCustomer` declaration with:

```kotlin
    data class FirstCustomer(
        val customerFirstName: String,
        /**
         * True when the create chains straight into Add Measurements — the
         * card's body/CTA then hand the user into that task instead of a
         * generic "Continue". Presentation-only: key/flag/analytics ignore it.
         */
        val addingMeasurementsNext: Boolean = false,
    ) : Milestone {
        override val key: String = "first_customer"
    }
```

- [ ] **Step 4: Pass the toggle from the ViewModel**

In `CustomerFormViewModel.kt`'s create-success branch, the trigger becomes:

```kotlin
                        if (isFirstCustomerCandidate) {
                            celebrations.trigger(
                                userId = userId,
                                milestone = Milestone.FirstCustomer(
                                    customerFirstName = customer.name.substringBefore(' '),
                                    addingMeasurementsNext = s.addMeasurementsNext,
                                ),
                            )
                        }
```

(`s` is the `_state.value` snapshot already taken in `save()` — the same source `postSaveEvent` reads.)

- [ ] **Step 5: Add the strings**

In `strings.xml`, inside the milestone-celebrations block after `celebration_first_customer_body`:

```xml
    <string name="celebration_first_customer_body_measurements">%1$s is in your workshop. Let’s capture their measurements.</string>
```

and after `celebration_continue`:

```xml
    <string name="celebration_add_measurements">Add measurements</string>
```

(Typographic `’`, never `\'`.)

- [ ] **Step 6: Branch the card copy**

In `CelebrationOverlay.kt`, update the two copy helpers:

```kotlin
@Composable
private fun Milestone.body(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_body)
    is Milestone.FirstCustomer -> if (addingMeasurementsNext) {
        stringResource(Res.string.celebration_first_customer_body_measurements, customerFirstName)
    } else {
        stringResource(Res.string.celebration_first_customer_body, customerFirstName)
    }
    is Milestone.FirstOrder ->
        stringResource(Res.string.celebration_first_order_body, customerFirstName)
}

@Composable
private fun Milestone.buttonLabel(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_button)
    is Milestone.FirstCustomer -> if (addingMeasurementsNext) {
        stringResource(Res.string.celebration_add_measurements)
    } else {
        stringResource(Res.string.celebration_continue)
    }
    is Milestone.FirstOrder -> stringResource(Res.string.celebration_continue)
}
```

Add the two new imports to the generated-resources import block in alphabetical position: `stitchpad.composeapp.generated.resources.celebration_add_measurements` and `stitchpad.composeapp.generated.resources.celebration_first_customer_body_measurements`.

- [ ] **Step 7: Run the full gates**

Run: `./gradlew :composeApp:testDebugUnitTest` — Expected: PASS (existing celebration tests unchanged thanks to the default; plus the new test).
Run: `./gradlew detekt` — Expected: PASS.
Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/presentation/celebration/Milestone.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModel.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/celebration/CelebrationOverlay.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelCelebrationTest.kt
git commit -m "feat(celebration): chained-create card hands off into Add Measurements"
```

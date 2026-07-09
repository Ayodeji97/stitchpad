# Measurement Empty State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "View Measurements" for a zero-measurement customer lands on the Measurement Detail screen showing an empty-state hero with an "Add measurement" CTA, unified across the customer actions sheet and the dashboard picker.

**Architecture:** `MeasurementDetailRoute.measurementId` becomes nullable (null = empty mode). The ViewModel's existing measurement observer handles the null-ID case (empty list → empty state; non-empty → adopt most recent). A pure `destinationFor()` decision function on `MeasurementEntryResolver`'s companion is shared by the resolver (actions sheet) and the dashboard picker. A `fromEmptyDetail` flag on `MeasurementFormRoute` lets the post-save `popUpTo` also pop the stale empty-mode detail entry.

**Tech Stack:** KMP + Compose Multiplatform, MVI, Koin, kotlin.test + Turbine (`:composeApp:testDebugUnitTest`), detekt.

**Spec:** `docs/superpowers/specs/2026-07-10-measurement-empty-state-design.md`

## Global Constraints

- Branch: `feat/measurement-empty-state` (already created off `main`). Never push to `main`.
- `MeasurementDetailViewModel` has 14 functions vs detekt `TooManyFunctions.thresholdInClasses: 15` — **do not add any member function to it**; new logic goes inline in existing functions/`when` branches.
- No hardcoded user-facing strings — compose resources only. Format args are always positional (`%1$s`). Never backslash-escape apostrophes in strings.xml — use `&apos;`.
- Test names use backticks with NO parentheses inside (illegal in JVM method names).
- Never pipe gradle output in a way that masks the exit code (no `./gradlew ... | tail`); run commands bare.
- All state in ViewModel; Screen composables stateless with `@Preview`; new UI must look right in light AND dark (M3 tokens only).
- Every task ends with a commit on this branch. Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- iOS compile is part of done (Task 5) — KMP code that compiles on Android can still fail iOS.

---

### Task 1: Empty-mode plumbing — Route, State, Action, Event, ViewModel, nav wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt:48-61`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailEvent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt:115-139` (Root only — new callback + event branch)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt:316-352`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailViewModelTest.kt`

**Interfaces:**
- Consumes: existing `MeasurementDetailViewModel` collaborators (fakes already exist).
- Produces (later tasks rely on these exact shapes):
  - `MeasurementDetailRoute(customerId: String, measurementId: String?, source: String, fromSave: Boolean = false)` — null `measurementId` = empty mode.
  - `MeasurementFormRoute(..., fromEmptyDetail: Boolean = false)`.
  - `MeasurementDetailState.isEmptyState: Boolean` (default false).
  - `MeasurementDetailAction.OnAddMeasurementClick` (data object).
  - `MeasurementDetailEvent.NavigateToAdd(customerId: String)`.
  - `MeasurementDetailRoot(onNavigateBack, onNavigateToEdit, onNavigateToUpgrade, onNavigateToAdd: (String) -> Unit)`.

- [ ] **Step 1: Write the failing ViewModel tests**

In `MeasurementDetailViewModelTest.kt`, change the `createViewModel` helper's parameter from `measurementId: String = "meas-1"` to `measurementId: String? = "meas-1"` (the map literal already accepts a null value; nothing else in the helper changes). Then add these tests after the existing ones:

```kotlin
    @Test
    fun `empty mode shows empty state when customer has no measurements`() = runTest {
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(measurementId = null)

        val state = vm.state.value
        assertTrue(state.isEmptyState)
        assertNull(state.measurement)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `empty mode adopts the most recent measurement when the list is not empty`() = runTest {
        measurementRepository.measurementsList = listOf(
            fakeMeasurement(id = "older").copy(createdAt = 1L),
            fakeMeasurement(id = "newer", name = "Newest").copy(createdAt = 2L),
        )
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(measurementId = null)

        val state = vm.state.value
        assertEquals("newer", state.measurement?.id)
        assertEquals(false, state.isEmptyState)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `empty mode does not navigate back`() = runTest {
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(measurementId = null)

        vm.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `empty mode add CTA emits NavigateToAdd for unlocked customer`() = runTest {
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(measurementId = null)

        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnAddMeasurementClick)
            val event = assertIs<MeasurementDetailEvent.NavigateToAdd>(awaitItem())
            assertEquals("customer-1", event.customerId)
        }
    }

    @Test
    fun `empty mode add CTA routes locked customer to upgrade`() = runTest {
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        val vm = createViewModel(measurementId = null)

        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnAddMeasurementClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
        }
    }
```

(`assertTrue`, `assertNull`, `assertIs`, `assertEquals`, `CustomerSlotState` are already imported in this file.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.detail.MeasurementDetailViewModelTest"`
Expected: FAIL — compilation errors (`isEmptyState`, `OnAddMeasurementClick`, `NavigateToAdd` unresolved).

- [ ] **Step 3: Implement routes, state, action, event**

`Routes.kt` — replace lines 48-61 with:

```kotlin
@Serializable
data class MeasurementFormRoute(
    val customerId: String,
    val measurementId: String? = null,
    val linkToOrderId: String? = null,
    val fromCustomerCreation: Boolean = false,
    /** Launched from the detail screen's empty-state CTA — post-save must pop that detail entry too. */
    val fromEmptyDetail: Boolean = false,
)

@Serializable
data class MeasurementDetailRoute(
    val customerId: String,
    /** null = empty mode: the customer is confirmed to have zero measurements; the screen shows the add-first hero. */
    val measurementId: String?,
    val source: String,
    val fromSave: Boolean = false,
)
```

`MeasurementDetailState.kt` — add after `isLoading`:

```kotlin
    /** Empty mode (null-measurementId route) with a confirmed-empty list — render the add-first hero. */
    val isEmptyState: Boolean = false,
```

`MeasurementDetailAction.kt` — add to the sealed interface:

```kotlin
    /** Empty-state hero CTA. */
    data object OnAddMeasurementClick : MeasurementDetailAction
```

`MeasurementDetailEvent.kt` — add to the sealed interface:

```kotlin
    /** Empty-state CTA — open the create form for this customer. */
    data class NavigateToAdd(val customerId: String) : MeasurementDetailEvent
```

Also extend the `NavigateToUpgrade` KDoc from "— Edit, Rename, or Delete." to "— Edit, Rename, Delete, or the empty-state Add CTA."

- [ ] **Step 4: Implement the ViewModel empty mode**

In `MeasurementDetailViewModel.kt` (NO new member functions — the class is at detekt's TooManyFunctions limit):

(a) `onStart` block (lines 95-100): only bail on a missing customerId; pass the nullable measurementId through:

```kotlin
                if (customerId == null) {
                    _events.send(MeasurementDetailEvent.NavigateBack)
                    return@onStart
                }
                analytics.logEvent(AnalyticsEvent.MeasurementDetailViewed(source))
                observeMeasurement(customerId, measurementId)
```

(b) `observeMeasurement` — signature becomes `private fun observeMeasurement(customerId: String, measurementId: String?)` and the `Result.Success` branch becomes:

```kotlin
                    is Result.Success -> {
                        val measurement = if (measurementId != null) {
                            result.data.find { it.id == measurementId }
                        } else {
                            // Empty mode: adopt whatever exists (synced in from another
                            // device, or created via the CTA and returned to) — most
                            // recent wins.
                            result.data.maxByOrNull { it.createdAt }
                        }
                        when {
                            measurement != null -> {
                                hasSeenMeasurement = true
                                _state.update {
                                    it.copy(measurement = measurement, isLoading = false, isEmptyState = false)
                                }
                            }
                            measurementId == null ->
                                _state.update { it.copy(isLoading = false, isEmptyState = true) }
                            !navigatedAway && (hasSeenMeasurement || !fromSave) -> {
                                // Deleted elsewhere (another device / another screen) — leave.
                                navigatedAway = true
                                _events.send(MeasurementDetailEvent.NavigateBack)
                            }
                            // else: fromSave and never seen — the enqueued write hasn't
                            // reached the local cache yet; wait for the next snapshot.
                        }
                    }
```

(c) `onAction` — add one branch (inline, not a new function):

```kotlin
            MeasurementDetailAction.OnAddMeasurementClick -> requireUnlocked {
                val id = customerId ?: return@requireUnlocked
                viewModelScope.launch { _events.send(MeasurementDetailEvent.NavigateToAdd(id)) }
            }
```

(d) `onEditClick` — the `measurementId` property is null in empty mode even after a measurement is adopted; read the id from state instead:

```kotlin
    private fun onEditClick() = requireUnlocked {
        val customerId = customerId ?: return@requireUnlocked
        val measurementId = _state.value.measurement?.id ?: return@requireUnlocked
        viewModelScope.launch {
            _events.send(MeasurementDetailEvent.NavigateToEdit(customerId, measurementId))
        }
    }
```

- [ ] **Step 5: Wire Root and MainScreen**

`MeasurementDetailScreen.kt` Root (lines 115-139): add the callback param and event branch:

```kotlin
fun MeasurementDetailRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String, String) -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToAdd: (String) -> Unit,
) {
```

and inside `ObserveAsEvents`:

```kotlin
            is MeasurementDetailEvent.NavigateToAdd -> onNavigateToAdd(event.customerId)
```

`MainScreen.kt` — in `composable<MeasurementDetailRoute>` (lines 342-352) add:

```kotlin
                onNavigateToAdd = { customerId ->
                    navController.navigate(
                        MeasurementFormRoute(customerId = customerId, fromEmptyDetail = true),
                    )
                },
```

and in `composable<MeasurementFormRoute>`'s `onNavigateToDetail` (lines 329-338), replace the popUpTo block and its comment with:

```kotlin
                        // Opened from a detail entry — edit mode, or create from the
                        // empty-state CTA — replace that stale detail entry too, so Back
                        // returns to wherever the detail was launched from. Other create
                        // modes have no detail beneath; just replace the form.
                        if (formRoute.measurementId != null || formRoute.fromEmptyDetail) {
                            popUpTo<MeasurementDetailRoute> { inclusive = true }
                        } else {
                            popUpTo<MeasurementFormRoute> { inclusive = true }
                        }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.detail.MeasurementDetailViewModelTest"`
Expected: PASS (all, including pre-existing tests — the not-found/back-nav and fromSave guards must still hold).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/ \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailViewModelTest.kt
git commit -m "feat(measurement): detail screen empty mode plumbing (nullable measurementId)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Empty-state UI — strings, hero content, chrome hiding, previews

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (near line 1142, next to the other `measurement_detail_*` strings)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt`

**Interfaces:**
- Consumes: `MeasurementDetailState.isEmptyState`, `MeasurementDetailAction.OnAddMeasurementClick` (Task 1).
- Produces: no new public surface — private `MeasurementEmptyContent` composable inside the screen file.

- [ ] **Step 1: Add string resources**

In `strings.xml`, next to the other `measurement_detail_*` entries:

```xml
    <string name="measurement_empty_title">No measurements yet</string>
    <string name="measurement_empty_supporting">Record %1$s&apos;s measurements once and reuse them on every order.</string>
    <string name="measurement_empty_supporting_generic">Record measurements once and reuse them on every order.</string>
    <string name="measurement_empty_add_button">Add measurement</string>
```

- [ ] **Step 2: Implement the empty-state UI in `MeasurementDetailScreen.kt`**

(a) Top-bar title (lines 176-185): empty mode shows the customer's name:

```kotlin
                title = {
                    Text(
                        text = when {
                            state.isEmptyState ->
                                state.customer?.name ?: stringResource(Res.string.measurement_detail_title)
                            else -> measurement?.name?.ifBlank { null }
                                ?: stringResource(Res.string.measurement_detail_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
```

(b) Hide the overflow menu (rename/delete) in empty mode (line 194):

```kotlin
                actions = { if (!state.isEmptyState) DetailOverflowMenu(onAction) },
```

(The bottom bar — Edit/Share — already hides itself: it renders only when `measurement != null`.)

(c) Body `when` (lines 235-246) — add the empty branch between loading and content:

```kotlin
            state.isEmptyState -> MeasurementEmptyContent(
                customerFirstName = state.customer?.name?.trim()
                    ?.substringBefore(' ')?.takeIf { it.isNotBlank() },
                onAddClick = { onAction(MeasurementDetailAction.OnAddMeasurementClick) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
```

(d) New private composable (place after `MeasurementDetailContent`). Mirrors `CustomerDetailScreen.kt`'s `MeasurementsEmptyState` visual language (64dp `primaryContainer` box, `radiusXl`, `Straighten`):

```kotlin
@Composable
private fun MeasurementEmptyContent(
    customerFirstName: String?,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = DesignTokens.space4),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(DesignTokens.radiusXl),
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(DesignTokens.space4))
        Text(
            text = stringResource(Res.string.measurement_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = if (customerFirstName != null) {
                stringResource(Res.string.measurement_empty_supporting, customerFirstName)
            } else {
                stringResource(Res.string.measurement_empty_supporting_generic)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DesignTokens.space4))
        StitchPadButton(
            text = stringResource(Res.string.measurement_empty_add_button),
            onClick = onAddClick,
            leadingIcon = Icons.Default.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

New imports needed: `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height`, `androidx.compose.material.icons.filled.Add`, `androidx.compose.material.icons.filled.Straighten`, `androidx.compose.ui.text.style.TextAlign`, `com.danzucker.stitchpad.core.domain.model.Customer`, `com.danzucker.stitchpad.core.domain.model.CustomerSlotState`, plus the four generated string refs (`measurement_empty_title`, `measurement_empty_supporting`, `measurement_empty_supporting_generic`, `measurement_empty_add_button`).

(e) Two previews at the end of the file (the file already has `@file:Suppress("TooManyFunctions")`):

```kotlin
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailEmptyPreview() {
    StitchPadTheme {
        MeasurementDetailScreen(
            state = MeasurementDetailState(
                customer = Customer(
                    id = "c1",
                    userId = "u1",
                    name = "Fola Adeyemi",
                    phone = "0705 991 2340",
                    slotState = CustomerSlotState.ACTIVE,
                ),
                isEmptyState = true,
                isLoading = false,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        MeasurementDetailScreen(
            state = MeasurementDetailState(
                customer = Customer(
                    id = "c1",
                    userId = "u1",
                    name = "Fola Adeyemi",
                    phone = "0705 991 2340",
                    slotState = CustomerSlotState.ACTIVE,
                ),
                isEmptyState = true,
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
```

If the `Customer` constructor requires more parameters than shown, copy the argument set used by `fakeCustomer()` in `MeasurementDetailViewModelTest.kt` (id, userId, name, phone, slotState).

- [ ] **Step 3: Verify it compiles and detekt passes**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL
Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt
git commit -m "feat(measurement): empty-state hero UI on measurement detail

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Resolver — shared decision function; actions-sheet path routes zero to empty mode

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/MeasurementEntryResolver.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListEvent.kt:10`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt:120`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModel.kt` (KDoc of `viewMeasurementsFromSheet` only)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/MeasurementEntryResolverTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModelTest.kt`

**Interfaces:**
- Consumes: `MeasurementEntryDestination.Detail(customerId, measurementId: String?)` (this task makes the field nullable).
- Produces (Task 4 relies on this exact signature):
  - `MeasurementEntryResolver.destinationFor(customerId: String, measurementCount: Int?, singleMeasurementId: String?): MeasurementEntryDestination` — companion-object function; `singleMeasurementId != null` → `Detail(customerId, id)`; `measurementCount == 0` → `Detail(customerId, null)`; anything else (several, or null count = unknown) → `CustomerDetail(customerId)`.

- [ ] **Step 1: Update/add the failing tests**

In `MeasurementEntryResolverTest.kt`, replace the `zero measurements resolve to customer detail` test with:

```kotlin
    @Test
    fun `zero measurements resolve to empty-mode detail`() = runTest {
        measurementRepository.measurementsList = emptyList()
        assertEquals(
            MeasurementEntryDestination.Detail("customer-1", measurementId = null),
            resolver.resolve("customer-1"),
        )
    }
```

Keep `repository error resolves to customer detail`, `signed-out user resolves to customer detail`, and the timeout test EXACTLY as they are — they now pin the "unknown must not look like confirmed zero" rule. Add pure decision-function tests:

```kotlin
    @Test
    fun `destinationFor single measurement routes to its detail`() {
        assertEquals(
            MeasurementEntryDestination.Detail("c1", "m1"),
            MeasurementEntryResolver.destinationFor("c1", measurementCount = 1, singleMeasurementId = "m1"),
        )
    }

    @Test
    fun `destinationFor confirmed zero routes to empty-mode detail`() {
        assertEquals(
            MeasurementEntryDestination.Detail("c1", measurementId = null),
            MeasurementEntryResolver.destinationFor("c1", measurementCount = 0, singleMeasurementId = null),
        )
    }

    @Test
    fun `destinationFor several measurements routes to customer detail`() {
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("c1"),
            MeasurementEntryResolver.destinationFor("c1", measurementCount = 3, singleMeasurementId = null),
        )
    }

    @Test
    fun `destinationFor unknown count routes to customer detail`() {
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("c1"),
            MeasurementEntryResolver.destinationFor("c1", measurementCount = null, singleMeasurementId = null),
        )
    }
```

In `CustomerListViewModelTest.kt`, replace `view measurements from sheet with none navigates to customer detail` with (keep the surrounding setup lines identical to the current test):

```kotlin
    @Test
    fun `view measurements from sheet with none navigates to empty-mode detail`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(fakeCustomer())
        measurementRepository.measurementsList = emptyList()
        val vm = createViewModel()

        vm.events.test {
            vm.onAction(CustomerListAction.OnViewMeasurementsFromRow("customer-1"))
            advanceTimeBy(451)
            runCurrent()
            val event = assertIs<CustomerListEvent.NavigateToMeasurementDetail>(awaitItem())
            assertEquals("customer-1", event.customerId)
            assertNull(event.measurementId)
        }
    }
```

(Add `import kotlin.test.assertNull` if not present.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolverTest" --tests "com.danzucker.stitchpad.feature.customer.presentation.list.CustomerListViewModelTest"`
Expected: FAIL — `destinationFor` unresolved; nullable `Detail.measurementId` compile errors.

- [ ] **Step 3: Implement the resolver changes**

Replace `MeasurementEntryResolver.kt`'s body (keep package/imports; imports are unchanged):

```kotlin
/** Where a "view measurements" entry point should land for a customer. */
sealed interface MeasurementEntryDestination {
    /** null [measurementId] = the detail screen's empty mode (customer confirmed to have zero measurements). */
    data class Detail(val customerId: String, val measurementId: String?) : MeasurementEntryDestination
    data class CustomerDetail(val customerId: String) : MeasurementEntryDestination
}

/**
 * Fetches the measurement snapshot for the actions-sheet entry point and applies
 * the shared [destinationFor] routing rule. Callers that already know the count
 * (the dashboard picker) call [destinationFor] directly instead.
 */
class MeasurementEntryResolver(
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun resolve(customerId: String): MeasurementEntryDestination {
        val measurements = firstSnapshotOrNull(customerId)
        return destinationFor(
            customerId = customerId,
            measurementCount = measurements?.size,
            singleMeasurementId = measurements?.singleOrNull()?.id,
        )
    }

    /**
     * First measurement snapshot, or null when it can't be had: signed-out, a repo
     * error, or the snapshot never arrives (cold cache with no network can leave the
     * Firestore flow pending indefinitely — the wait is bounded so the tap never
     * silently dies; the sheet that triggered it is already dismissed). Null means
     * UNKNOWN — it must never read as "confirmed zero", which would show the empty
     * state for a customer who may well have measurements.
     */
    private suspend fun firstSnapshotOrNull(customerId: String): List<Measurement>? {
        val userId = authRepository.getCurrentUser()?.id ?: return null
        val result = withTimeoutOrNull(FIRST_SNAPSHOT_TIMEOUT_MS) {
            measurementRepository.observeMeasurements(userId, customerId).first()
        }
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> null
            null -> null
        }
    }

    companion object {
        /**
         * The shared routing rule for measurement entry points (spec): exactly one
         * measurement opens its detail; a confirmed zero opens the detail screen's
         * empty state; several land on customer detail (whose measurements section
         * is the list). Unknown count (error, timeout, signed-out) falls back to
         * customer detail — never a dead end, never a false empty state.
         */
        fun destinationFor(
            customerId: String,
            measurementCount: Int?,
            singleMeasurementId: String?,
        ): MeasurementEntryDestination = when {
            singleMeasurementId != null ->
                MeasurementEntryDestination.Detail(customerId, singleMeasurementId)
            measurementCount == 0 ->
                MeasurementEntryDestination.Detail(customerId, measurementId = null)
            else -> MeasurementEntryDestination.CustomerDetail(customerId)
        }

        private const val FIRST_SNAPSHOT_TIMEOUT_MS = 3_000L
    }
}
```

- [ ] **Step 4: Ripple the nullable measurementId through the customer-list path**

`CustomerListEvent.kt:10`:

```kotlin
    data class NavigateToMeasurementDetail(val customerId: String, val measurementId: String?) : CustomerListEvent
```

`CustomerListScreen.kt:120` — callback type becomes:

```kotlin
    onNavigateToMeasurementDetail: (String, String?) -> Unit,
```

(The event pass-through at lines 136-137 and `CustomerListViewModel.viewMeasurementsFromSheet` compile unchanged. `MainScreen.kt:239-247`'s lambda infers the nullable type and `MeasurementDetailRoute(measurementId = ...)` already accepts `String?`.)

Update the `viewMeasurementsFromSheet` KDoc in `CustomerListViewModel.kt` — replace "(exactly one measurement -> its detail; zero or several -> customer detail)" with "(exactly one measurement -> its detail; confirmed zero -> the detail empty state; several or unknown -> customer detail)".

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolverTest" --tests "com.danzucker.stitchpad.feature.customer.presentation.list.CustomerListViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/MeasurementEntryResolver.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/ \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/MeasurementEntryResolverTest.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModelTest.kt
git commit -m "feat(measurement): shared entry routing rule; actions sheet zero -> empty-mode detail

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Dashboard picker — apply the shared rule, remove the zero→form shortcut

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardEvent.kt:50-53`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt:411-426`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt:307-308,362-363,498-499,532-534`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt:537-548`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModelTest.kt:1720-1739`

**Interfaces:**
- Consumes: `MeasurementEntryResolver.destinationFor(customerId, measurementCount, singleMeasurementId)` and `MeasurementEntryDestination` (Task 3).
- Produces: `DashboardEvent.NavigateToMeasurementDetail(customerId: String, measurementId: String?)`; `DashboardEvent.NavigateToAddMeasurement` DELETED (its only emitter was the picker zero case).

- [ ] **Step 1: Update the failing test**

In `DashboardViewModelTest.kt`, replace `picker row with zero measurements navigates to add measurement` (lines ~1720-1739) with:

```kotlin
    @Test
    fun `picker row with zero measurements navigates to empty-mode detail`() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnMeasurementsShortcutClick)

        vm.events.test {
            vm.onAction(
                DashboardAction.OnMeasurementsPickerRowClick(
                    MeasurementsPickerRow("c1", "Chidinma", measurementCount = 0, singleMeasurementId = null),
                ),
            )
            advanceTimeBy(451)
            runCurrent()
            val event = assertIs<DashboardEvent.NavigateToMeasurementDetail>(awaitItem())
            assertEquals("c1", event.customerId)
            assertNull(event.measurementId)
        }
    }
```

(Add `import kotlin.test.assertNull` if not present. The `errored row -> customer detail` and `several -> customer detail` tests stay untouched.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.presentation.DashboardViewModelTest"`
Expected: FAIL — the zero row still emits `NavigateToAddMeasurement`.

- [ ] **Step 3: Implement the dashboard changes**

`DashboardEvent.kt` — make the id nullable and delete the add event:

```kotlin
    data class NavigateToMeasurementDetail(val customerId: String, val measurementId: String?) : DashboardEvent
```

Delete `data class NavigateToAddMeasurement(val customerId: String) : DashboardEvent` (line 53) and any KDoc attached only to it.

`DashboardViewModel.kt` — replace `onMeasurementsPickerRowClick` (lines 411-426) with:

```kotlin
    private fun onMeasurementsPickerRowClick(row: MeasurementsPickerRow) {
        _state.update { it.copy(measurementsPicker = null) }
        viewModelScope.launch {
            delay(PICKER_DISMISS_DELAY_MS)
            // Same rule as the customer actions sheet — including that an unknown
            // count (fetch failed/timed out) must not masquerade as "no measurements"
            // and show a false empty state (Bugbot, PR #261).
            val destination = MeasurementEntryResolver.destinationFor(
                customerId = row.customerId,
                measurementCount = row.measurementCount,
                singleMeasurementId = row.singleMeasurementId,
            )
            val event = when (destination) {
                is MeasurementEntryDestination.Detail ->
                    DashboardEvent.NavigateToMeasurementDetail(destination.customerId, destination.measurementId)
                is MeasurementEntryDestination.CustomerDetail ->
                    DashboardEvent.NavigateToCustomerDetail(destination.customerId)
            }
            emitEvent(event)
        }
    }
```

Add imports to `DashboardViewModel.kt`:

```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryDestination
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolver
```

`DashboardScreen.kt`:
- Line 307 and 498: `onNavigateToMeasurementDetail: (customerId: String, measurementId: String?) -> Unit,`
- Delete the `onNavigateToAddMeasurementForCustomer` parameter (lines 308, 499), its pass-through (line 363), and the event branch (lines 533-534). Check the whole file (and any dashboard previews) for further references to the deleted parameter/event and remove them.

`MainScreen.kt` — delete the `onNavigateToAddMeasurementForCustomer = { ... }` block (lines 546-548). The `onNavigateToMeasurementDetail` wiring (537-545) stays as-is.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.presentation.DashboardViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/ \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModelTest.kt
git commit -m "feat(dashboard): measurements picker zero case routes to empty-mode detail

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Full verification — detekt, all tests, Android build, iOS compile

**Files:** none (verification only; fix-forward anything that fails, amend into the responsible task's commit or add a fix commit).

- [ ] **Step 1: Full unit-test suite** (backtick test names can pass filtered runs but fail elsewhere — the full run is the gate)

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 2: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. If `TooManyFunctions` fires on `MeasurementDetailViewModel`, a member function was added in violation of the global constraint — inline it rather than suppressing.

- [ ] **Step 3: Android build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: iOS compile** (KMP code that compiles on Android can still fail iOS)

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. If the task name doesn't exist, list candidates with `./gradlew :composeApp:tasks --all` and run the iosSimulatorArm64 compile task.

- [ ] **Step 5: Commit any verification fixes**

Only if fixes were needed:

```bash
git add -A composeApp/src
git commit -m "fix(measurement): verification fixes for empty-state routing

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Manual smoke test (PR body — Daniel is QA)

1. Customer with zero measurements → customer list → actions sheet → View Measurements → empty state (customer name in top bar, hero + "Add measurement", NO share/edit/delete/overflow).
2. Same via Dashboard → Measurements shortcut → picker row with "+ Add"/zero count.
3. From the empty state tap Add measurement → form → save → detail shows the new measurement with "Saved" snackbar → Back returns to the originating screen (customer list or dashboard), NOT the empty state or the form.
4. From the empty state tap Back → returns to the originating screen.
5. Customer with exactly one measurement → unchanged (straight to its detail).
6. Customer with several measurements → unchanged (Customer Detail).
7. Locked customer with zero measurements → Add CTA routes to Upgrade.
8. Airplane mode + fresh app start → dashboard picker rows with unknown counts still route to Customer Detail (no false empty state).
9. Repeat 1-3 in dark mode.

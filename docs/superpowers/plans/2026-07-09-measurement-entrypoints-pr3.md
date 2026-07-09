# Measurement Entry Points (PR 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two labeled entry points to a customer's measurements — a "Measurements" row in the Dashboard's Quick access section (opening a customer-picker sheet with measurement counts) and a "View measurements" row in the customer list's actions sheet.

**Architecture:** A small shared `MeasurementEntryResolver` (one on-demand query, no denormalized fields) implements the routing rule — exactly 1 measurement → `MeasurementDetailRoute`, otherwise → `CustomerDetailRoute` — used by the customer-actions-sheet path; the Dashboard picker fetches per-customer counts when the sheet opens (freemium caps keep N small, reads hit the local cache) and routes from the counts it already holds, including "+ Add" directly to the measurement form for zero-count customers. Both paths tag `MeasurementDetailViewed` with new sources.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, Koin, kotlin.test + Turbine, detekt.

**Spec:** `docs/superpowers/specs/2026-07-08-measurement-detail-view-design.md` (Entry points section). Mockup: `preview/measurement-visibility-redesign.html` phones A/A2. Branch context: PR 1 merged (#255), PR 2 in QA (#260); this branch (`feat/measurement-entrypoints`) is stacked on `feat/measurement-sharing`.

## Global Constraints

- Worktree `/Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/measurement-detail-view`, branch **`feat/measurement-entrypoints`**. All commands from the worktree root. Commit per task; do NOT push.
- Routing rule (shared semantics everywhere): exactly 1 measurement → `MeasurementDetailRoute(customerId, measurementId, source)`; 0 or >1 → `CustomerDetailRoute(customerId)` — except the Dashboard picker's zero-count rows, which offer "+ Add" straight to `MeasurementFormRoute(customerId)` (spec-sanctioned; the picker already knows the count).
- New analytics sources (exact strings): `MeasurementDetailSource.DASHBOARD = "dashboard"`, `MeasurementDetailSource.CUSTOMER_ACTIONS_SHEET = "customer_actions_sheet"`.
- Sheet-dismiss navigation delay: any navigation emitted after closing a `ModalBottomSheet` waits `450L` ms (`CustomerListViewModel.navigateFromSheet` precedent — iOS modal-dismiss timing).
- Count copy must be grammatical (Bugbot catches plural drift): use TWO strings, `measurements_picker_count_one` ("1 measurement") and `measurements_picker_count_many` ("%1$d measurements" — positional arg), picked by `count == 1`.
- MVI; all state in ViewModels (picker search query included); strings in strings.xml; light+dark previews for new sheet content; KMP-safe; backtick test names without parens; never pipe gradle output.
- Debug-menu evaluation (per-feature rule): considered and SKIPPED — entry points need no time/entitlement/state manipulation; testers create measurements in-app.

---

### Task 1: `MeasurementEntryResolver` (shared routing rule) + DI + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/MeasurementEntryResolver.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt` (add `singleOf(::MeasurementEntryResolver)` to `measurementPresentationModule`, with import)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailEvent.kt` (add the two source constants to `MeasurementDetailSource`)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/MeasurementEntryResolverTest.kt`

**Interfaces:**
- Consumes: `MeasurementRepository.observeMeasurements(userId, customerId): Flow<Result<List<Measurement>, DataError.Network>>`, `AuthRepository.getCurrentUser()?.id`.
- Produces (Tasks 2–3 rely on):

```kotlin
sealed interface MeasurementEntryDestination {
    data class Detail(val customerId: String, val measurementId: String) : MeasurementEntryDestination
    data class CustomerDetail(val customerId: String) : MeasurementEntryDestination
}
class MeasurementEntryResolver(
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun resolve(customerId: String): MeasurementEntryDestination
}
```
Plus `MeasurementDetailSource.DASHBOARD` / `.CUSTOMER_ACTIONS_SHEET`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.entry

import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementEntryResolverTest {

    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var resolver: MeasurementEntryResolver

    @BeforeTest
    fun setUp() {
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository()
        authRepository.currentUser = User(
            id = "user-1",
            email = "tailor@example.com",
            displayName = "Test Tailor",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
        resolver = MeasurementEntryResolver(measurementRepository, authRepository)
    }

    private fun measurement(id: String) = Measurement(
        id = id, customerId = "customer-1", gender = CustomerGender.FEMALE, name = "M",
        fields = mapOf("waist" to 31.0), unit = MeasurementUnit.INCHES, notes = null,
        dateTaken = 1L, createdAt = 1L,
    )

    @Test
    fun `single measurement resolves to detail`() = runTest {
        measurementRepository.measurementsList = listOf(measurement("meas-1"))
        assertEquals(
            MeasurementEntryDestination.Detail("customer-1", "meas-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `zero measurements resolve to customer detail`() = runTest {
        measurementRepository.measurementsList = emptyList()
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `multiple measurements resolve to customer detail`() = runTest {
        measurementRepository.measurementsList = listOf(measurement("m1"), measurement("m2"))
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `repository error resolves to customer detail`() = runTest {
        measurementRepository.observeError = DataError.Network.UNKNOWN
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `signed-out user resolves to customer detail`() = runTest {
        authRepository.currentUser = null
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }
}
```
(Check `FakeAuthRepository` — if `currentUser` is non-nullable or named differently, mirror how `MeasurementDetailViewModelTest` seeds and clears it.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementEntryResolverTest*"`
Expected: FAIL — unresolved reference `MeasurementEntryResolver`.

- [ ] **Step 3: Implement**

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.entry

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.first

/** Where a "view measurements" entry point should land for a customer. */
sealed interface MeasurementEntryDestination {
    data class Detail(val customerId: String, val measurementId: String) : MeasurementEntryDestination
    data class CustomerDetail(val customerId: String) : MeasurementEntryDestination
}

/**
 * The shared routing rule for measurement entry points (spec): exactly one
 * measurement opens it directly; zero or several land on customer detail
 * (whose measurements section handles both). Errors and signed-out fall back
 * to customer detail — never a dead end.
 */
class MeasurementEntryResolver(
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun resolve(customerId: String): MeasurementEntryDestination {
        val userId = authRepository.getCurrentUser()?.id
            ?: return MeasurementEntryDestination.CustomerDetail(customerId)
        val measurements = when (val result = measurementRepository.observeMeasurements(userId, customerId).first()) {
            is Result.Success -> result.data
            is Result.Error -> emptyList()
        }
        return when (measurements.size) {
            1 -> MeasurementEntryDestination.Detail(customerId, measurements.single().id)
            else -> MeasurementEntryDestination.CustomerDetail(customerId)
        }
    }
}
```

In `MeasurementDetailEvent.kt`, extend the constants:
```kotlin
object MeasurementDetailSource {
    const val CUSTOMER_DETAIL = "customer_detail"
    const val ORDER_DETAIL = "order_detail"
    const val POST_SAVE = "post_save"
    const val DASHBOARD = "dashboard"
    const val CUSTOMER_ACTIONS_SHEET = "customer_actions_sheet"
}
```

In `MeasurementModule.kt`: `singleOf(::MeasurementEntryResolver)` inside `measurementPresentationModule` + import `com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolver` (and `org.koin.core.module.dsl.singleOf` is already imported there).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementEntryResolverTest*"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailEvent.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/entry/
git commit -m "feat(measurement): shared entry-point routing resolver + new analytics sources"
```

---

### Task 2: "View measurements" in the customer actions sheet

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListAction.kt`
- Modify: `.../list/CustomerListEvent.kt`
- Modify: `.../list/CustomerListViewModel.kt`
- Modify: `.../list/CustomerListScreen.kt` (Root event arm + sheet call site)
- Modify: `.../list/components/CustomerActionsSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` (`composable<CustomerListRoute>` block)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CustomerModule.kt` (only if `CustomerListViewModel` is registered via `viewModelOf` — the new ctor param resolves via `get()` automatically; just verify it compiles)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: extend `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListViewModelTest.kt` (find the actual test file name with `find composeApp/src/commonTest -path "*customer/presentation/list*"` — if none exists, create `CustomerListViewModelTest.kt` with only the fixture needed for these tests, mirroring `CustomerDetailViewModelTest`'s setup style)

**Interfaces:**
- Consumes: Task 1's `MeasurementEntryResolver` / `MeasurementEntryDestination` / `MeasurementDetailSource.CUSTOMER_ACTIONS_SHEET`; existing `navigateFromSheet` pattern (`CustomerListViewModel.kt:301-307`, `SHEET_DISMISS_DELAY_MS = 450L`).
- Produces: `CustomerListAction.OnViewMeasurementsFromRow(customerId: String)`; `CustomerListEvent.NavigateToMeasurementDetail(customerId: String, measurementId: String)`; `CustomerActionsSheet` gains `onViewMeasurements: (String) -> Unit`.

- [ ] **Step 1: Write the failing tests**

Add to the customer-list VM test file (reuse/mirror its fixture; the resolver is constructed from the same fakes):

```kotlin
@Test
fun `view measurements from sheet with one measurement navigates to detail`() = runTest {
    customerRepository.customersList = listOf(fakeCustomer())
    measurementRepository.measurementsList = listOf(fakeMeasurement(id = "meas-1"))
    val vm = createViewModel()
    vm.onAction(CustomerListAction.OnOverflowClick(fakeCustomer()))
    vm.events.test {
        vm.onAction(CustomerListAction.OnViewMeasurementsFromRow("customer-1"))
        val event = assertIs<CustomerListEvent.NavigateToMeasurementDetail>(awaitItem())
        assertEquals("customer-1", event.customerId)
        assertEquals("meas-1", event.measurementId)
    }
    assertNull(vm.state.value.actionsSheetForId)
}

@Test
fun `view measurements from sheet with several measurements navigates to customer detail`() = runTest {
    customerRepository.customersList = listOf(fakeCustomer())
    measurementRepository.measurementsList = listOf(fakeMeasurement(id = "m1"), fakeMeasurement(id = "m2"))
    val vm = createViewModel()
    vm.events.test {
        vm.onAction(CustomerListAction.OnViewMeasurementsFromRow("customer-1"))
        val event = assertIs<CustomerListEvent.NavigateToCustomerDetail>(awaitItem())
        assertEquals("customer-1", event.customerId)
    }
}

@Test
fun `view measurements from sheet with none navigates to customer detail`() = runTest {
    customerRepository.customersList = listOf(fakeCustomer())
    measurementRepository.measurementsList = emptyList()
    val vm = createViewModel()
    vm.events.test {
        vm.onAction(CustomerListAction.OnViewMeasurementsFromRow("customer-1"))
        assertIs<CustomerListEvent.NavigateToCustomerDetail>(awaitItem())
    }
}
```
Adapt helper names (`fakeCustomer`, `fakeMeasurement`, `createViewModel`) to the file's existing fixture; add a `FakeMeasurementRepository` field to the fixture and pass `MeasurementEntryResolver(measurementRepository, authRepository)` as the new ctor arg. NOTE: the existing tests may use `advanceTimeBy` around `navigateFromSheet`'s 450ms delay — if `awaitItem()` hangs, mirror how existing sheet-navigation tests in this file handle the delay (e.g. `advanceTimeBy(451)` before awaiting, or `runCurrent()`; with `UnconfinedTestDispatcher` + `runTest`, virtual time auto-advances on `awaitItem`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomerListViewModelTest*"`
Expected: new tests FAIL (unresolved `OnViewMeasurementsFromRow`); existing tests still pass.

- [ ] **Step 3: Implement**

`CustomerListAction.kt` — next to `OnAddMeasurementFromRow`:
```kotlin
    data class OnViewMeasurementsFromRow(val customerId: String) : CustomerListAction
```

`CustomerListEvent.kt` — next to `NavigateToAddMeasurement`:
```kotlin
    data class NavigateToMeasurementDetail(val customerId: String, val measurementId: String) : CustomerListEvent
```

`CustomerListViewModel.kt` — new last ctor param `private val measurementEntryResolver: MeasurementEntryResolver`; in `onAction`, next to the other sheet arms:
```kotlin
    is CustomerListAction.OnViewMeasurementsFromRow -> viewMeasurementsFromSheet(action.customerId)
```
and the handler (mirrors `navigateFromSheet`'s dismiss-then-delay contract; resolution happens during the dismiss animation):
```kotlin
    private fun viewMeasurementsFromSheet(customerId: String) {
        _state.update { it.copy(actionsSheetForId = null) }
        viewModelScope.launch {
            val destination = measurementEntryResolver.resolve(customerId)
            delay(SHEET_DISMISS_DELAY_MS)
            val event = when (destination) {
                is MeasurementEntryDestination.Detail ->
                    CustomerListEvent.NavigateToMeasurementDetail(destination.customerId, destination.measurementId)
                is MeasurementEntryDestination.CustomerDetail ->
                    CustomerListEvent.NavigateToCustomerDetail(destination.customerId)
            }
            _events.send(event)
        }
    }
```
(Match the event-channel member name used by the file — check whether it's `_events.send` or an `eventChannel`; follow the file.)

`CustomerActionsSheet.kt` — new param after `onEdit`: `onViewMeasurements: (String) -> Unit`; new `ActionRow` placed BETWEEN `customer_actions_edit` and `customer_actions_new_measurement`:
```kotlin
        ActionRow(
            icon = Icons.Default.Visibility,
            label = stringResource(Res.string.customer_actions_view_measurements),
            onClick = { onViewMeasurements(customer.id) },
        )
```
(import `Icons.Default.Visibility` + the new string; also thread the param through `CustomerActionsSheetContent` and update the file's previews with `onViewMeasurements = {}`).

`CustomerListScreen.kt` — sheet call site adds `onViewMeasurements = { id -> onAction(CustomerListAction.OnViewMeasurementsFromRow(id)) }`; `CustomerListRoot` gains `onNavigateToMeasurementDetail: (String, String) -> Unit` and the event arm:
```kotlin
    is CustomerListEvent.NavigateToMeasurementDetail ->
        onNavigateToMeasurementDetail(event.customerId, event.measurementId)
```

`MainScreen.kt` `composable<CustomerListRoute>`:
```kotlin
onNavigateToMeasurementDetail = { customerId, measurementId ->
    navController.navigate(
        MeasurementDetailRoute(
            customerId = customerId,
            measurementId = measurementId,
            source = MeasurementDetailSource.CUSTOMER_ACTIONS_SHEET,
        ),
    )
},
```

`strings.xml` — next to `customer_actions_new_measurement`:
```xml
    <string name="customer_actions_view_measurements">View measurements</string>
```

- [ ] **Step 4: Run tests + compile**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*CustomerListViewModelTest*" :composeApp:compileDebugKotlinAndroid detekt`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(customer): View measurements action in the customer actions sheet"
```

---

### Task 3: Dashboard picker — ViewModel side

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardState.kt`
- Modify: `.../dashboard/presentation/DashboardAction.kt`
- Modify: `.../dashboard/presentation/DashboardEvent.kt`
- Modify: `.../dashboard/presentation/DashboardViewModel.kt`
- Create: `.../dashboard/presentation/model/MeasurementsPickerUi.kt`
- Test: extend `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModelTest.kt` (fixture exists at line 86 — extend it)

**Interfaces:**
- Consumes: `MeasurementRepository` (new VM dep), the customer list already flowing through `loadData()`'s combine (`DashboardViewModel.kt:345,374` — capture it in a private `var latestCustomers: List<Customer>` inside the collect), `MeasurementDetailSource.DASHBOARD`.
- Produces (Task 4 relies on):

```kotlin
// model/MeasurementsPickerUi.kt
data class MeasurementsPickerRow(
    val customerId: String,
    val name: String,
    val measurementCount: Int,
    /** Set only when measurementCount == 1 — enables direct routing to the detail view. */
    val singleMeasurementId: String?,
)
data class MeasurementsPickerUi(
    val isLoading: Boolean = true,
    val query: String = "",
    val rows: List<MeasurementsPickerRow> = emptyList(),
) {
    val filteredRows: List<MeasurementsPickerRow>
        get() = if (query.isBlank()) rows else rows.filter { it.name.contains(query, ignoreCase = true) }
}
```
- `DashboardState` gains `measurementsPicker: MeasurementsPickerUi? = null` (null = sheet closed).
- Actions: `OnMeasurementsShortcutClick`, `OnMeasurementsPickerQueryChange(query: String)`, `OnMeasurementsPickerRowClick(row: MeasurementsPickerRow)`, `OnDismissMeasurementsPicker`.
- Events: `NavigateToMeasurementDetail(customerId: String, measurementId: String)`, `NavigateToAddMeasurement(customerId: String)` (reuse existing `NavigateToCustomerDetail`, `NavigateToCustomers`).

- [ ] **Step 1: Write the failing tests**

Add to `DashboardViewModelTest` (reuse its fixture/`createViewModel`; add a `FakeMeasurementRepository` field wired as the new ctor arg — seed customers via whatever fake the fixture already uses for `customerRepository`):

```kotlin
@Test
fun `measurements shortcut opens picker with counts sorted has-measurements first`() = runTest {
    customerRepository.customersList = listOf(
        fakeCustomer(id = "c-empty", name = "Bola"),
        fakeCustomer(id = "c-one", name = "Chidinma"),
    )
    measurementRepository.measurementsForCustomer["c-one"] = listOf(fakeMeasurement(id = "meas-1", customerId = "c-one"))
    val vm = createViewModel()
    vm.onAction(DashboardAction.OnMeasurementsShortcutClick)
    val picker = vm.state.value.measurementsPicker
    assertNotNull(picker)
    assertEquals(false, picker.isLoading)
    assertEquals(listOf("Chidinma", "Bola"), picker.rows.map { it.name })
    assertEquals(1, picker.rows[0].measurementCount)
    assertEquals("meas-1", picker.rows[0].singleMeasurementId)
    assertEquals(0, picker.rows[1].measurementCount)
}

@Test
fun `picker row with one measurement navigates to detail and closes picker`() = runTest {
    val vm = createViewModel()
    vm.onAction(DashboardAction.OnMeasurementsShortcutClick)
    vm.events.test {
        vm.onAction(
            DashboardAction.OnMeasurementsPickerRowClick(
                MeasurementsPickerRow("c1", "Chidinma", measurementCount = 1, singleMeasurementId = "meas-1"),
            ),
        )
        val event = assertIs<DashboardEvent.NavigateToMeasurementDetail>(awaitItem())
        assertEquals("c1" to "meas-1", event.customerId to event.measurementId)
    }
    assertNull(vm.state.value.measurementsPicker)
}

@Test
fun `picker row with several measurements navigates to customer detail`() = runTest {
    val vm = createViewModel()
    vm.onAction(DashboardAction.OnMeasurementsShortcutClick)
    vm.events.test {
        vm.onAction(
            DashboardAction.OnMeasurementsPickerRowClick(
                MeasurementsPickerRow("c1", "Chidinma", measurementCount = 3, singleMeasurementId = null),
            ),
        )
        assertIs<DashboardEvent.NavigateToCustomerDetail>(awaitItem())
    }
}

@Test
fun `picker row with zero measurements navigates to add measurement`() = runTest {
    val vm = createViewModel()
    vm.onAction(DashboardAction.OnMeasurementsShortcutClick)
    vm.events.test {
        vm.onAction(
            DashboardAction.OnMeasurementsPickerRowClick(
                MeasurementsPickerRow("c1", "Chidinma", measurementCount = 0, singleMeasurementId = null),
            ),
        )
        val event = assertIs<DashboardEvent.NavigateToAddMeasurement>(awaitItem())
        assertEquals("c1", event.customerId)
    }
}

@Test
fun `picker query filters rows case-insensitively`() = runTest {
    val vm = createViewModel()
    vm.onAction(DashboardAction.OnMeasurementsShortcutClick)
    vm.onAction(DashboardAction.OnMeasurementsPickerQueryChange("chi"))
    assertEquals("chi", vm.state.value.measurementsPicker?.query)
}
```
IMPORTANT fixture note: `FakeMeasurementRepository.measurementsList` is a single flat list — it does NOT key by customer. For the counts test you need per-customer lists: check the fake first; if it has no per-customer support, EXTEND `FakeMeasurementRepository` (commonTest) with an optional `val measurementsForCustomer: MutableMap<String, List<Measurement>> = mutableMapOf()` and make `observeMeasurements` return `measurementsForCustomer[customerId]` when the map has the key, else the existing flat-list behavior — a backward-compatible addition; existing tests keep passing. Also adapt seeded-customer/measurement helper names to the fixture's own (`fakeCustomer(id, name)` may need adding as a local helper). If the Dashboard fixture's `createViewModel` uses named args for all deps, add `measurementRepository = measurementRepository` in ctor order.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DashboardViewModelTest*"`
Expected: new tests FAIL (unresolved references); existing tests pass.

- [ ] **Step 3: Implement**

`DashboardState.kt`: add `val measurementsPicker: MeasurementsPickerUi? = null,`.

`DashboardAction.kt` (next to `OnAddMeasurementClick`):
```kotlin
    data object OnMeasurementsShortcutClick : DashboardAction
    data class OnMeasurementsPickerQueryChange(val query: String) : DashboardAction
    data class OnMeasurementsPickerRowClick(val row: MeasurementsPickerRow) : DashboardAction
    data object OnDismissMeasurementsPicker : DashboardAction
```

`DashboardEvent.kt`:
```kotlin
    data class NavigateToMeasurementDetail(val customerId: String, val measurementId: String) : DashboardEvent
    data class NavigateToAddMeasurement(val customerId: String) : DashboardEvent
```

`DashboardViewModel.kt`:
- New ctor dep (place after `customerRepository`): `private val measurementRepository: MeasurementRepository,` — Koin `viewModelOf`/lambda registration resolves it; verify the module (`DashboardModule.kt` or wherever `DashboardViewModel` is registered) compiles; if it uses an explicit lambda, add the `get()` in position.
- Private `var latestCustomers: List<Customer> = emptyList()` set inside the `loadData()` combine collect right where `customers` is available (line ~374): `latestCustomers = customers`.
- Action arms:
```kotlin
    DashboardAction.OnMeasurementsShortcutClick -> openMeasurementsPicker()
    is DashboardAction.OnMeasurementsPickerQueryChange -> _state.update { s ->
        s.copy(measurementsPicker = s.measurementsPicker?.copy(query = action.query))
    }
    is DashboardAction.OnMeasurementsPickerRowClick -> onMeasurementsPickerRowClick(action.row)
    DashboardAction.OnDismissMeasurementsPicker -> _state.update { it.copy(measurementsPicker = null) }
```
(If `onAction`'s `when` is at a detekt complexity threshold, follow the file's existing suppression/extraction pattern with an honest comment.)
- Handlers:
```kotlin
    private fun openMeasurementsPicker() {
        // No customers yet — same affordance as the "Measurement" tile: go create one.
        if (latestCustomers.isEmpty()) {
            onAction(DashboardAction.OnAddMeasurementClick)
            return
        }
        _state.update { it.copy(measurementsPicker = MeasurementsPickerUi(isLoading = true)) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(measurementsPicker = null) }
                return@launch
            }
            // One cached read per customer; freemium caps keep N small (15–200).
            val rows = latestCustomers.map { customer ->
                async {
                    val measurements = (measurementRepository
                        .observeMeasurements(userId, customer.id).first() as? Result.Success)
                        ?.data.orEmpty()
                    MeasurementsPickerRow(
                        customerId = customer.id,
                        name = customer.name,
                        measurementCount = measurements.size,
                        singleMeasurementId = measurements.singleOrNull()?.id,
                    )
                }
            }.awaitAll()
                .sortedWith(compareByDescending<MeasurementsPickerRow> { it.measurementCount > 0 }.thenBy { it.name.lowercase() })
            _state.update { current ->
                // The user may have dismissed the sheet while counts were loading.
                if (current.measurementsPicker == null) current
                else current.copy(measurementsPicker = current.measurementsPicker.copy(isLoading = false, rows = rows))
            }
        }
    }

    private fun onMeasurementsPickerRowClick(row: MeasurementsPickerRow) {
        _state.update { it.copy(measurementsPicker = null) }
        viewModelScope.launch {
            delay(PICKER_DISMISS_DELAY_MS)
            val event = when {
                row.singleMeasurementId != null ->
                    DashboardEvent.NavigateToMeasurementDetail(row.customerId, row.singleMeasurementId)
                row.measurementCount == 0 -> DashboardEvent.NavigateToAddMeasurement(row.customerId)
                else -> DashboardEvent.NavigateToCustomerDetail(row.customerId)
            }
            emitEvent(event)
        }
    }
```
with `private const val PICKER_DISMISS_DELAY_MS = 450L` (file-level or companion, matching the file's constant style; same iOS modal-dismiss rationale as `CustomerListViewModel.SHEET_DISMISS_DELAY_MS`). Imports: `kotlinx.coroutines.async`, `kotlinx.coroutines.awaitAll`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.flow.first`, `MeasurementRepository`, `Result`, `MeasurementsPickerRow/Ui`. Use the file's existing `emitEvent(...)` helper (seen at line 204) rather than a raw channel send.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DashboardViewModelTest*" detekt`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src
git commit -m "feat(dashboard): measurements picker state, counts, and routing"
```

---

### Task 4: Dashboard UI — Quick access row + picker sheet + nav wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`
- Create: `.../dashboard/presentation/components/MeasurementsPickerSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` (`composable<DashboardRoute>` block)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: Task 3's state/actions/events; `CustomerAvatar` (ui/components); `CustomerPickerSheet` (feature/smart/presentation/draft/components — STRUCTURE reference only, do not modify); the search-field styling from `CustomerListScreen.kt`'s `CustomerSearchField` (reference only); `MeasurementDetailSource.DASHBOARD`.

- [ ] **Step 1: Add strings**

```xml
    <!-- Dashboard measurements shortcut + picker -->
    <string name="dashboard_measurements_card_title">Measurements</string>
    <string name="dashboard_measurements_card_subtitle">Find a customer&apos;s numbers fast</string>
    <string name="measurements_picker_title">Measurements</string>
    <string name="measurements_picker_subtitle">Pick a customer to view their measurements</string>
    <string name="measurements_picker_count_one">1 measurement</string>
    <string name="measurements_picker_count_many">%1$d measurements</string>
    <string name="measurements_picker_none">No measurements yet</string>
    <string name="measurements_picker_add">+ Add</string>
    <string name="measurements_picker_no_results">No customers match your search</string>
```
(Search hint: reuse existing `customer_search_hint`.)

- [ ] **Step 2: Second Quick access row**

In `DashboardScreen.kt`, generalize the row: rename `InspirationShortcutRow` to a private `QuickAccessRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit)` (same chrome: bordered Surface, 42.dp primaryContainer icon chip, chevron), and have `QuickAccessSection` render two of them:
```kotlin
@Composable
private fun QuickAccessSection(
    onInspirationClick: () -> Unit,
    onMeasurementsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = "Quick access",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuickAccessRow(
            icon = Icons.Default.CollectionsBookmark,
            title = stringResource(Res.string.dashboard_inspiration_card_title),
            subtitle = stringResource(Res.string.dashboard_inspiration_card_subtitle),
            onClick = onInspirationClick,
        )
        QuickAccessRow(
            icon = Icons.Default.Straighten,
            title = stringResource(Res.string.dashboard_measurements_card_title),
            subtitle = stringResource(Res.string.dashboard_measurements_card_subtitle),
            onClick = onMeasurementsClick,
        )
    }
}
```
Call site passes `onMeasurementsClick = { onAction(DashboardAction.OnMeasurementsShortcutClick) }`. (The "Quick access" header literal is pre-existing — leave as-is, consistent with the file.)

- [ ] **Step 3: `MeasurementsPickerSheet` composable** (new file; sheet shell mirrors `CustomerPickerSheet`, search field mirrors `CustomerSearchField`'s styling)

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

// imports per DashboardScreen.kt conventions + ModalBottomSheet, CustomerAvatar,
// MeasurementsPickerUi/Row, the new strings, customer_search_hint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsPickerSheet(
    picker: MeasurementsPickerUi,
    onQueryChange: (String) -> Unit,
    onRowClick: (MeasurementsPickerRow) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MeasurementsPickerContent(picker, onQueryChange, onRowClick)
    }
}

@Composable
private fun MeasurementsPickerContent(
    picker: MeasurementsPickerUi,
    onQueryChange: (String) -> Unit,
    onRowClick: (MeasurementsPickerRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space6),
    ) {
        Text(
            text = stringResource(Res.string.measurements_picker_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.measurements_picker_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space3))
        OutlinedTextField(
            value = picker.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.customer_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(DesignTokens.space2))
        when {
            picker.isLoading -> Box(
                Modifier.fillMaxWidth().padding(vertical = DesignTokens.space6),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            picker.filteredRows.isEmpty() -> Text(
                text = stringResource(Res.string.measurements_picker_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = DesignTokens.space4),
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(picker.filteredRows, key = { it.customerId }) { row ->
                    PickerRow(row = row, onClick = { onRowClick(row) })
                }
            }
        }
    }
}

@Composable
private fun PickerRow(row: MeasurementsPickerRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DesignTokens.space3),
    ) {
        CustomerAvatar(name = row.name, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    row.measurementCount == 0 -> stringResource(Res.string.measurements_picker_none)
                    row.measurementCount == 1 -> stringResource(Res.string.measurements_picker_count_one)
                    else -> stringResource(Res.string.measurements_picker_count_many, row.measurementCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.measurementCount == 0) {
            Text(
                text = stringResource(Res.string.measurements_picker_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```
Add light + dark previews of `MeasurementsPickerContent` (loaded rows incl. a 0-count, a 1-count, a many-count row). Check `CustomerAvatar`'s actual signature (it may take an `avatarColorIndex`/customer param — adapt from `CustomerListScreen.kt`'s usage at line 707; if it needs more than `name`, add the needed field to `MeasurementsPickerRow` in Task 3's model and thread it from `Customer` — coordinate: if you change the model, update Task 3's tests accordingly and note it in your report).

- [ ] **Step 4: Render + wire**

In `DashboardScreen.kt` (near the other sheet/dialog renders in `DashboardRoot`'s screen or `DashboardScreen` — follow where `PushPermissionSheetContent`/other sheets render):
```kotlin
state.measurementsPicker?.let { picker ->
    MeasurementsPickerSheet(
        picker = picker,
        onQueryChange = { onAction(DashboardAction.OnMeasurementsPickerQueryChange(it)) },
        onRowClick = { onAction(DashboardAction.OnMeasurementsPickerRowClick(it)) },
        onDismiss = { onAction(DashboardAction.OnDismissMeasurementsPicker) },
    )
}
```
`handleDashboardEvent` + `DashboardRoot` params + `MainScreen.kt` `composable<DashboardRoute>`:
```kotlin
onNavigateToMeasurementDetail = { customerId, measurementId ->
    navController.navigate(
        MeasurementDetailRoute(
            customerId = customerId,
            measurementId = measurementId,
            source = MeasurementDetailSource.DASHBOARD,
        ),
    )
},
onNavigateToAddMeasurementForCustomer = { customerId ->
    navController.navigate(MeasurementFormRoute(customerId = customerId))
},
```
(Event arms: `NavigateToMeasurementDetail -> onNavigateToMeasurementDetail(...)`, `NavigateToAddMeasurement -> onNavigateToAddMeasurementForCustomer(...)`. Reuse the existing `onNavigateToCustomerDetail` for the CustomerDetail branch.)

- [ ] **Step 5: Compile + detekt, commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL.

```bash
git add -A composeApp/src
git commit -m "feat(dashboard): Measurements quick-access row + customer picker sheet"
```

---

### Task 5: Full verification

- [ ] **Step 1:** `./gradlew detekt :composeApp:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 2:** `./gradlew :composeApp:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL.
- [ ] **Step 3:** `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Report done with QA smoke steps (do NOT push):
  1. Dashboard → Quick access shows Inspiration + Measurements rows (all non-loading dashboard states; light + dark).
  2. Measurements row → picker sheet: customers with measurements first (counts correct, "1 measurement" not "1 measurements"), zero-count rows show "No measurements yet" + "+ Add".
  3. Search in the picker filters as you type; nonsense query shows the empty message.
  4. Tap a 1-measurement customer → read-only detail view directly. Tap a several-measurements customer → customer detail. Tap "+ Add" → new measurement form for that customer.
  5. Dashboard with zero customers → Measurements row routes like the "Measurement" tile (add-customer-first flow), no empty sheet.
  6. Customers tab → row three-dots → "View measurements" (between Edit and New measurement) → same routing rule (1 → view, several → customer detail, none → customer detail).
  7. Analytics debug: detail views arriving via picker log source `dashboard`; via actions sheet log `customer_actions_sheet`.

## Out of scope

Search-tab measurement results and customer-row tape chips (cut in spec), denormalized measurement counts, receipt-renderer helper consolidation.

# Measurement Detail View (PR 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A read-only Measurement Detail screen that every saved-measurement tap lands on (instead of the editable form), with explicit Edit / Rename / Delete actions, post-save landing, and view-only access for locked customers.

**Architecture:** New MVI feature package `feature/measurement/presentation/detail/` (State/Action/Event + ViewModel + Root/Screen split), a new `MeasurementDetailRoute`, rerouted taps from customer detail / order detail / post-save, and removal of the per-row overflow menu on customer detail. Display data comes from the existing `MeasurementRepository.observeMeasurements` flow — no data-layer changes.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, Koin (`viewModelOf`), kotlin.test + Turbine (`:composeApp:testDebugUnitTest`), detekt.

**Spec:** `docs/superpowers/specs/2026-07-08-measurement-detail-view-design.md`. Mockup (V1 "sectioned note"): `preview/measurement-visibility-redesign.html`.

## Global Constraints

- Worktree: `/Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/measurement-detail-view`, branch `feat/measurement-detail-view`. Run all commands from the worktree root.
- MVI: State/Action/Event sealed types per screen; all state in the ViewModel; Root (koinViewModel) / Screen (stateless) split; every Screen composable gets `@Preview` in light AND dark.
- Never hardcode user-facing strings — add to `composeApp/src/commonMain/composeResources/values/strings.xml`. No backslash escapes (`\'`) in strings.xml — use `&apos;` or typographic `’`.
- `Result<T, E>` everywhere; errors surface as `UiText` via existing `toMeasurementUiText()`.
- KMP: no JVM-only APIs (`String.format`, etc.); no `()` in backtick test names; `stringResource` args must be positional (`%1$s`).
- GitLive writes suspend until server ACK — deletes that navigate away must be fire-and-forget; never block navigation on a write.
- Measurement numbers render in `JetBrainsMonoFamily()` (from `ui.theme.Type.kt` — it is a `@Composable` function).
- Gradle exit codes: never pipe `./gradlew … | tail`; run plainly so the exit code is the wrapper's.
- Commit after each task; do NOT push (Daniel reviews first; codex pre-push hook runs on push).

---

### Task 1: Section resolver (`measurementDetailSections`)

Pure function grouping a measurement's filled values into template sections + one custom group. Reuses `MeasurementPreviewField` (in `feature/measurement/presentation/MeasurementPreview.kt`).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailSections.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailSectionsTest.kt`

**Interfaces:**
- Consumes: `BodyProfileTemplate.sectionsFor(gender)`, `MeasurementPreviewField(label: String, value: Double)`, `Measurement`.
- Produces: `data class MeasurementDetailSection(val titleKey: String?, val rows: List<MeasurementPreviewField>)` and `fun measurementDetailSections(measurement: Measurement, customFieldLabels: Map<String, String>): List<MeasurementDetailSection>` — Task 3's screen and Task 2's tests rely on these exact names.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementDetailSectionsTest {

    private fun measurement(
        gender: CustomerGender = CustomerGender.FEMALE,
        fields: Map<String, Double>,
    ) = Measurement(
        id = "m1",
        customerId = "c1",
        gender = gender,
        name = "Test",
        fields = fields,
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 0L,
        createdAt = 0L,
    )

    @Test
    fun `groups filled values under their template sections in template order`() {
        val sections = measurementDetailSections(
            measurement = measurement(
                fields = mapOf(
                    "trouser_waist" to 31.0,
                    "shoulder_width" to 15.0,
                    "bust_circumference" to 38.0,
                ),
            ),
            customFieldLabels = emptyMap(),
        )
        assertEquals(listOf("section_upper_body", "section_trouser"), sections.map { it.titleKey })
        assertEquals(listOf("Shoulder", "Bust"), sections[0].rows.map { it.label })
        assertEquals(listOf(15.0, 38.0), sections[0].rows.map { it.value })
    }

    @Test
    fun `drops zero and missing values and omits empty sections`() {
        val sections = measurementDetailSections(
            measurement = measurement(fields = mapOf("waist" to 31.0, "hip_circumference" to 0.0)),
            customFieldLabels = emptyMap(),
        )
        assertEquals(1, sections.size)
        assertEquals("section_upper_body", sections[0].titleKey)
        assertEquals(listOf("Waist"), sections[0].rows.map { it.label })
    }

    @Test
    fun `custom fields come last as a null-titleKey group sorted alphabetically`() {
        val sections = measurementDetailSections(
            measurement = measurement(
                fields = mapOf("waist" to 31.0, "uuid-b" to 12.0, "uuid-a" to 7.5),
            ),
            customFieldLabels = mapOf("uuid-a" to "Zip length", "uuid-b" to "Agbada flare"),
        )
        val custom = sections.last()
        assertNull(custom.titleKey)
        assertEquals(listOf("Agbada flare", "Zip length"), custom.rows.map { it.label })
    }

    @Test
    fun `orphan keys without a label are skipped`() {
        val sections = measurementDetailSections(
            measurement = measurement(fields = mapOf("waist" to 31.0, "unknown-key" to 9.0)),
            customFieldLabels = emptyMap(),
        )
        assertTrue(sections.flatMap { it.rows }.none { it.value == 9.0 })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementDetailSectionsTest*"`
Expected: FAIL — unresolved reference `measurementDetailSections`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.feature.measurement.presentation.MeasurementPreviewField

/**
 * One rendered section on the read-only measurement detail screen. [titleKey]
 * is a BodyProfileTemplate section key ("section_upper_body", …) resolved to a
 * string resource at the composable layer; null marks the custom-fields group.
 */
data class MeasurementDetailSection(
    val titleKey: String?,
    val rows: List<MeasurementPreviewField>,
)

/**
 * Groups the measurement's filled values (> 0) into template sections in
 * template order, followed by one custom group (alphabetical) resolved via
 * [customFieldLabels]. Sections with no filled values are dropped. Keys that
 * are neither template fields nor resolvable custom fields are skipped —
 * the same policy as [com.danzucker.stitchpad.feature.measurement.presentation.filledPreviewFields].
 */
fun measurementDetailSections(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
): List<MeasurementDetailSection> {
    val templateSections = BodyProfileTemplate.sectionsFor(measurement.gender)
    val templateKeys = templateSections.flatMap { it.fields }.mapTo(mutableSetOf()) { it.key }

    val filled = templateSections.mapNotNull { section ->
        val rows = section.fields.mapNotNull { field ->
            measurement.fields[field.key]
                ?.takeIf { it > 0.0 }
                ?.let { MeasurementPreviewField(field.label, it) }
        }
        if (rows.isEmpty()) null else MeasurementDetailSection(section.titleKey, rows)
    }

    val customRows = measurement.fields
        .filter { (key, value) -> key !in templateKeys && key in customFieldLabels && value > 0.0 }
        .map { (key, value) -> MeasurementPreviewField(customFieldLabels.getValue(key), value) }
        .sortedBy { it.label.lowercase() }

    return if (customRows.isEmpty()) {
        filled
    } else {
        filled + MeasurementDetailSection(titleKey = null, rows = customRows)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementDetailSectionsTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailSections.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailSectionsTest.kt
git commit -m "feat(measurement): section resolver for read-only detail view"
```

---

### Task 2: Detail State / Action / Event / ViewModel + analytics event

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailState.kt`
- Create: `.../detail/MeasurementDetailAction.kt`
- Create: `.../detail/MeasurementDetailEvent.kt` (includes `MeasurementDetailSource` constants)
- Create: `.../detail/MeasurementDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt` (add one event after `MeasurementAdded`)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `MeasurementRepository`, `CustomMeasurementFieldRepository.observeFields(userId)`, `CustomerRepository.observeCustomer(userId, customerId)`, `AuthRepository.getCurrentUser()?.id`, `Analytics.logEvent`, `toMeasurementUiText()` (package `feature.measurement.presentation`), `CustomerSlotState.LOCKED`, `UiText` (package `core.presentation`).
- Produces (Tasks 3–5 rely on these exact names):
  - `MeasurementDetailState(measurement, customFieldLabels, isLocked, isLoading, showDeleteDialog, renameDraft, showSavedMessage, errorMessage)`
  - `MeasurementDetailAction`: `OnEditClick, OnRenameClick, OnRenameDraftChange(name: String), OnConfirmRename, OnDismissRenameDialog, OnDeleteClick, OnConfirmDelete, OnDismissDeleteDialog, OnSavedMessageShown, OnNavigateBack, OnErrorDismiss`
  - `MeasurementDetailEvent`: `NavigateBack, NavigateToEdit(customerId: String, measurementId: String), NavigateToUpgrade`
  - `MeasurementDetailSource.CUSTOMER_DETAIL / ORDER_DETAIL / POST_SAVE` (strings `"customer_detail"`, `"order_detail"`, `"post_save"`)
  - `AnalyticsEvent.MeasurementDetailViewed(source: String)` → name `measurement_detail_viewed`, params `{"source": source}`
  - ViewModel constructor: `MeasurementDetailViewModel(savedStateHandle, measurementRepository, customFieldRepository, customerRepository, authRepository, analytics)` reading route args `customerId: String`, `measurementId: String`, `source: String = "customer_detail"`, `fromSave: Boolean = false`.

- [ ] **Step 1: Write the State / Action / Event files**

`MeasurementDetailState.kt`:
```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementDetailState(
    val measurement: Measurement? = null,
    /** id → label for ALL custom-field definitions (archived included, so recorded values keep rendering). */
    val customFieldLabels: Map<String, String> = emptyMap(),
    val isLocked: Boolean = false,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    /** Non-null = rename dialog open with this draft text. */
    val renameDraft: String? = null,
    /** One-shot "Measurement saved" snackbar when arriving from a save. */
    val showSavedMessage: Boolean = false,
    val errorMessage: UiText? = null,
)
```

`MeasurementDetailAction.kt`:
```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

sealed interface MeasurementDetailAction {
    data object OnEditClick : MeasurementDetailAction
    data object OnRenameClick : MeasurementDetailAction
    data class OnRenameDraftChange(val name: String) : MeasurementDetailAction
    data object OnConfirmRename : MeasurementDetailAction
    data object OnDismissRenameDialog : MeasurementDetailAction
    data object OnDeleteClick : MeasurementDetailAction
    data object OnConfirmDelete : MeasurementDetailAction
    data object OnDismissDeleteDialog : MeasurementDetailAction
    data object OnSavedMessageShown : MeasurementDetailAction
    data object OnNavigateBack : MeasurementDetailAction
    data object OnErrorDismiss : MeasurementDetailAction
}
```

`MeasurementDetailEvent.kt`:
```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

sealed interface MeasurementDetailEvent {
    data object NavigateBack : MeasurementDetailEvent
    data class NavigateToEdit(val customerId: String, val measurementId: String) : MeasurementDetailEvent

    /** Locked (over-cap) customer tapped a gated action — Edit, Rename, or Delete. */
    data object NavigateToUpgrade : MeasurementDetailEvent
}

/** GA4 `source` values for [com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent.MeasurementDetailViewed]. */
object MeasurementDetailSource {
    const val CUSTOMER_DETAIL = "customer_detail"
    const val ORDER_DETAIL = "order_detail"
    const val POST_SAVE = "post_save"
}
```

In `AnalyticsEvent.kt`, directly after the `MeasurementAdded` object:
```kotlin
    data class MeasurementDetailViewed(val source: String) : AnalyticsEvent {
        override val name = "measurement_detail_viewed"
        override val params = mapOf("source" to source)
    }
```

- [ ] **Step 2: Write the failing ViewModel tests**

Model setup on `MeasurementFormViewModelTest` (same file layout: `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@BeforeTest`, `resetMain()` in `@AfterTest`, `backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }` after construction). Use Turbine for events.

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.data.repository.FakeCustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementDetailViewModelTest {

    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var customFieldRepository: FakeCustomMeasurementFieldRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var analytics: FakeAnalytics

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        measurementRepository = FakeMeasurementRepository()
        customFieldRepository = FakeCustomMeasurementFieldRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        analytics = FakeAnalytics()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeMeasurement(id: String = "meas-1", name: String = "Wedding gown") = Measurement(
        id = id,
        customerId = "customer-1",
        gender = CustomerGender.FEMALE,
        name = name,
        fields = mapOf("waist" to 31.0),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 1L,
        createdAt = 1L,
    )

    private fun fakeCustomer(slotState: CustomerSlotState = CustomerSlotState.ACTIVE) = Customer(
        id = "customer-1",
        userId = "user-1",
        name = "Chidinma Eze",
        phone = "0705 991 2340",
        slotState = slotState,
    )

    private fun TestScope.createViewModel(
        measurementId: String = "meas-1",
        source: String = MeasurementDetailSource.CUSTOMER_DETAIL,
        fromSave: Boolean = false,
    ): MeasurementDetailViewModel {
        val vm = MeasurementDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "customerId" to "customer-1",
                    "measurementId" to measurementId,
                    "source" to source,
                    "fromSave" to fromSave,
                ),
            ),
            measurementRepository = measurementRepository,
            customFieldRepository = customFieldRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            analytics = analytics,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `loads measurement and lock state on start`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()

        val state = vm.state.value
        assertEquals("Wedding gown", state.measurement?.name)
        assertEquals(false, state.isLocked)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `logs viewed analytics with source once`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        createViewModel(source = MeasurementDetailSource.POST_SAVE)

        val event = analytics.loggedEvents.filterIsInstance<AnalyticsEvent.MeasurementDetailViewed>().single()
        assertEquals("post_save", event.source)
    }

    @Test
    fun `missing measurement navigates back`() = runTest {
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.events.test {
            assertIs<MeasurementDetailEvent.NavigateBack>(awaitItem())
        }
    }

    @Test
    fun `edit on unlocked customer navigates to edit form`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnEditClick)
            val event = assertIs<MeasurementDetailEvent.NavigateToEdit>(awaitItem())
            assertEquals("customer-1", event.customerId)
            assertEquals("meas-1", event.measurementId)
        }
    }

    @Test
    fun `edit rename and delete on locked customer route to upgrade`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnEditClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
            vm.onAction(MeasurementDetailAction.OnRenameClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
            vm.onAction(MeasurementDetailAction.OnDeleteClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
        }
        assertNull(vm.state.value.renameDraft)
        assertEquals(false, vm.state.value.showDeleteDialog)
    }

    @Test
    fun `confirm delete fires repository delete and navigates back`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnDeleteClick)
        assertTrue(vm.state.value.showDeleteDialog)
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnConfirmDelete)
            assertIs<MeasurementDetailEvent.NavigateBack>(awaitItem())
        }
        assertEquals("meas-1", measurementRepository.lastDeletedMeasurementId)
    }

    @Test
    fun `confirm rename updates repository with trimmed name`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnRenameClick)
        assertEquals("Wedding gown", vm.state.value.renameDraft)
        vm.onAction(MeasurementDetailAction.OnRenameDraftChange("  Aso-ebi  "))
        vm.onAction(MeasurementDetailAction.OnConfirmRename)
        assertEquals("Aso-ebi", measurementRepository.lastUpdatedMeasurement?.name)
        assertNull(vm.state.value.renameDraft)
    }

    @Test
    fun `fromSave arg arms the saved snackbar once`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(fromSave = true)
        assertTrue(vm.state.value.showSavedMessage)
        vm.onAction(MeasurementDetailAction.OnSavedMessageShown)
        assertEquals(false, vm.state.value.showSavedMessage)
    }
}
```

Note: `FakeAnalytics` — check `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/FakeAnalytics.kt` for the recorded-events property name; if it is not `loggedEvents`, use the actual property. `FakeMeasurementRepository.measurementsList` returns a static `flowOf` (no re-emission) — that is sufficient for these tests.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementDetailViewModelTest*"`
Expected: FAIL — unresolved reference `MeasurementDetailViewModel`.

- [ ] **Step 4: Write the ViewModel**

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.measurement.presentation.toMeasurementUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MeasurementDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val source: String = savedStateHandle["source"] ?: MeasurementDetailSource.CUSTOMER_DETAIL
    private val fromSave: Boolean = savedStateHandle["fromSave"] ?: false

    private var hasLoadedInitialData = false

    // Set when THIS screen initiates the exit (delete, missing measurement) so
    // the measurement observer doesn't double-fire NavigateBack.
    private var navigatedAway = false

    private val _state = MutableStateFlow(MeasurementDetailState(showSavedMessage = fromSave))
    private val _events = Channel<MeasurementDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId == null || measurementId == null) {
                    _events.send(MeasurementDetailEvent.NavigateBack)
                    return@onStart
                }
                analytics.logEvent(AnalyticsEvent.MeasurementDetailViewed(source))
                observeMeasurement(customerId, measurementId)
                observeCustomFieldLabels()
                observeLockState(customerId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementDetailState(showSavedMessage = fromSave),
        )

    fun onAction(action: MeasurementDetailAction) {
        when (action) {
            MeasurementDetailAction.OnEditClick -> requireUnlocked {
                val customerId = customerId ?: return@requireUnlocked
                val measurementId = measurementId ?: return@requireUnlocked
                viewModelScope.launch {
                    _events.send(MeasurementDetailEvent.NavigateToEdit(customerId, measurementId))
                }
            }
            MeasurementDetailAction.OnRenameClick -> requireUnlocked {
                _state.update { it.copy(renameDraft = it.measurement?.name ?: "") }
            }
            is MeasurementDetailAction.OnRenameDraftChange ->
                _state.update { it.copy(renameDraft = action.name) }
            MeasurementDetailAction.OnConfirmRename -> renameMeasurement()
            MeasurementDetailAction.OnDismissRenameDialog ->
                _state.update { it.copy(renameDraft = null) }
            MeasurementDetailAction.OnDeleteClick -> requireUnlocked {
                _state.update { it.copy(showDeleteDialog = true) }
            }
            MeasurementDetailAction.OnConfirmDelete -> deleteMeasurement()
            MeasurementDetailAction.OnDismissDeleteDialog ->
                _state.update { it.copy(showDeleteDialog = false) }
            MeasurementDetailAction.OnSavedMessageShown ->
                _state.update { it.copy(showSavedMessage = false) }
            MeasurementDetailAction.OnNavigateBack ->
                viewModelScope.launch { _events.send(MeasurementDetailEvent.NavigateBack) }
            MeasurementDetailAction.OnErrorDismiss ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    /** Gated actions on a locked (over-cap) customer route to the upgrade screen instead. */
    private inline fun requireUnlocked(block: () -> Unit) {
        if (_state.value.isLocked) {
            viewModelScope.launch { _events.send(MeasurementDetailEvent.NavigateToUpgrade) }
        } else {
            block()
        }
    }

    private fun observeMeasurement(customerId: String, measurementId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            measurementRepository.observeMeasurements(userId, customerId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val measurement = result.data.find { it.id == measurementId }
                        if (measurement != null) {
                            _state.update { it.copy(measurement = measurement, isLoading = false) }
                        } else if (!navigatedAway) {
                            // Deleted elsewhere (another device / another screen) — leave.
                            navigatedAway = true
                            _events.send(MeasurementDetailEvent.NavigateBack)
                        }
                    }
                    is Result.Error -> _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toMeasurementUiText())
                    }
                }
            }
        }
    }

    private fun observeCustomFieldLabels() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customFieldRepository.observeFields(userId).collect { result ->
                if (result is Result.Success) {
                    // ALL definitions, archived included — recorded values on old
                    // measurements must keep their labels after archive.
                    _state.update { current ->
                        current.copy(customFieldLabels = result.data.associate { it.id to it.label })
                    }
                }
                // Errors are non-fatal: custom rows fall back to being skipped.
            }
        }
    }

    private fun observeLockState(customerId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customerRepository.observeCustomer(userId, customerId).collect { result ->
                if (result is Result.Success) {
                    _state.update {
                        it.copy(isLocked = result.data.slotState == CustomerSlotState.LOCKED)
                    }
                }
                // On error keep the last known lock state — read-only content stays visible.
            }
        }
    }

    private fun renameMeasurement() {
        val customerId = customerId ?: return
        val measurement = _state.value.measurement ?: return
        val newName = _state.value.renameDraft?.trim().orEmpty()
        if (newName.isBlank()) return
        _state.update { it.copy(renameDraft = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = measurementRepository.updateMeasurement(
                userId,
                customerId,
                measurement.copy(name = newName),
            )
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toMeasurementUiText()) }
            }
        }
    }

    private fun deleteMeasurement() {
        val customerId = customerId ?: return
        val measurement = _state.value.measurement ?: return
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            navigatedAway = true
            // Fire-and-forget: GitLive deletes suspend until server ACK, but the
            // local cache applies the mutation immediately — enqueue and leave.
            // Customer detail's observer drops the row at once.
            launch { measurementRepository.deleteMeasurement(userId, customerId, measurement.id) }
            _events.send(MeasurementDetailEvent.NavigateBack)
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementDetailViewModelTest*"`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailViewModelTest.kt
git commit -m "feat(measurement): MeasurementDetailViewModel with locked gating, rename, delete"
```

---

### Task 3: Detail Screen composable + strings

V1 "sectioned note" layout from the mockup: app bar (back / title / overflow with Rename + Delete), meta chips (gender · unit · taken date), one card per section with label-left / mono-value-right rows, notes card, sticky bottom bar with a full-width **Edit measurement** button. (The Share square button next to Edit arrives in PR 2 — leave the bar a single button for now.)

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt`

**Interfaces:**
- Consumes: Task 1 `measurementDetailSections`, Task 2 state/actions, `formatMeasurementValue`, `JetBrainsMonoFamily()`, `StitchPadButton(text, onClick, modifier, leadingIcon)`, `DesignTokens`, `ObserveAsEvents`, existing strings (`measurement_detail_title`, `measurement_gender_women/men`, `measurement_unit_inches/cm`, `section_upper_body/…`, `custom_field_section_title`, `measurement_menu_rename/delete`, `measurement_delete_title/message`, `measurement_rename_dialog_title/save/cancel`, `measurement_name_label/placeholder`, `customer_delete_confirm/cancel`).
- Produces: `MeasurementDetailRoot(onNavigateBack: () -> Unit, onNavigateToEdit: (String, String) -> Unit, onNavigateToUpgrade: () -> Unit)` — Task 4's nav registration relies on this exact signature.

- [ ] **Step 1: Add new strings**

In `strings.xml`, after the `measurement_rename_*` block (~line 440):
```xml
    <!-- Measurement detail (read-only view) -->
    <string name="measurement_detail_taken">Taken %1$s</string>
    <string name="measurement_detail_edit_button">Edit measurement</string>
    <string name="measurement_detail_saved_snackbar">Measurement saved</string>
    <string name="measurement_detail_notes_title">Notes</string>
    <string name="cd_measurement_detail_overflow">More options</string>
    <string name="cd_measurement_detail_back">Back</string>
```

- [ ] **Step 2: Write the screen**

Structure (follow `MeasurementFormScreen` / `CustomerDetailScreen` conventions — imports mirror theirs):

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.detail

// imports follow the conventions of MeasurementFormScreen.kt + CustomerDetailScreen.kt

@Composable
fun MeasurementDetailRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String, String) -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: MeasurementDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementDetailEvent.NavigateBack -> onNavigateBack()
            is MeasurementDetailEvent.NavigateToEdit ->
                onNavigateToEdit(event.customerId, event.measurementId)
            MeasurementDetailEvent.NavigateToUpgrade -> onNavigateToUpgrade()
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(MeasurementDetailAction.OnErrorDismiss)
        }
    }

    val savedMessage = stringResource(Res.string.measurement_detail_saved_snackbar)
    LaunchedEffect(state.showSavedMessage) {
        if (state.showSavedMessage) {
            viewModel.onAction(MeasurementDetailAction.OnSavedMessageShown)
            snackbarHostState.showSnackbar(savedMessage)
        }
    }

    MeasurementDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    state: MeasurementDetailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (MeasurementDetailAction) -> Unit,
) {
    val measurement = state.measurement
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = measurement?.name?.ifBlank { null }
                            ?: stringResource(Res.string.measurement_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(MeasurementDetailAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_measurement_detail_back),
                        )
                    }
                },
                actions = { DetailOverflowMenu(onAction) },
            )
        },
        bottomBar = {
            if (measurement != null) {
                Surface {
                    StitchPadButton(
                        text = stringResource(Res.string.measurement_detail_edit_button),
                        onClick = { onAction(MeasurementDetailAction.OnEditClick) },
                        leadingIcon = Icons.Default.Edit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = DesignTokens.space4,
                                end = DesignTokens.space4,
                                top = DesignTokens.space3,
                                bottom = DesignTokens.space4,
                            ),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            measurement != null -> MeasurementDetailContent(
                measurement = measurement,
                customFieldLabels = state.customFieldLabels,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.showDeleteDialog) { /* AlertDialog — copy the exact dialog from
        CustomerDetailScreen.kt:394-436 (measurement_delete_title / _message,
        customer_delete_confirm / _cancel, error-colored confirm) with
        onAction(MeasurementDetailAction.OnConfirmDelete) / OnDismissDeleteDialog */ }

    if (state.renameDraft != null) { /* AlertDialog — copy the exact rename dialog from
        CustomerDetailScreen.kt:438-465 bound to state.renameDraft with
        OnRenameDraftChange / OnConfirmRename / OnDismissRenameDialog */ }
}
```

`MeasurementDetailContent` (the V1 body):
```kotlin
@Composable
private fun MeasurementDetailContent(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val sections = remember(measurement, customFieldLabels) {
        measurementDetailSections(measurement, customFieldLabels)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            bottom = DesignTokens.space4,
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item { MetaChipsRow(measurement) }
        items(sections) { section -> SectionCard(section, measurement.unit) }
        val notes = measurement.notes
        if (!notes.isNullOrBlank()) {
            item { NotesCard(notes) }
        }
    }
}
```

`MetaChipsRow`: a `Row` of three pill `Surface`s (shape `RoundedCornerShape(DesignTokens.radiusFull)`, color `surfaceVariant` — gender pill uses `primaryContainer`/`onPrimaryContainer`): gender via `measurement_gender_women`/`measurement_gender_men`, unit via `measurement_unit_inches`/`measurement_unit_cm`, date via `stringResource(Res.string.measurement_detail_taken, formatTakenDate(measurement.dateTaken))` where:
```kotlin
private fun formatTakenDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}
```
(same helper as `MeasurementDetailSheet.formatShortDate` — kotlinx.datetime, KMP-safe).

`SectionCard`: `Surface` (shape `RoundedCornerShape(DesignTokens.radiusMd)`, color `surface`, 1.dp `outlineVariant` border) containing the section title (`labelSmall`, SemiBold, uppercase, `onSurfaceVariant`) and one row per field:
```kotlin
@Composable
private fun SectionCard(section: MeasurementDetailSection, unit: MeasurementUnit) {
    val title = sectionTitle(section.titleKey)
    val unitSuffix = if (unit == MeasurementUnit.INCHES) "″" else "cm"
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            section.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.space2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatMeasurementValue(row.value)}$unitSuffix",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFamily(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(titleKey: String?): String = when (titleKey) {
    "section_upper_body" -> stringResource(Res.string.section_upper_body)
    "section_body_lengths" -> stringResource(Res.string.section_body_lengths)
    "section_trouser" -> stringResource(Res.string.section_trouser)
    "section_neck_shoulders" -> stringResource(Res.string.section_neck_shoulders)
    "section_bust" -> stringResource(Res.string.section_bust)
    "section_waist_hip" -> stringResource(Res.string.section_waist_hip)
    "section_arms" -> stringResource(Res.string.section_arms)
    null -> stringResource(Res.string.custom_field_section_title)
    else -> titleKey // future template keys degrade to the raw key rather than crash
}
```

`NotesCard`: same card chrome, `measurement_detail_notes_title` header + notes body (`bodyMedium`, italic, `onSurfaceVariant`).

`DetailOverflowMenu`: `IconButton` (`Icons.Default.MoreVert`, cd `cd_measurement_detail_overflow`) + `DropdownMenu` with two `DropdownMenuItem`s — `measurement_menu_rename` (leading `Icons.Default.Edit`) → `OnRenameClick`, `measurement_menu_delete` (leading `Icons.Default.Delete`, tinted `MaterialTheme.colorScheme.error`) → `OnDeleteClick`. Local `remember { mutableStateOf(false) }` for menu expansion is allowed (Compose-internal state).

Previews (required, light + dark; add `@file:Suppress("TooManyFunctions")` only if detekt trips):
```kotlin
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailScreenPreview() {
    StitchPadTheme {
        MeasurementDetailScreen(
            state = MeasurementDetailState(
                measurement = Measurement(
                    id = "m1", customerId = "c1", gender = CustomerGender.FEMALE,
                    name = "Wedding guest gown",
                    fields = mapOf("shoulder_width" to 15.0, "bust_circumference" to 38.0, "waist" to 31.0, "trouser_waist" to 31.0),
                    unit = MeasurementUnit.INCHES,
                    notes = "Prefers the gown loose at the hip.",
                    dateTaken = 1_750_000_000_000L, createdAt = 1_750_000_000_000L,
                ),
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
```
plus an identical `...DarkPreview` wrapped in `StitchPadTheme(darkTheme = true)`.

- [ ] **Step 3: Verify it compiles and detekt passes**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid detekt`
Expected: BUILD SUCCESSFUL. (No unit tests for pure UI; previews are the visual check.)

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/detail/MeasurementDetailScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(measurement): read-only detail screen (V1 sectioned-note layout)"
```

---

### Task 4: Route, DI, nav registration + reroute existing taps

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt` (after `MeasurementFormRoute`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailEvent.kt`, `CustomerDetailViewModel.kt`, `CustomerDetailScreen.kt`

**Interfaces:**
- Consumes: `MeasurementDetailRoot` (Task 3), `MeasurementDetailSource` (Task 2).
- Produces: `MeasurementDetailRoute(customerId: String, measurementId: String, source: String, fromSave: Boolean = false)` — Task 5 relies on this exact shape.

- [ ] **Step 1: Add the route**

```kotlin
@Serializable
data class MeasurementDetailRoute(
    val customerId: String,
    val measurementId: String,
    val source: String,
    val fromSave: Boolean = false,
)
```

- [ ] **Step 2: Register DI**

In `MeasurementModule.kt` add the import and line:
```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.detail.MeasurementDetailViewModel
// inside measurementPresentationModule:
    viewModelOf(::MeasurementDetailViewModel)
```

- [ ] **Step 3: Register the destination in MainScreen**

Next to the existing `composable<MeasurementFormRoute>` block (MainScreen.kt ~line 297):
```kotlin
composable<MeasurementDetailRoute> {
    MeasurementDetailRoot(
        onNavigateBack = { navController.navigateUp() },
        onNavigateToEdit = { customerId, measurementId ->
            navController.navigate(
                MeasurementFormRoute(customerId = customerId, measurementId = measurementId),
            )
        },
        onNavigateToUpgrade = { navController.navigate(UpgradeRoute) },
    )
}
```

- [ ] **Step 4: Reroute customer-detail taps**

Rename the event and thread it through (view semantics, not edit):
- `CustomerDetailEvent.kt`: `NavigateToEditMeasurement` → `NavigateToViewMeasurement` (same payload).
- `CustomerDetailViewModel.kt:109`: send `CustomerDetailEvent.NavigateToViewMeasurement(it, action.measurement.id)`.
- `CustomerDetailScreen.kt` `CustomerDetailRoot`: rename the parameter `onNavigateToEditMeasurement` → `onNavigateToViewMeasurement` and the event arm to match.
- `MainScreen.kt` `composable<CustomerDetailRoute>` block:
```kotlin
onNavigateToViewMeasurement = { customerId, measurementId ->
    navController.navigate(
        MeasurementDetailRoute(
            customerId = customerId,
            measurementId = measurementId,
            source = MeasurementDetailSource.CUSTOMER_DETAIL,
        ),
    )
},
```
(The `AddMeasurementSheet` existing-measurement rows already funnel through `OnMeasurementClick`, so they follow automatically.)

- [ ] **Step 5: Reroute the order-detail tap**

In `MainScreen.kt` `composable<OrderDetailRoute>` block (~line 400), change `onNavigateToViewMeasurement` to:
```kotlin
onNavigateToViewMeasurement = { customerId, measurementId ->
    navController.navigate(
        MeasurementDetailRoute(
            customerId = customerId,
            measurementId = measurementId,
            source = MeasurementDetailSource.ORDER_DETAIL,
        ),
    )
},
```

- [ ] **Step 6: Make locked rows tappable**

In `CustomerDetailScreen.kt`, `ReadOnlyMeasurementItem` (line 797) gains an `onClick: () -> Unit` parameter, passed through to `MeasurementListItem(onClick = onClick)`; keep the 50% alpha. Update its call site in the measurements list to pass `onClick = { onAction(CustomerDetailAction.OnMeasurementClick(measurement)) }` and update the composable's doc comment: locked customers can now VIEW measurements read-only; only write actions (edit/rename/delete, gated on the detail screen) remain locked.

- [ ] **Step 7: Compile, run existing tests, commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL — the event rename is mechanical; if `CustomerDetailViewModelTest` references `NavigateToEditMeasurement`, update those references to `NavigateToViewMeasurement`.

```bash
git add -A composeApp/src
git commit -m "feat(measurement): route all saved-measurement taps to the read-only detail view"
```

---

### Task 5: Post-save landing on the detail view

Save (create or edit) lands on the detail view with a "Measurement saved" snackbar — except the two chained flows that must return to their parent: customer-creation (`fromCustomerCreation`) and order-linking (`linkToOrderId`), which keep `NavigateBack`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormEvent.kt`
- Modify: `.../form/MeasurementFormViewModel.kt` (`save()` tail)
- Modify: `.../form/MeasurementFormScreen.kt` (`MeasurementFormRoot`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` (`composable<MeasurementFormRoute>`)
- Test: extend `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

**Interfaces:**
- Produces: `MeasurementFormEvent.MeasurementSaved(customerId: String, measurementId: String)`; `MeasurementFormRoot` gains `onNavigateToDetail: (customerId: String, measurementId: String) -> Unit`.

- [ ] **Step 1: Write the failing tests**

Add to `MeasurementFormViewModelTest` (reuse its existing helpers — `createViewModel`, `fakeMeasurement`, and its established pattern for filling a field and clicking save; mirror an existing save test's setup exactly):

```kotlin
@Test
fun `save emits MeasurementSaved with the persisted id`() = runTest {
    val vm = createViewModel()
    // canSave requires gender + non-blank name + at least one positive field.
    vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.FEMALE))
    vm.onAction(MeasurementFormAction.OnNameChange("Agbada"))
    vm.onAction(MeasurementFormAction.OnFieldChange("waist", "31"))
    vm.events.test {
        vm.onAction(MeasurementFormAction.OnSaveClick)
        val event = assertIs<MeasurementFormEvent.MeasurementSaved>(awaitItem())
        assertEquals("customer-1", event.customerId)
        assertEquals(measurementRepository.lastCreatedMeasurement?.id, event.measurementId)
    }
}

@Test
fun `save during customer creation still navigates back`() = runTest {
    val vm = createViewModel(fromCustomerCreation = true)
    vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.FEMALE))
    vm.onAction(MeasurementFormAction.OnNameChange("Agbada"))
    vm.onAction(MeasurementFormAction.OnFieldChange("waist", "31"))
    vm.events.test {
        vm.onAction(MeasurementFormAction.OnSaveClick)
        assertIs<MeasurementFormEvent.NavigateBack>(awaitItem())
    }
}
```
Also add a third test `save with linkToOrderId still navigates back` if `createViewModel` supports a `linkToOrderId` arg (add the SavedStateHandle key to the helper if missing — mirror how `measurementId` is passed).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementFormViewModelTest*"`
Expected: the two/three new tests FAIL (`MeasurementSaved` unresolved); all existing tests still pass.

- [ ] **Step 3: Implement**

`MeasurementFormEvent.kt` — add:
```kotlin
    /**
     * Save succeeded on a standalone create/edit — land on the read-only detail
     * view (replacing the form in the back stack). The chained flows — customer
     * creation and order-linking — keep [NavigateBack] so they return to their
     * parent (customer detail / order detail).
     */
    data class MeasurementSaved(val customerId: String, val measurementId: String) : MeasurementFormEvent
```

`MeasurementFormViewModel.save()` — replace the final `_events.send(MeasurementFormEvent.NavigateBack)` (line 465) with:
```kotlin
            _state.update { it.copy(isLoading = false) }
            if (fromCustomerCreation || linkToOrderId != null) {
                _events.send(MeasurementFormEvent.NavigateBack)
            } else {
                _events.send(MeasurementFormEvent.MeasurementSaved(customerId, effectiveId))
            }
```

`MeasurementFormRoot` — new parameter + event arm:
```kotlin
fun MeasurementFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
) {
    // …
            is MeasurementFormEvent.MeasurementSaved ->
                onNavigateToDetail(event.customerId, event.measurementId)
```

`MainScreen.kt` `composable<MeasurementFormRoute>` — add:
```kotlin
onNavigateToDetail = { customerId, measurementId ->
    navController.navigate(
        MeasurementDetailRoute(
            customerId = customerId,
            measurementId = measurementId,
            source = MeasurementDetailSource.POST_SAVE,
            fromSave = true,
        ),
    ) {
        // Replace the form: Back from the detail view returns to wherever the
        // form was opened from (customer detail), never to the stale form.
        popUpTo<MeasurementFormRoute> { inclusive = true }
    }
},
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementFormViewModelTest*"`
Expected: PASS, including all pre-existing tests (several assert `NavigateBack` after save — any that save WITHOUT `fromCustomerCreation`/`linkToOrderId` must be updated to expect `MeasurementSaved`; that expectation change is the point of this task, not a regression).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt
git commit -m "feat(measurement): land on read-only detail view after save"
```

---

### Task 6: Remove the per-row overflow menu from customer detail

All manage actions now live on the detail screen — rows become purely tappable.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailScreen.kt`
- Modify: `.../detail/CustomerDetailAction.kt`, `CustomerDetailState.kt`, `CustomerDetailViewModel.kt`
- Test: update `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/detail/CustomerDetailViewModelTest.kt`

- [ ] **Step 1: Strip the UI**

In `CustomerDetailScreen.kt`:
- `MeasurementListItem` (line 933): delete the `onDelete`/`onRename` parameters and the entire trailing `if (onDelete != null || onRename != null) { … DropdownMenu … }` block (lines 1021+).
- `SwipeableMeasurementItem` (line 773): delete its `onDelete`/`onRename` parameters and stop passing them down; update its call site.
- Delete the measurement-delete `AlertDialog` (lines 394–436) and the rename `AlertDialog` (lines 438–465). Do NOT touch the delete-CUSTOMER dialog (`showDeleteCustomerDialog`, line 468) — that one stays.

- [ ] **Step 2: Strip state/actions/VM**

- `CustomerDetailAction.kt`: remove `OnDeleteMeasurementClick`, `OnConfirmDelete`, `OnDismissDeleteDialog`, `OnRenameMeasurementClick`, `OnRenameDraftChange`, `OnConfirmRename`, `OnDismissRenameDialog`. (Keep `OnMeasurementClick`, `OnCreateNewMeasurementClick`, everything customer-level.)
- `CustomerDetailState.kt`: remove `showDeleteDialog`, `measurementToDelete`, `measurementToRename`, `renameDraft`.
- `CustomerDetailViewModel.kt`: remove the corresponding `when` arms and the `deleteMeasurement()` (line 330) and `renameMeasurement()` (line 344) functions.
- Keep the strings (`measurement_menu_rename/delete`, `measurement_delete_*`, `measurement_rename_*`) — the detail screen (Task 3) uses them. Remove `cd_measurement_overflow` from `strings.xml` only if nothing else references it (`grep -rn cd_measurement_overflow composeApp/src`).

- [ ] **Step 3: Update tests, run, commit**

Delete/adjust any `CustomerDetailViewModelTest` cases covering measurement delete/rename (that behavior now lives in `MeasurementDetailViewModelTest` from Task 2).

Run: `./gradlew :composeApp:testDebugUnitTest detekt`
Expected: PASS.

```bash
git add -A composeApp/src
git commit -m "refactor(customer): remove measurement row overflow — manage actions live on the detail view"
```

---

### Task 7: Full verification

- [ ] **Step 1: Full unit tests + detekt**

Run: `./gradlew detekt :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, zero detekt findings.

- [ ] **Step 2: iOS compile gate** (KMP rule — JVM-green ≠ iOS-green)

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Android assemble**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Report + hand off for QA**

Do NOT push. Report done and give Daniel these manual smoke steps (he is QA; both light and dark mode):

1. Customer detail → tap a saved measurement → read-only detail view opens (no keyboard, nothing editable); values grouped by section in mono font.
2. Detail view → Edit measurement → the familiar form opens pre-filled → change a value → Save → lands back on the detail view with "Measurement saved" snackbar → Back returns to customer detail (not the form).
3. Detail view overflow → Rename → new name shows immediately. Overflow → Delete → confirm → returns to customer detail, row gone.
4. Customer detail measurement rows no longer show the three-dot menu.
5. Create a brand-new measurement from customer detail FAB → Save → lands on the detail view of the new measurement.
6. Create measurement via new-customer flow ("Save & Add Measurements") → Save → returns to the new customer's detail (unchanged behavior). Same for order-detail "link measurement" create → returns to the order.
7. Order detail → view linked measurement ("View full measurement") → read-only detail view.
8. Locked (over-cap free) customer → tap a measurement row → view opens read-only; Edit / Rename / Delete each route to the Upgrade screen.
9. Airplane mode → delete a measurement from the detail view → returns instantly, row gone from customer detail (offline fire-and-forget).

## Out of scope for PR 1

Sharing (PR 2: `MeasurementSharer` + share bar button), dashboard quick-access entry point and `CustomerActionsSheet` "View measurements" row (PR 3).

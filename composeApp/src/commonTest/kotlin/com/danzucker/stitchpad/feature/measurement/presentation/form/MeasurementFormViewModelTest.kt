package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.data.repository.FakeCustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.measurement.data.FakeMeasurementPreferencesStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementFormViewModelTest {

    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var preferencesStore: FakeMeasurementPreferencesStore
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customFieldRepository: FakeCustomMeasurementFieldRepository
    private lateinit var fakeEntitlements: FakeEntitlementsProvider

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository()
        preferencesStore = FakeMeasurementPreferencesStore()
        orderRepository = FakeOrderRepository()
        customFieldRepository = FakeCustomMeasurementFieldRepository()
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
        measurementId: String? = null,
        fromCustomerCreation: Boolean = false,
        linkToOrderId: String? = null,
        analytics: Analytics = FakeAnalytics(),
    ): MeasurementFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (measurementId != null) put("measurementId", measurementId)
            put("fromCustomerCreation", fromCustomerCreation)
            if (linkToOrderId != null) put("linkToOrderId", linkToOrderId)
        }
        val vm = MeasurementFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            measurementRepository = measurementRepository,
            authRepository = authRepository,
            measurementPreferencesStore = preferencesStore,
            orderRepository = orderRepository,
            customFieldRepository = customFieldRepository,
            entitlements = fakeEntitlements,
            analytics = analytics,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private class FakeEntitlementsProvider(
        initialCanUseCustomMeasurements: Boolean,
    ) : EntitlementsProvider {
        private fun build(can: Boolean) = UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = 15,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = can,
        )
        private val _flow = MutableStateFlow(build(initialCanUseCustomMeasurements))
        private var awaitedEntitlements = build(initialCanUseCustomMeasurements)
        private var shouldAwaitForever = false
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements {
            if (shouldAwaitForever) {
                CompletableDeferred<Unit>().await()
            }
            return awaitedEntitlements
        }

        fun setEntitled(can: Boolean) {
            val entitlements = build(can)
            _flow.value = entitlements
            awaitedEntitlements = entitlements
        }

        fun setAwaitedEntitled(can: Boolean) {
            awaitedEntitlements = build(can)
        }

        fun neverHydrate() {
            shouldAwaitForever = true
        }
    }

    private fun fakeMeasurement(
        id: String = "meas-1",
        customerId: String = "customer-1",
        gender: CustomerGender = CustomerGender.FEMALE,
        unit: MeasurementUnit = MeasurementUnit.CM,
        notes: String? = "morning measurement",
    ) = Measurement(
        id = id,
        customerId = customerId,
        gender = gender,
        fields = mapOf("bust_circumference" to 90.0, "waist" to 70.5),
        unit = unit,
        notes = notes,
        dateTaken = 0L,
        createdAt = 12345L,
    )

    @Test
    fun missingCustomerId_navigatesBack_withoutLoading() = runTest {
        val vm = MeasurementFormViewModel(
            savedStateHandle = SavedStateHandle(),
            measurementRepository = measurementRepository,
            authRepository = authRepository,
            measurementPreferencesStore = preferencesStore,
            orderRepository = orderRepository,
            customFieldRepository = customFieldRepository,
            entitlements = fakeEntitlements,
            analytics = FakeAnalytics(),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }

        assertIs<MeasurementFormEvent.NavigateBack>(vm.events.first())
        assertFalse(vm.state.value.isLoading)
    }

    // --- Initial state ---

    @Test
    fun initialState_createMode_isEditModeFalse() = runTest {
        val vm = createViewModel()
        assertFalse(vm.state.value.isEditMode)
    }

    @Test
    fun initialState_editMode_isEditModeTrue() = runTest {
        val vm = createViewModel(measurementId = "meas-1")
        assertTrue(vm.state.value.isEditMode)
    }

    // --- onStart: create mode ---

    @Test
    fun onStart_createMode_loadsUnitFromPreferences() = runTest {
        preferencesStore.unit = MeasurementUnit.CM
        val vm = createViewModel()
        assertEquals(MeasurementUnit.CM, vm.state.value.unit)
    }

    @Test
    fun onStart_createMode_setsGenderFemale_andPopulatesSections() = runTest {
        val vm = createViewModel()
        assertEquals(CustomerGender.FEMALE, vm.state.value.gender)
        assertTrue(vm.state.value.sections.isNotEmpty())
    }

    @Test
    fun onStart_createMode_initializesAllFieldsToEmpty() = runTest {
        val vm = createViewModel()
        assertTrue(vm.state.value.fields.isNotEmpty())
        assertTrue(vm.state.value.fields.values.all { it == "" })
    }

    // --- onStart: edit mode ---

    @Test
    fun onStart_editMode_loadsMeasurement_populatesState() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val vm = createViewModel(measurementId = "meas-1")

        val state = vm.state.value
        assertEquals(CustomerGender.FEMALE, state.gender)
        assertEquals(MeasurementUnit.CM, state.unit)
        assertEquals("morning measurement", state.notes)
        assertFalse(state.isLoading)
    }

    @Test
    fun onStart_editMode_fieldsLoadedAsString_wholeNumberFormattedAsInt() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val vm = createViewModel(measurementId = "meas-1")

        // 90.0 → "90" (whole number), 70.5 → "70.5" (decimal)
        assertEquals("90", vm.state.value.fields["bust_circumference"])
        assertEquals("70.5", vm.state.value.fields["waist"])
    }

    @Test
    fun onStart_editMode_measurementNotFound_clearsLoading() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = emptyList()
        val vm = createViewModel(measurementId = "meas-missing")

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun onStart_editMode_observeError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.observeError = DataError.Network.UNKNOWN
        val vm = createViewModel(measurementId = "meas-1")

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun onStart_editMode_noAuthUser_clearsLoading() = runTest {
        // no signUp — auth has no current user
        val vm = createViewModel(measurementId = "meas-1")

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.gender)
    }

    // --- Gender change ---

    @Test
    fun onGenderChange_toMale_updatesSectionsAndResetsFields() = runTest {
        val vm = createViewModel()
        // starts as FEMALE after onStart
        assertEquals(CustomerGender.FEMALE, vm.state.value.gender)
        val femaleFieldCount = vm.state.value.fields.size

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))

        assertEquals(CustomerGender.MALE, vm.state.value.gender)
        assertTrue(vm.state.value.sections.isNotEmpty())
        assertTrue(vm.state.value.fields.size != femaleFieldCount || vm.state.value.fields.values.all { it == "" })
    }

    @Test
    fun onGenderChange_resetsSectionIndexToZero() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnSectionChange(2))
        assertEquals(2, vm.state.value.currentSectionIndex)

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))

        assertEquals(0, vm.state.value.currentSectionIndex)
    }

    // --- Section navigation ---

    @Test
    fun onSectionChange_updatesCurrentSectionIndex() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnSectionChange(3))
        assertEquals(3, vm.state.value.currentSectionIndex)
    }

    @Test
    fun onNextSection_incrementsIndex() = runTest {
        val vm = createViewModel()
        val initial = vm.state.value.currentSectionIndex
        vm.onAction(MeasurementFormAction.OnNextSection)
        assertEquals(initial + 1, vm.state.value.currentSectionIndex)
    }

    @Test
    fun onNextSection_fromLastDefaultSection_landsOnCustomPage() = runTest {
        val vm = createViewModel()
        // Capture the expected custom-page index up front so the assertion
        // doesn't re-read state on its expected side.
        val customPageIndex = vm.state.value.sections.size
        vm.onAction(MeasurementFormAction.OnSectionChange(customPageIndex - 1))
        vm.onAction(MeasurementFormAction.OnNextSection)
        // Custom page index == sections.size (one past the last default section).
        assertEquals(customPageIndex, vm.state.value.currentSectionIndex)
    }

    @Test
    fun onNextSection_doesNotExceedCustomPage() = runTest {
        val vm = createViewModel()
        val customPageIndex = vm.state.value.sections.size
        vm.onAction(MeasurementFormAction.OnSectionChange(customPageIndex))
        vm.onAction(MeasurementFormAction.OnNextSection)
        assertEquals(customPageIndex, vm.state.value.currentSectionIndex)
    }

    @Test
    fun onPreviousSection_decrementsIndex() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnSectionChange(2))
        vm.onAction(MeasurementFormAction.OnPreviousSection)
        assertEquals(1, vm.state.value.currentSectionIndex)
    }

    @Test
    fun onPreviousSection_doesNotGoBelowZero() = runTest {
        val vm = createViewModel()
        assertEquals(0, vm.state.value.currentSectionIndex)
        vm.onAction(MeasurementFormAction.OnPreviousSection)
        assertEquals(0, vm.state.value.currentSectionIndex)
    }

    // --- Toggle actions ---

    @Test
    fun onToggleShowMore_togglesIsCurrentSectionExpanded() = runTest {
        val vm = createViewModel()
        val initial = vm.state.value.isCurrentSectionExpanded
        vm.onAction(MeasurementFormAction.OnToggleShowMore)
        assertEquals(!initial, vm.state.value.isCurrentSectionExpanded)
        vm.onAction(MeasurementFormAction.OnToggleShowMore)
        assertEquals(initial, vm.state.value.isCurrentSectionExpanded)
    }

    @Test
    fun onToggleNotes_togglesIsNotesExpanded() = runTest {
        val vm = createViewModel()
        assertFalse(vm.state.value.isNotesExpanded)
        vm.onAction(MeasurementFormAction.OnToggleNotes)
        assertTrue(vm.state.value.isNotesExpanded)
        vm.onAction(MeasurementFormAction.OnToggleNotes)
        assertFalse(vm.state.value.isNotesExpanded)
    }

    // --- Field and notes changes ---

    @Test
    fun onFieldChange_updatesFieldValue() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        assertEquals("92", vm.state.value.fields["bust_circumference"])
    }

    @Test
    fun onFieldChange_doesNotAffectOtherFields() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        assertEquals("", vm.state.value.fields["waist"])
    }

    @Test
    fun onNotesChange_updatesNotes() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnNotesChange("taken after breakfast"))
        assertEquals("taken after breakfast", vm.state.value.notes)
    }

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
        // Name is mandatory; the Root effect pre-fills it in production, so set it
        // here to reach the same canSave=true precondition.
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        // Precondition: canSave is true, so a save WOULD persist here. This makes the
        // null assertions below prove that skip actively avoids the write, not that
        // there was simply nothing to save.
        assertTrue(vm.state.value.canSave)

        vm.onAction(MeasurementFormAction.OnSkipClick)

        assertIs<MeasurementFormEvent.SkipMeasurements>(vm.events.first())
        assertNull(measurementRepository.lastCreatedMeasurement)
        assertNull(measurementRepository.lastUpdatedMeasurement)
    }

    // --- Save: create mode ---

    @Test
    fun save_createMode_callsCreateMeasurement_andEmitsMeasurementSaved() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNotNull(measurementRepository.lastCreatedMeasurement)
        assertNull(measurementRepository.lastUpdatedMeasurement)
        // Standalone create (no fromCustomerCreation, no linkToOrderId) lands on
        // the read-only detail view rather than navigating back to the form's
        // caller — see Task 5.
        assertIs<MeasurementFormEvent.MeasurementSaved>(vm.events.first())
    }

    @Test
    fun save_createMode_parsesFieldsToDoubles_andFiltersZeros() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "90"))
        vm.onAction(MeasurementFormAction.OnFieldChange("waist", ""))  // blank → 0.0 → filtered out
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val saved = measurementRepository.lastCreatedMeasurement
        assertNotNull(saved)
        assertEquals(90.0, saved.fields["bust_circumference"])
        assertFalse(saved.fields.containsKey("waist"))
    }

    @Test
    fun save_createMode_whenNotEntitled_dropsCustomDraftValues() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = false)
        customFieldRepository.seedFields(listOf(
            customField(
                id = "f-both",
                label = "Cuff",
                genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
            ),
        ))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "90"))
        vm.onAction(MeasurementFormAction.OnFieldChange("f-both", "13"))
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))

        vm.onAction(MeasurementFormAction.OnSaveClick)

        val saved = measurementRepository.lastCreatedMeasurement
        assertNotNull(saved)
        assertEquals(90.0, saved.fields["bust_circumference"])
        assertFalse(saved.fields.containsKey("f-both"))
    }

    @Test
    fun save_createMode_whenEntitlementExpiresWithOnlyCustomDraft_doesNotCreateEmptyMeasurement() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(
                id = "f-both",
                label = "Cuff",
                genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
            ),
        ))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("f-both", "13"))

        fakeEntitlements.setEntitled(false)
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertFalse(vm.state.value.canSave)
        assertNull(measurementRepository.lastCreatedMeasurement)
    }

    @Test
    fun save_createMode_trimsBlanksNotes_storesNullWhenBlank() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnNotesChange("   "))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNull(measurementRepository.lastCreatedMeasurement?.notes)
    }

    // --- Save: post-save navigation (Task 5) ---

    @Test
    fun `save emits MeasurementSaved with the persisted id`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
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
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel(fromCustomerCreation = true)
        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.FEMALE))
        vm.onAction(MeasurementFormAction.OnNameChange("Agbada"))
        vm.onAction(MeasurementFormAction.OnFieldChange("waist", "31"))
        vm.events.test {
            vm.onAction(MeasurementFormAction.OnSaveClick)
            assertIs<MeasurementFormEvent.NavigateBack>(awaitItem())
        }
    }

    @Test
    fun `save with linkToOrderId still navigates back`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel(linkToOrderId = "order-1")
        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.FEMALE))
        vm.onAction(MeasurementFormAction.OnNameChange("Agbada"))
        vm.onAction(MeasurementFormAction.OnFieldChange("waist", "31"))
        vm.events.test {
            vm.onAction(MeasurementFormAction.OnSaveClick)
            assertIs<MeasurementFormEvent.NavigateBack>(awaitItem())
        }
    }

    // --- Save: edit mode ---

    @Test
    fun save_editMode_callsUpdateMeasurement_andEmitsMeasurementSaved() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val vm = createViewModel(measurementId = "meas-1")
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNotNull(measurementRepository.lastUpdatedMeasurement)
        assertNull(measurementRepository.lastCreatedMeasurement)
        // Standalone edit (no fromCustomerCreation, no linkToOrderId) lands on
        // the read-only detail view rather than navigating back — see Task 5.
        assertIs<MeasurementFormEvent.MeasurementSaved>(vm.events.first())
    }

    @Test
    fun save_editMode_preservesOriginalCreatedAtAndDateTaken() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val vm = createViewModel(measurementId = "meas-1")
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val saved = measurementRepository.lastUpdatedMeasurement
        assertNotNull(saved)
        assertEquals(12345L, saved.createdAt)
        assertEquals(0L, saved.dateTaken)
    }

    // --- Save: error paths ---

    @Test
    fun save_withNoAuthUser_doesNotCallRepository() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNull(measurementRepository.lastCreatedMeasurement)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun save_withRepositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.operationError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        // Provide at least one positive field so canSave is true and save()
        // actually reaches the repository (where the injected error fires).
        vm.onAction(MeasurementFormAction.OnFieldChange(key = "chest", value = "38"))
        vm.onAction(MeasurementFormAction.OnNameChange("Men's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    // --- Navigation ---

    @Test
    fun onNavigateBack_emitsNavigateBackEvent() = runTest {
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnNavigateBack)
        assertIs<MeasurementFormEvent.NavigateBack>(vm.events.first())
    }

    // --- Error dismiss ---

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.operationError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        // Provide at least one positive field so canSave is true and save()
        // actually reaches the repository (where the injected error fires).
        vm.onAction(MeasurementFormAction.OnFieldChange(key = "chest", value = "38"))
        vm.onAction(MeasurementFormAction.OnNameChange("Men's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(MeasurementFormAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }

    // --- PTSP-12: custom fields observe + sheet handlers ---

    @Test
    fun observeCustomFields_filtersByGenderAndArchive() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE)),
            customField(id = "f2", label = "Lapel", genders = setOf(CustomerGender.MALE)),
            customField(id = "f3", label = "Old", genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE), isArchived = true),
        ))
        val vm = createViewModel()
        // Gender auto-defaults to FEMALE for create mode
        val visibleIds = vm.state.value.customFields.map { it.id }
        assertEquals(listOf("f1"), visibleIds)
    }

    @Test
    fun onGenderChange_afterObserverFires_showsOtherGenderFieldsFromCache() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(id = "f1", label = "Bra cup", genders = setOf(CustomerGender.FEMALE)),
            customField(id = "f2", label = "Lapel", genders = setOf(CustomerGender.MALE)),
        ))
        val vm = createViewModel()
        // Default gender FEMALE → f1 visible, f2 not
        assertEquals(listOf("f1"), vm.state.value.customFields.map { it.id })

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))
        // f2 should now be visible — only passes if the unfiltered cache is consulted
        assertEquals(listOf("f2"), vm.state.value.customFields.map { it.id })
    }

    @Test
    fun observeCustomFields_archivedFieldWithCreateValue_isHiddenAndRemoved() = runTest {
        // Create mode has no "past measurement" value to preserve. If another
        // device archives this field while the form is open, remove the row and
        // its draft value so save cannot persist a now-archived custom key.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val field = customField(
            id = "f-both",
            label = "Cuff",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
        )
        customFieldRepository.seedFields(listOf(field))
        val vm = createViewModel()

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))
        vm.onAction(MeasurementFormAction.OnFieldChange("f-both", "13"))
        assertEquals(listOf("f-both"), vm.state.value.customFields.map { it.id })

        customFieldRepository.seedFields(listOf(field.copy(isArchived = true)))

        assertEquals(emptyList(), vm.state.value.customFields.map { it.id })
        assertNull(vm.state.value.fields["f-both"])
    }

    @Test
    fun onAddCustomFieldClick_whenEntitled_opensAddingSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun onAddCustomFieldClick_whenNotEntitled_emitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = false)
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        assertNull(vm.state.value.customFieldSheet)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onAddCustomFieldClick_awaitsHydratedEntitlementBeforeRouting() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = false).also {
            it.setAwaitedEntitled(true)
        }
        val vm = createViewModel()

        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)

        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun onAddCustomFieldClick_whenHydrationNeverCompletes_fallsBackToCurrentEntitlement() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = false).also {
            it.neverHydrate()
        }
        val vm = createViewModel()

        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        runCurrent()
        assertNull(vm.state.value.customFieldSheet)

        advanceTimeBy(2_000L)
        runCurrent()

        assertNull(vm.state.value.customFieldSheet)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onAddCustomFieldClick_whenHydrationNeverCompletesAndCurrentEntitled_opensSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = true).also {
            it.neverHydrate()
        }
        val vm = createViewModel()

        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        advanceTimeBy(2_000L)
        runCurrent()

        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun onLockedCustomFieldClick_whenHydratedNotEntitled_emitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = false)
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnLockedCustomFieldClick)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onEditCustomFieldClick_opensEditingSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnEditCustomFieldClick("f1"))
        val sheet = vm.state.value.customFieldSheet
        assertIs<CustomFieldSheet.Editing>(sheet)
        assertEquals("Cuff", sheet.field.label)
    }

    @Test
    fun onArchiveCustomFieldRequest_opensConfirmSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldRequest("f1"))
        val sheet = vm.state.value.customFieldSheet
        assertIs<CustomFieldSheet.ConfirmArchive>(sheet)
    }

    @Test
    fun onCustomFieldSheetDismiss_clearsSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
        vm.onAction(MeasurementFormAction.OnCustomFieldSheetDismiss)
        assertNull(vm.state.value.customFieldSheet)
    }

    // --- PTSP-12: save/update/archive ---

    @Test
    fun saveCustomField_create_callsRepoCreate_withNewUuid_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "Sleeve cuff width",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
        ))
        val created = customFieldRepository.lastCreatedField
        assertNotNull(created)
        assertTrue(created.id.isNotBlank())
        assertEquals("Sleeve cuff width", created.label)
        assertEquals(setOf(CustomerGender.FEMALE, CustomerGender.MALE), created.genders)
        assertFalse(created.isArchived)
        assertNull(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_create_withInitialValue_seedsCurrentMeasurementField() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "Sleeve cuff width",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
            initialValue = "12.5",
        ))

        val created = customFieldRepository.lastCreatedField
        assertNotNull(created)
        assertEquals("12.5", vm.state.value.fields[created.id])
        assertNull(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_create_withInitialValueForOtherGender_doesNotSeedHiddenField() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        assertEquals(CustomerGender.FEMALE, vm.state.value.gender)

        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "Lapel",
            genders = setOf(CustomerGender.MALE),
            initialValue = "4",
        ))

        val created = customFieldRepository.lastCreatedField
        assertNotNull(created)
        assertNull(vm.state.value.fields[created.id])
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun customFieldDraft_updatesStayInViewModelState() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()

        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnCustomFieldDraftLabelChange("Back fir"))
        vm.onAction(MeasurementFormAction.OnCustomFieldDraftInitialValueChange("4"))
        vm.onAction(MeasurementFormAction.OnCustomFieldDraftGendersChange(setOf(CustomerGender.MALE)))

        val sheet = vm.state.value.customFieldSheet
        assertIs<CustomFieldSheet.Adding>(sheet)
        assertEquals("Back fir", sheet.draft.label)
        assertEquals("4", sheet.draft.initialValue)
        assertEquals(setOf(CustomerGender.MALE), sheet.draft.genders)
    }

    @Test
    fun saveCustomField_edit_callsRepoUpdate_preservesId_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(existing))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnEditCustomFieldClick("f1"))
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = "f1",
            label = "Sleeve cuff",
            genders = setOf(CustomerGender.FEMALE),
        ))
        val updated = customFieldRepository.lastUpdatedField
        assertNotNull(updated)
        assertEquals("f1", updated.id)
        assertEquals("Sleeve cuff", updated.label)
        assertNull(customFieldRepository.lastCreatedField)
        assertNull(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_blankLabel_doesNotCallRepo_andKeepsSheetOpen() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "   ",
            genders = setOf(CustomerGender.FEMALE),
        ))
        assertNull(customFieldRepository.lastCreatedField)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_emptyGenders_doesNotCallRepo_andKeepsSheetOpen() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "X",
            genders = emptySet(),
        ))
        assertNull(customFieldRepository.lastCreatedField)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_whenEntitlementLost_emitsUpgrade_andDoesNotCallRepo() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        // VM was created while entitled; entitlement lost (e.g., welcome ended)
        fakeEntitlements.setEntitled(false)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "X",
            genders = setOf(CustomerGender.FEMALE),
        ))
        assertNull(customFieldRepository.lastCreatedField)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onArchiveCustomFieldConfirm_callsRepoArchive_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldRequest("f1"))
        assertIs<CustomFieldSheet.ConfirmArchive>(vm.state.value.customFieldSheet)
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm("f1"))
        assertEquals("f1", customFieldRepository.lastArchivedFieldId)
        assertNull(vm.state.value.customFieldSheet)
    }

    @Test
    fun onArchiveCustomFieldConfirm_removesFieldFromCurrentForm() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("f1", "12.5"))

        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm("f1"))

        assertNull(vm.state.value.fields["f1"])
        assertEquals(emptyList(), vm.state.value.customFields.map { it.id })
    }

    @Test
    fun onArchiveCustomFieldConfirm_inEditMode_clearsRecordedFieldValueOnThisMeasurement() = runTest {
        // PTSP-30: deleting a custom field while editing a measurement that
        // recorded it clears the value here too, so the row disappears
        // immediately and the value is dropped from THIS measurement on save.
        // (Other measurements that recorded the field keep their values — their
        // documents are untouched.)
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        measurementRepository.measurementsList = listOf(
            fakeMeasurement(id = "meas-1").copy(fields = mapOf("f1" to 12.5))
        )
        val vm = createViewModel(measurementId = "meas-1")

        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm("f1"))
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNull(vm.state.value.fields["f1"])
        assertEquals(emptyList(), vm.state.value.customFields.map { it.id })
        assertNull(measurementRepository.lastUpdatedMeasurement?.fields?.get("f1"))
    }

    @Test
    fun archiveCustomField_whenNotEntitled_emitsUpgrade_andDoesNotCallRepo() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        // Open the confirm sheet first so the action is reachable
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldRequest("f1"))
        // Entitlement is lost mid-edit
        fakeEntitlements.setEntitled(false)
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm("f1"))

        assertNull(customFieldRepository.lastArchivedFieldId)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    // --- PTSP-12: loadMeasurement key preservation ---

    @Test
    fun loadMeasurement_withCustomFieldValues_populatesAllKeys() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(id = "custom-1", label = "Cuff", genders = setOf(CustomerGender.FEMALE)),
        ))
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.FEMALE,
            fields = mapOf(
                "bust_circumference" to 92.0,
                "custom-1" to 12.5,
            ),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")

        assertEquals("12.5", vm.state.value.fields["custom-1"])
        assertEquals("92", vm.state.value.fields["bust_circumference"])
    }

    @Test
    fun loadMeasurement_withOrphanKeys_preservesValuesOnRoundtrip() = runTest {
        // A measurement stored with a custom-field key whose definition the
        // user has archived (or that was imported from another source). The
        // form must NOT drop the value when saving.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // No matching definition in customFieldRepository — that's the point
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.FEMALE,
            fields = mapOf("orphan-key-99" to 7.0),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")
        // Name is mandatory; the seeded measurement has none, so set it (the Root
        // effect would pre-fill in production). Then save without other modification.
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val updated = measurementRepository.lastUpdatedMeasurement
        assertNotNull(updated)
        assertEquals(7.0, updated.fields["orphan-key-99"])
    }

    @Test
    fun loadMeasurement_filtersCustomFieldsByMeasurementGender_notObserverDefault() = runTest {
        // Edit-mode race: gender starts null when the observer first emits, so the
        // wildcard filter would pass ALL fields. loadMeasurement must re-filter
        // against the measurement's actual gender so opposite-gender custom fields
        // don't leak into the form and onto save.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(id = "custom-female", label = "Bra cup", genders = setOf(CustomerGender.FEMALE)),
            customField(id = "custom-male", label = "Lapel", genders = setOf(CustomerGender.MALE)),
        ))
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.MALE,
            fields = mapOf("custom-male" to 8.0),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")

        val visibleIds = vm.state.value.customFields.map { it.id }
        assertEquals(listOf("custom-male"), visibleIds)
    }

    @Test
    fun loadMeasurement_archivedFieldWithRecordedValue_isStillVisible() = runTest {
        // Spec promise: "Values already recorded stay visible on past measurements."
        // An archived field with a recorded value must surface in the edit form so
        // the tailor can see/correct/save the value, not silently leak via the
        // orphan path.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(
                id = "archived-cuff",
                label = "Cuff",
                genders = setOf(CustomerGender.FEMALE),
                isArchived = true,
            ),
        ))
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.FEMALE,
            fields = mapOf("archived-cuff" to 11.0),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")

        val visibleIds = vm.state.value.customFields.map { it.id }
        assertEquals(listOf("archived-cuff"), visibleIds)
        assertEquals("11", vm.state.value.fields["archived-cuff"])
    }

    @Test
    fun observeEntitlements_updatesStateWhenFlowEmits() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // Start NOT entitled (simulates cold-start default before hydration)
        fakeEntitlements = FakeEntitlementsProvider(initialCanUseCustomMeasurements = false)
        val vm = createViewModel()
        assertFalse(vm.state.value.canUseCustomMeasurements)

        // Hydration lands → entitlement flips to true
        fakeEntitlements.setEntitled(true)
        assertTrue(vm.state.value.canUseCustomMeasurements)
    }

    @Test
    fun observeEntitlements_seedsTierIntoState() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // FakeEntitlementsProvider hardcodes tier = FREE; verify it lands in state
        // so the "First Month" pill can correctly distinguish trial-only (FREE +
        // welcome) from permanent (Pro/Atelier) access in CustomFieldsSection.
        val vm = createViewModel()
        assertEquals(SubscriptionTier.FREE, vm.state.value.tier)
    }

    @Test
    fun saveCustomField_edit_preservesIsArchivedFromExistingField() = runTest {
        // HIGH bug: editing an archived field's label/genders MUST keep the
        // archive flag — otherwise the field silently un-archives, contradicting
        // the soft-archive contract.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val archived = customField(
            id = "f1",
            label = "Old",
            genders = setOf(CustomerGender.FEMALE),
            isArchived = true,
        )
        customFieldRepository.seedFields(listOf(archived))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = "f1",
            label = "Old (renamed)",
            genders = setOf(CustomerGender.FEMALE),
        ))
        val updated = customFieldRepository.lastUpdatedField
        assertNotNull(updated)
        assertTrue(updated.isArchived)
        assertEquals("Old (renamed)", updated.label)
    }

    @Test
    fun onGenderChange_preservesCustomFieldValuesForFieldsVisibleInBothGenders() = runTest {
        // Medium bug: switching gender used to wipe typed values for custom
        // fields because the field-map reset only included template keys.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(
                id = "f-both",
                label = "Cuff",
                genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
            ),
        ))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("f-both", "13"))
        assertEquals("13", vm.state.value.fields["f-both"])

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))
        assertEquals("13", vm.state.value.fields["f-both"])
    }

    @Test
    fun onGenderChange_editMode_preservesRecordedArchivedCustomRowAndOrphanValuesOnSave() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(
                id = "archived-cuff",
                label = "Cuff",
                genders = setOf(CustomerGender.FEMALE),
                isArchived = true,
            ),
        ))
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.FEMALE,
            fields = mapOf(
                "archived-cuff" to 11.0,
                "orphan-key-99" to 7.0,
            ),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))

        val customFieldsAfterGenderChange = vm.state.value.customFields
        assertTrue(customFieldsAfterGenderChange.any { it.id == "archived-cuff" })
        assertEquals("11", vm.state.value.fields["archived-cuff"])

        vm.onAction(MeasurementFormAction.OnNameChange("Men's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val updated = measurementRepository.lastUpdatedMeasurement
        assertNotNull(updated)
        assertEquals(11.0, updated.fields["archived-cuff"])
        assertEquals(7.0, updated.fields["orphan-key-99"])
    }

    // --- Measurement name (mandatory + pre-filled) ---

    @Test
    fun blankName_blocksSave() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        // Clearing the (default-filled) name must block save.
        vm.onAction(MeasurementFormAction.OnNameChange(""))

        assertFalse(vm.state.value.canSave)

        vm.onAction(MeasurementFormAction.OnSaveClick)
        assertNull(measurementRepository.lastCreatedMeasurement)
    }

    @Test
    fun namePersists_onCreate() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        vm.onAction(MeasurementFormAction.OnNameChange("Wedding Agbada"))

        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertEquals("Wedding Agbada", measurementRepository.lastCreatedMeasurement?.name)
    }

    @Test
    fun namePersists_onEdit() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(
            fakeMeasurement(id = "meas-1").copy(name = "Original"),
        )
        val vm = createViewModel(measurementId = "meas-1")
        vm.onAction(MeasurementFormAction.OnNameChange("Renamed"))

        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertEquals("Renamed", measurementRepository.lastUpdatedMeasurement?.name)
    }

    @Test
    fun editPrefill_setsName() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(
            fakeMeasurement(id = "meas-1").copy(name = "X"),
        )
        val vm = createViewModel(measurementId = "meas-1")

        assertEquals("X", vm.state.value.name)
    }

    @Test
    fun create_startsWithEmptyName() = runTest {
        val vm = createViewModel()
        assertEquals("", vm.state.value.name)
    }

    private fun customField(
        id: String,
        label: String,
        genders: Set<CustomerGender>,
        isArchived: Boolean = false,
    ) = CustomMeasurementField(
        id = id,
        label = label,
        genders = genders,
        isArchived = isArchived,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // --- Analytics ---

    @Test
    fun create_save_logs_measurement_added() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val analytics = FakeAnalytics()
        val vm = createViewModel(analytics = analytics)
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)
        runCurrent()
        assertTrue(analytics.events.contains(AnalyticsEvent.MeasurementAdded))
    }

    @Test
    fun edit_save_does_not_log_measurement_added() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val analytics = FakeAnalytics()
        val vm = createViewModel(measurementId = "meas-1", analytics = analytics)
        vm.onAction(MeasurementFormAction.OnNameChange("Women's measurement 1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)
        runCurrent()
        assertFalse(analytics.events.any { it is AnalyticsEvent.MeasurementAdded })
    }
}

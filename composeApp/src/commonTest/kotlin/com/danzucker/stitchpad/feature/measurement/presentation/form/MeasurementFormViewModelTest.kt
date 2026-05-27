package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
    ): MeasurementFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (measurementId != null) put("measurementId", measurementId)
        }
        val vm = MeasurementFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            measurementRepository = measurementRepository,
            authRepository = authRepository,
            measurementPreferencesStore = preferencesStore,
            orderRepository = orderRepository,
            customFieldRepository = customFieldRepository,
            entitlements = fakeEntitlements,
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
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements = _flow.value

        fun setEntitled(can: Boolean) {
            _flow.value = build(can)
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
    fun onNextSection_doesNotExceedLastSection() = runTest {
        val vm = createViewModel()
        val lastIndex = vm.state.value.sections.size - 1
        vm.onAction(MeasurementFormAction.OnSectionChange(lastIndex))
        vm.onAction(MeasurementFormAction.OnNextSection)
        assertEquals(lastIndex, vm.state.value.currentSectionIndex)
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

    // --- Save: create mode ---

    @Test
    fun save_createMode_callsCreateMeasurement_andNavigatesBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "92"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNotNull(measurementRepository.lastCreatedMeasurement)
        assertNull(measurementRepository.lastUpdatedMeasurement)
        assertIs<MeasurementFormEvent.NavigateBack>(vm.events.first())
    }

    @Test
    fun save_createMode_parsesFieldsToDoubles_andFiltersZeros() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnFieldChange("bust_circumference", "90"))
        vm.onAction(MeasurementFormAction.OnFieldChange("waist", ""))  // blank → 0.0 → filtered out
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val saved = measurementRepository.lastCreatedMeasurement
        assertNotNull(saved)
        assertEquals(90.0, saved.fields["bust_circumference"])
        assertFalse(saved.fields.containsKey("waist"))
    }

    @Test
    fun save_createMode_trimsBlanksNotes_storesNullWhenBlank() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnNotesChange("   "))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNull(measurementRepository.lastCreatedMeasurement?.notes)
    }

    // --- Save: edit mode ---

    @Test
    fun save_editMode_callsUpdateMeasurement_andNavigatesBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val vm = createViewModel(measurementId = "meas-1")
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertNotNull(measurementRepository.lastUpdatedMeasurement)
        assertNull(measurementRepository.lastCreatedMeasurement)
        assertIs<MeasurementFormEvent.NavigateBack>(vm.events.first())
    }

    @Test
    fun save_editMode_preservesOriginalCreatedAtAndDateTaken() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        val vm = createViewModel(measurementId = "meas-1")
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
    fun observeCustomFields_archivedFieldWithEmptyCreateValue_isHidden() = runTest {
        // Low bug: create mode may have an empty string entry for a custom
        // field after gender filtering. If another device archives that field,
        // the observer must not treat the empty key as recorded data.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val field = customField(
            id = "f-both",
            label = "Cuff",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
        )
        customFieldRepository.seedFields(listOf(field))
        val vm = createViewModel()

        vm.onAction(MeasurementFormAction.OnGenderChange(CustomerGender.MALE))
        assertEquals("", vm.state.value.fields["f-both"])
        assertEquals(listOf("f-both"), vm.state.value.customFields.map { it.id })

        customFieldRepository.seedFields(listOf(field.copy(isArchived = true)))

        assertEquals(emptyList(), vm.state.value.customFields.map { it.id })
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
    fun onLockedCustomFieldClick_emitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
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
    fun onArchiveCustomFieldConfirm_inEditMode_preservesRecordedFieldValue() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        measurementRepository.measurementsList = listOf(
            fakeMeasurement(id = "meas-1").copy(fields = mapOf("f1" to 12.5))
        )
        val vm = createViewModel(measurementId = "meas-1")

        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm("f1"))
        vm.onAction(MeasurementFormAction.OnSaveClick)

        assertEquals("12.5", vm.state.value.fields["f1"])
        assertEquals(12.5, measurementRepository.lastUpdatedMeasurement?.fields?.get("f1"))
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
        // Save without modification
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
    fun onGenderChange_editMode_preservesRecordedCustomAndOrphanValuesOnSave() = runTest {
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
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val updated = measurementRepository.lastUpdatedMeasurement
        assertNotNull(updated)
        assertEquals(11.0, updated.fields["archived-cuff"])
        assertEquals(7.0, updated.fields["orphan-key-99"])
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
}

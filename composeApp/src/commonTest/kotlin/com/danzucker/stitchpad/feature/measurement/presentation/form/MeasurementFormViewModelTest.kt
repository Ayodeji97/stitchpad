package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.measurement.data.FakeMeasurementPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository()
        preferencesStore = FakeMeasurementPreferencesStore()
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
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
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
        vm.onAction(MeasurementFormAction.OnSaveClick)
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(MeasurementFormAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }
}

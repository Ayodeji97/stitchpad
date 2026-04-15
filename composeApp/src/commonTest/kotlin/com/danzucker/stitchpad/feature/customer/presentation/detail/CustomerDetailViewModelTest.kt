package com.danzucker.stitchpad.feature.customer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CustomerDetailViewModelTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        customerRepository = FakeCustomerRepository()
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
    ): CustomerDetailViewModel {
        val vm = CustomerDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("customerId" to customerId)),
            customerRepository = customerRepository,
            measurementRepository = measurementRepository,
            authRepository = authRepository,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun fakeCustomer(
        id: String = "customer-1",
        name: String = "Ade Fashions",
        phone: String = "+2348012345678",
    ) = Customer(id = id, userId = "test-uid", name = name, phone = phone)

    private fun fakeMeasurement(
        id: String = "meas-1",
        customerId: String = "customer-1",
    ) = Measurement(
        id = id,
        customerId = customerId,
        gender = CustomerGender.FEMALE,
        fields = mapOf("bust_circumference" to 90.0),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 0L,
        createdAt = 0L,
    )

    // --- Load data ---

    @Test
    fun loadData_success_populatesCustomerAndMeasurements() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = fakeCustomer()
        measurementRepository.measurementsList = listOf(fakeMeasurement())

        val vm = createViewModel()

        val state = vm.state.value
        assertNotNull(state.customer)
        assertEquals("Ade Fashions", state.customer?.name)
        assertEquals(1, state.measurements.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadData_noAuthUser_setsIsLoadingFalse() = runTest {
        // no signUp — no current user
        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.customer)
        assertTrue(vm.state.value.measurements.isEmpty())
    }

    @Test
    fun loadData_customerError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.NOT_FOUND

        val vm = createViewModel()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadData_measurementError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = fakeCustomer()
        measurementRepository.observeError = DataError.Network.UNKNOWN

        val vm = createViewModel()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadData_emptyMeasurements_populatesEmptyList() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = fakeCustomer()
        measurementRepository.measurementsList = emptyList()

        val vm = createViewModel()

        assertTrue(vm.state.value.measurements.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    // --- Navigation events ---

    @Test
    fun onEditCustomerClick_emitsNavigateToEditCustomer() = runTest {
        val vm = createViewModel()
        vm.onAction(CustomerDetailAction.OnEditCustomerClick)
        assertIs<CustomerDetailEvent.NavigateToEditCustomer>(vm.events.first())
    }

    @Test
    fun onAddMeasurementClick_emitsNavigateToAddMeasurement() = runTest {
        val vm = createViewModel()
        vm.onAction(CustomerDetailAction.OnAddMeasurementClick)
        assertIs<CustomerDetailEvent.NavigateToAddMeasurement>(vm.events.first())
    }

    @Test
    fun onMeasurementClick_emitsNavigateToEditMeasurement_withCorrectId() = runTest {
        val vm = createViewModel()
        val measurement = fakeMeasurement(id = "meas-42")
        vm.onAction(CustomerDetailAction.OnMeasurementClick(measurement))

        val event = vm.events.first()
        assertIs<CustomerDetailEvent.NavigateToEditMeasurement>(event)
        assertEquals("meas-42", event.measurementId)
    }

    @Test
    fun onNavigateBack_emitsNavigateBack() = runTest {
        val vm = createViewModel()
        vm.onAction(CustomerDetailAction.OnNavigateBack)
        assertIs<CustomerDetailEvent.NavigateBack>(vm.events.first())
    }

    // --- Delete measurement flow ---

    @Test
    fun onDeleteMeasurementClick_showsDeleteDialog_withMeasurement() = runTest {
        val vm = createViewModel()
        val measurement = fakeMeasurement()

        vm.onAction(CustomerDetailAction.OnDeleteMeasurementClick(measurement))

        assertTrue(vm.state.value.showDeleteDialog)
        assertEquals(measurement, vm.state.value.measurementToDelete)
    }

    @Test
    fun onDismissDeleteDialog_hidesDialog_andClearsMeasurementToDelete() = runTest {
        val vm = createViewModel()
        vm.onAction(CustomerDetailAction.OnDeleteMeasurementClick(fakeMeasurement()))
        assertTrue(vm.state.value.showDeleteDialog)

        vm.onAction(CustomerDetailAction.OnDismissDeleteDialog)

        assertFalse(vm.state.value.showDeleteDialog)
        assertNull(vm.state.value.measurementToDelete)
    }

    @Test
    fun onConfirmDelete_callsDeleteMeasurement_andHidesDialog() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        val measurement = fakeMeasurement(id = "meas-1")
        vm.onAction(CustomerDetailAction.OnDeleteMeasurementClick(measurement))
        vm.onAction(CustomerDetailAction.OnConfirmDelete)

        assertEquals("meas-1", measurementRepository.lastDeletedMeasurementId)
        assertFalse(vm.state.value.showDeleteDialog)
        assertNull(vm.state.value.measurementToDelete)
    }

    @Test
    fun onConfirmDelete_withNoMeasurementToDelete_doesNotCallRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        // no OnDeleteMeasurementClick — measurementToDelete is null
        vm.onAction(CustomerDetailAction.OnConfirmDelete)

        assertNull(measurementRepository.lastDeletedMeasurementId)
    }

    @Test
    fun deleteMeasurement_withRepositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        measurementRepository.operationError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        vm.onAction(CustomerDetailAction.OnDeleteMeasurementClick(fakeMeasurement()))
        vm.onAction(CustomerDetailAction.OnConfirmDelete)

        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun deleteMeasurement_withNoAuthUser_doesNotCallRepository() = runTest {
        // no signUp
        val vm = createViewModel()
        vm.onAction(CustomerDetailAction.OnDeleteMeasurementClick(fakeMeasurement()))
        vm.onAction(CustomerDetailAction.OnConfirmDelete)

        assertNull(measurementRepository.lastDeletedMeasurementId)
    }

    // --- Error dismiss ---

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(CustomerDetailAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }
}

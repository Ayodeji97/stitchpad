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
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        // Fakes ignore the userId value itself (they key off customerId/measurementId),
        // but the ViewModel bails out of every observe*() early when getCurrentUser()
        // is null — so a signed-in user must be present for these tests to reach them.
        authRepository.currentUser = User(
            id = "user-1",
            email = "tailor@example.com",
            displayName = "Test Tailor",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
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

        val event = analytics.events.filterIsInstance<AnalyticsEvent.MeasurementDetailViewed>().single()
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

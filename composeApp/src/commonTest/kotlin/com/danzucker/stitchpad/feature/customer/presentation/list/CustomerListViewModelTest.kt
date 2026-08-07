package com.danzucker.stitchpad.feature.customer.presentation.list

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModelTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var activeWorkshopProvider: FakeActiveWorkshopProvider
    private lateinit var freemiumRepository: FreemiumRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        customerRepository = FakeCustomerRepository()
        measurementRepository = FakeMeasurementRepository()
        orderRepository = FakeOrderRepository()
        activeWorkshopProvider = FakeActiveWorkshopProvider()
        freemiumRepository = FakeFreemiumRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): CustomerListViewModel {
        val vm = CustomerListViewModel(
            customerRepository = customerRepository,
            orderRepository = orderRepository,
            activeWorkshopProvider = activeWorkshopProvider,
            freemiumRepository = freemiumRepository,
            measurementEntryResolver = MeasurementEntryResolver(measurementRepository, activeWorkshopProvider),
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
        fields = mapOf("bust_circumference" to "90"),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 0L,
        createdAt = 0L,
    )

    // --- View measurements from the customer actions sheet ---

    @Test
    fun `view measurements from sheet with one measurement navigates to detail`() = runTest {
        customerRepository.customersList = listOf(fakeCustomer())
        measurementRepository.measurementsList = listOf(fakeMeasurement(id = "meas-1"))
        val vm = createViewModel()
        vm.onAction(CustomerListAction.OnOverflowClick(fakeCustomer()))

        vm.events.test {
            vm.onAction(CustomerListAction.OnViewMeasurementsFromRow("customer-1"))
            advanceTimeBy(451)
            runCurrent()
            val event = assertIs<CustomerListEvent.NavigateToMeasurementDetail>(awaitItem())
            assertEquals("customer-1", event.customerId)
            assertEquals("meas-1", event.measurementId)
        }
        assertNull(vm.state.value.actionsSheetForId)
    }

    @Test
    fun `view measurements from sheet with several measurements navigates to customer detail`() = runTest {
        customerRepository.customersList = listOf(fakeCustomer())
        measurementRepository.measurementsList = listOf(
            fakeMeasurement(id = "m1"),
            fakeMeasurement(id = "m2"),
        )
        val vm = createViewModel()

        vm.events.test {
            vm.onAction(CustomerListAction.OnViewMeasurementsFromRow("customer-1"))
            advanceTimeBy(451)
            runCurrent()
            val event = assertIs<CustomerListEvent.NavigateToCustomerDetail>(awaitItem())
            assertEquals("customer-1", event.customerId)
        }
    }

    @Test
    fun `view measurements from sheet with none navigates to empty-mode detail`() = runTest {
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

    // --- Per-row pending-sync state (snapshot metadata -> Customer.isPendingSync) ---

    @Test
    fun pending_customers_reach_the_list_state_marked() = runTest {
        val pending = Customer(
            id = "c1",
            userId = "u1",
            name = "Offline Ghost",
            phone = "08000000000",
            isPendingSync = true,
        )
        val vm = createViewModel()
        customerRepository.customersList = listOf(pending)
        runCurrent()

        val row = vm.state.value.customers.first { it.id == "c1" }
        assertTrue(row.isPendingSync)
    }

    // --- Staff read-only contact guards (Slice 6c) ---

    private val activeStaffSession = WorkshopSession(
        authUid = "s",
        workshopUid = "o",
        role = StaffRole.STAFF,
        membershipStatus = MembershipStatus.ACTIVE,
    )

    @Test
    fun activeStaffSession_setsIsActiveStaffTrue() = runTest {
        activeWorkshopProvider.setSession(activeStaffSession)
        val vm = createViewModel()

        assertTrue(vm.state.value.isActiveStaff)
    }

    @Test
    fun staff_onMessageWhatsApp_emitsNoLaunchEvent() = runTest {
        // Seed a customer with a usable number so only the staff guard — not a
        // missing/blank phone — can be what blocks the WhatsApp launch.
        customerRepository.customersList = listOf(fakeCustomer(phone = "+2348012345678"))
        activeWorkshopProvider.setSession(activeStaffSession)
        val vm = createViewModel()
        assertTrue(vm.state.value.isActiveStaff)

        vm.events.test {
            vm.onAction(CustomerListAction.OnMessageWhatsApp(fakeCustomer(phone = "+2348012345678")))
            advanceTimeBy(451)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun staff_rowMutations_emitNoNavigationAndDoNotArmDelete() = runTest {
        customerRepository.customersList = listOf(fakeCustomer())
        activeWorkshopProvider.setSession(activeStaffSession)
        val vm = createViewModel()
        assertTrue(vm.state.value.isActiveStaff)

        vm.events.test {
            vm.onAction(CustomerListAction.OnEditCustomerFromRow("c1"))
            vm.onAction(CustomerListAction.OnNewOrderFromRow("c1"))
            vm.onAction(CustomerListAction.OnAddMeasurementFromRow("c1"))
            vm.onAction(CustomerListAction.OnDeleteCustomerClick(fakeCustomer()))
            // The confirm/execute is guarded too (cold-start race).
            vm.onAction(CustomerListAction.OnConfirmDelete)
            advanceTimeBy(451)
            runCurrent()
            expectNoEvents()
        }
        // Delete never armed the confirm dialog.
        assertFalse(vm.state.value.showDeleteDialog)
    }

    private class FakeFreemiumRepository : FreemiumRepository {
        override suspend fun reconcileSlots(): EmptyResult<DataError.Network> = Result.Success(Unit)
        override suspend fun swapCustomerSlot(
            promote: String,
            demote: String,
        ): EmptyResult<DataError.Network> = Result.Success(Unit)
    }
}

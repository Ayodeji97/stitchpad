package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun signIn() {
        authRepository.signUpWithEmail("t@t.com", "pass", "Ade Bello")
    }

    private fun fakeCustomer(
        id: String = "c1",
        name: String = "Ada Lovelace",
        slotState: CustomerSlotState = CustomerSlotState.ACTIVE,
    ) = Customer(
        id = id,
        userId = "test-uid",
        name = name,
        phone = "+2348012345678",
        slotState = slotState,
    )

    private fun fakeOrder(
        id: String,
        archivedAt: Long? = null,
        customerName: String = "Ada Lovelace",
    ) = Order(
        id = id,
        userId = "test-uid",
        customerId = "c1",
        customerName = customerName,
        items = listOf(
            OrderItem(id = "i-$id", garmentType = GarmentType.SUIT, description = "", price = 10_000.0)
        ),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 10_000.0,
        deadline = null,
        notes = null,
        archivedAt = archivedAt,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun TestScope.createViewModel(): OrderListViewModel {
        val vm = OrderListViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
        )
        // Subscribe so the state flow's onStart loads orders + customers.
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun onAddOrderClick_withNoCustomers_emitsNavigateToAddCustomerFirst() = runTest {
        // Brand-new user: the Orders-tab FAB must gate to "add a customer first"
        // instead of dropping the user on an empty customer picker.
        signIn()
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToAddCustomerFirst, vm.events.first())
    }

    @Test
    fun onAddOrderClick_withActiveCustomer_emitsNavigateToOrderForm() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToOrderForm, vm.events.first())
    }

    @Test
    fun onAddOrderClick_withOnlyLockedCustomers_emitsNavigateToAddCustomerFirst() = runTest {
        // Locked customers aren't selectable in the order form's picker, so a
        // user whose customers are all locked must be gated rather than dropped
        // on an empty picker dead-end.
        signIn()
        customerRepository.customersList =
            listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToAddCustomerFirst, vm.events.first())
    }

    @Test
    fun onAddOrderClick_whenCustomerQueryErrors_failsOpenToOrderForm() = runTest {
        // If we can't resolve the customer list, route to the form (which surfaces
        // whatever's cached) rather than wrongly gating a customer-owning user.
        signIn()
        customerRepository.shouldReturnError = DataError.Network.NO_INTERNET
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnAddOrderClick)

        assertEquals(OrderListEvent.NavigateToOrderForm, vm.events.first())
    }

    // --- Archived view + restore (PTSP-37) ---

    @Test
    fun defaultView_showsActiveOrdersOnly_archivedHidden() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "active"),
            fakeOrder(id = "archived", archivedAt = 100L),
        )
        val vm = createViewModel()

        assertFalse(vm.state.value.showArchived)
        assertEquals(listOf("active"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun onShowArchived_switchesToArchivedOrders() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "active"),
            fakeOrder(id = "archived", archivedAt = 100L),
        )
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnShowArchived)

        assertTrue(vm.state.value.showArchived)
        assertEquals(listOf("archived"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun selectingStatusFilter_exitsArchivedView() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "active"),
            fakeOrder(id = "archived", archivedAt = 100L),
        )
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnShowArchived)

        vm.onAction(OrderListAction.OnStatusFilterChange(null))

        assertFalse(vm.state.value.showArchived)
        assertEquals(listOf("active"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun archivedLoading_clearsAfterSnapshotLoads() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "active"),
            fakeOrder(id = "archived", archivedAt = 100L),
        )
        val vm = createViewModel()

        // Once the archived snapshot emits, the view stops spinning — so the empty
        // state can never flash before archived orders have loaded.
        assertFalse(vm.state.value.isArchivedLoading)
    }

    @Test
    fun archivedError_clearsLoading_andSurfacesWhileViewingArchived() = runTest {
        signIn()
        orderRepository.ordersList = listOf(fakeOrder(id = "archived", archivedAt = 100L))
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnShowArchived)

        // Archived stream fails (independent of the active stream) on a fresh emit.
        orderRepository.archivedError = DataError.Network.UNKNOWN
        orderRepository.ordersList = listOf(fakeOrder(id = "archived2", archivedAt = 200L))

        assertFalse(vm.state.value.isArchivedLoading)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun archivedError_onActiveView_doesNotSurface_butClearsLoading() = runTest {
        signIn()
        orderRepository.archivedError = DataError.Network.UNKNOWN
        val vm = createViewModel()

        // Not viewing archived — the active stream owns the error surface, so an
        // archived-only failure must not pop an error here, but loading still clears.
        assertFalse(vm.state.value.showArchived)
        assertFalse(vm.state.value.isArchivedLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun onRestoreOrderClick_callsUnarchive_andEmitsOrderRestored() = runTest {
        signIn()
        val archived = fakeOrder(id = "archived", archivedAt = 100L)
        orderRepository.ordersList = listOf(archived)
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnShowArchived)

        vm.onAction(OrderListAction.OnRestoreOrderClick(archived))

        assertEquals("archived", orderRepository.lastUnarchivedOrderId)
        assertEquals(OrderListEvent.OrderRestored, vm.events.first())
    }
}

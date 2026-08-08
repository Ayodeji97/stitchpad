package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Slice 6c — the Orders LIST must be money-free for an active staff member. The list
 * VM has no per-row contact actions, so this covers the two staff-relevant surfaces:
 * the [OrderListState.isActiveStaff] flag (the screen hides every price / payment /
 * profit affordance off it) and the profit-toggle guard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderListStaffTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var activeWorkshopProvider: FakeActiveWorkshopProvider

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        activeWorkshopProvider = FakeActiveWorkshopProvider()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setStaffSession() {
        activeWorkshopProvider.setSession(
            WorkshopSession(
                authUid = "s",
                workshopUid = "o",
                role = StaffRole.STAFF,
                membershipStatus = MembershipStatus.ACTIVE,
            ),
        )
    }

    private fun TestScope.createViewModel(): OrderListViewModel {
        val vm = OrderListViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            activeWorkshopProvider = activeWorkshopProvider,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun activeStaffSession_setsIsActiveStaffTrue() = runTest {
        setStaffSession()
        val vm = createViewModel()

        assertTrue(vm.state.value.isActiveStaff)
    }

    @Test
    fun ownerSession_leavesIsActiveStaffFalse() = runTest {
        // Default FakeActiveWorkshopProvider session is owner-of-self.
        val vm = createViewModel()

        assertFalse(vm.state.value.isActiveStaff)
    }

    @Test
    fun onToggleShowProfit_forStaff_isNoOp() = runTest {
        setStaffSession()
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleShowProfit)

        assertFalse(vm.state.value.showProfit)
    }

    @Test
    fun onToggleShowProfit_forOwner_stillToggles() = runTest {
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleShowProfit)

        assertTrue(vm.state.value.showProfit)
    }

    @Test
    fun onDeleteOrderClick_forStaff_doesNotArmDeleteDialog() = runTest {
        setStaffSession()
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnDeleteOrderClick(sampleOrder()))

        assertFalse(vm.state.value.showDeleteDialog)
    }

    @Test
    fun onDeleteOrderClick_forOwner_armsDeleteDialog() = runTest {
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnDeleteOrderClick(sampleOrder()))

        assertTrue(vm.state.value.showDeleteDialog)
    }

    // --- Slice 8e: staff LIST access is now unblocked server-side, so a repo
    // error must surface exactly like it does for an owner — no more treating
    // a denial as an empty queue. ---

    @Test
    fun staffSession_surfacesListErrorsLikeOwner() = runTest {
        setStaffSession()
        orderRepository.shouldReturnError = DataError.Network.FORBIDDEN
        val vm = createViewModel()

        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun staffSession_surfacesArchivedErrorsLikeOwner() = runTest {
        setStaffSession()
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnShowArchived)

        orderRepository.archivedError = DataError.Network.FORBIDDEN
        orderRepository.ordersList = listOf(sampleOrder().copy(id = "trigger-emit"))

        assertNotNull(vm.state.value.errorMessage)
    }

    private fun sampleOrder(): Order = Order(
        id = "o1",
        userId = "o",
        customerId = "c1",
        customerName = "Ada",
        items = listOf(OrderItem(id = "i1", garmentType = GarmentType.AGBADA, description = "", price = 0.0)),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        deadline = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )
}

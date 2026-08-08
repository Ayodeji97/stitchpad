package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.test.runCurrent
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

    private fun TestScope.createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): OrderListViewModel {
        val vm = OrderListViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            activeWorkshopProvider = activeWorkshopProvider,
            savedStateHandle = savedStateHandle,
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

    // --- Task 1 (staff phase2 assignment): kill-switch / revocation must bite
    // mid-session — the listener must not stay pinned to the tree it started on. ---

    @Test
    fun workshopChangeMidSession_reSubscribesTheOrdersListener() = runTest {
        setStaffSession() // workshopUid = "o"
        orderRepository.setOrdersFor("o", listOf(sampleOrder().copy(id = "owned-by-o")))
        orderRepository.setOrdersFor("s", listOf(sampleOrder().copy(id = "owned-by-s")))
        val vm = createViewModel()
        assertEquals(listOf("owned-by-o"), vm.state.value.orders.map { it.id })

        // Kill switch drops the session to owner-of-self: workshopUid becomes the auth uid.
        activeWorkshopProvider.setSession(WorkshopSession.ownerOfSelf("s"))
        runCurrent()

        assertEquals(listOf("owned-by-s"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun workshopChangeMidSession_reSubscribesTheArchivedOrdersListener() = runTest {
        setStaffSession() // workshopUid = "o"
        orderRepository.setOrdersFor(
            "o",
            listOf(sampleOrder().copy(id = "archived-by-o", archivedAt = 1L)),
        )
        orderRepository.setOrdersFor(
            "s",
            listOf(sampleOrder().copy(id = "archived-by-s", archivedAt = 1L)),
        )
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnShowArchived)
        assertEquals(listOf("archived-by-o"), vm.state.value.orders.map { it.id })

        activeWorkshopProvider.setSession(WorkshopSession.ownerOfSelf("s"))
        runCurrent()

        assertEquals(listOf("archived-by-s"), vm.state.value.orders.map { it.id })
    }

    // --- Task 8: "My work" filter chip — staff filters the ACTIVE list to their own
    // assignments (assignedMemberId == the signed-in authUid). ---

    @Test
    fun onToggleMyWork_forStaff_filtersToOwnAssignments() = runTest {
        setStaffSession() // authUid = "s"
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "s"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "other"),
            sampleOrder().copy(id = "unassigned", assignedMemberId = null),
        )
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleMyWork)

        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })
        assertTrue(vm.state.value.myWorkOnly)
    }

    @Test
    fun onToggleMyWork_toggledOff_restoresFullList() = runTest {
        setStaffSession()
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "s"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "other"),
        )
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleMyWork)
        vm.onAction(OrderListAction.OnToggleMyWork)

        assertEquals(setOf("mine", "not-mine"), vm.state.value.orders.map { it.id }.toSet())
        assertFalse(vm.state.value.myWorkOnly)
    }

    @Test
    fun onToggleMyWork_composesWithStatusFilter() = runTest {
        setStaffSession()
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine-pending", assignedMemberId = "s", status = OrderStatus.PENDING),
            sampleOrder().copy(id = "mine-ready", assignedMemberId = "s", status = OrderStatus.READY),
            sampleOrder().copy(id = "other-pending", assignedMemberId = "other", status = OrderStatus.PENDING),
        )
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleMyWork)
        vm.onAction(OrderListAction.OnStatusFilterChange(OrderStatus.PENDING))

        assertEquals(listOf("mine-pending"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun myWorkOnly_isIgnoredAfterKillSwitchRevokesStaffStatus() = runTest {
        setStaffSession() // authUid = "s", workshopUid = "o"
        orderRepository.setOrdersFor("o", listOf(sampleOrder().copy(id = "mine", assignedMemberId = "s")))
        orderRepository.setOrdersFor(
            "s",
            listOf(
                sampleOrder().copy(id = "owned-by-s-1", assignedMemberId = "s"),
                sampleOrder().copy(id = "owned-by-s-2", assignedMemberId = null),
            ),
        )
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnToggleMyWork)
        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })

        // Kill switch: role flips to owner-of-self on the same authUid. myWorkOnly and
        // staffAuthUid are left stale in state (no chip left to clear them), but the
        // ex-staff user's own order tree must not stay silently filtered by them.
        activeWorkshopProvider.setSession(WorkshopSession.ownerOfSelf("s"))
        runCurrent()

        assertEquals(
            setOf("owned-by-s-1", "owned-by-s-2"),
            vm.state.value.orders.map { it.id }.toSet(),
        )
    }

    @Test
    fun onToggleMyWork_forOwner_isNoOp() = runTest {
        // Default FakeActiveWorkshopProvider session is owner-of-self.
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "o1", assignedMemberId = "someone"),
        )
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleMyWork)

        assertFalse(vm.state.value.myWorkOnly)
        assertEquals(listOf("o1"), vm.state.value.orders.map { it.id })
    }

    // --- Task 9 (staff-phase2-assignment): seed statusFilter/myWorkOnly from
    // OrderListRoute.initialFilter (a dashboard tile deep-linking into a filtered view). ---

    @Test
    fun seededWithMyWorkFilter_startsWithMyWorkOnlyTrue() = runTest {
        setStaffSession()
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.MY_WORK)),
        )

        assertTrue(vm.state.value.myWorkOnly)
    }

    @Test
    fun seededWithInProgressFilter_startsWithInProgressStatusFilter() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.IN_PROGRESS)),
        )

        assertEquals(OrderStatus.IN_PROGRESS, vm.state.value.statusFilter)
    }

    @Test
    fun seededWithNoFilter_leavesDefaultsUnchanged() = runTest {
        val vm = createViewModel()

        assertFalse(vm.state.value.myWorkOnly)
        assertNull(vm.state.value.statusFilter)
    }

    @Test
    fun seededWithUnrecognizedFilter_leavesDefaultsUnchanged() = runTest {
        // "overdue"/"due-today" have no matching filter yet — must not crash or
        // half-apply; the list stays unfiltered exactly like a null initialFilter.
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.OVERDUE)),
        )

        assertFalse(vm.state.value.myWorkOnly)
        assertNull(vm.state.value.statusFilter)
    }

    @Test
    fun seededMyWorkFilter_actuallyFiltersTheOrdersOnceStaffSessionLands() = runTest {
        // The seeded flag must survive observeActiveWorkshop's isActiveStaff/staffAuthUid
        // update (a separate _state.update call) and still narrow the active list once
        // the staff session resolves — not just sit inert in state.
        setStaffSession() // authUid = "s"
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "s"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "other"),
        )

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.MY_WORK)),
        )

        assertTrue(vm.state.value.myWorkOnly)
        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun seededMyWorkFilter_forOwnerSession_doesNotFilter() = runTest {
        // Guard precedent from Task 8: myWorkOnly only bites with isActiveStaff +
        // a resolved staffAuthUid. A seeded flag on an owner session must not
        // silently narrow the owner's own order list.
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "o1", assignedMemberId = "someone-else"),
        )

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.MY_WORK)),
        )

        assertTrue(vm.state.value.myWorkOnly)
        assertEquals(listOf("o1"), vm.state.value.orders.map { it.id })
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

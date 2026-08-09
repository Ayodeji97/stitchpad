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

    // --- Slice 8e Bugbot follow-up: the kill-switch tests above only prove the
    // listener re-subscribes — both trees' data were ready the instant flatMapLatest
    // switched, so `allOrders`/state never had a chance to show A's stale rows. Force
    // that window open with a fake flow that genuinely hasn't emitted yet, and keep
    // the STAFF role constant across the switch (no role-change nav redirect to mask
    // the gap) — this is a pure multi-workshop switch. ---

    @Test
    fun workshopSwitchMidSession_resetsOrdersBeforeNewTreeDataLands() = runTest {
        setStaffSession() // authUid = "s", workshopUid = "o"
        orderRepository.setOrdersFor("o", listOf(sampleOrder().copy(id = "owned-by-o")))
        val vm = createViewModel()
        assertEquals(listOf("owned-by-o"), vm.state.value.orders.map { it.id })

        // "o2"'s listener genuinely hasn't produced a first snapshot yet.
        orderRepository.setOrdersPendingFor("o2")
        activeWorkshopProvider.setSession(
            WorkshopSession(
                authUid = "s",
                workshopUid = "o2",
                role = StaffRole.STAFF,
                membershipStatus = MembershipStatus.ACTIVE,
            ),
        )
        runCurrent()

        // Must not still show "o"'s stale rows while "o2" hasn't emitted.
        assertEquals(emptyList(), vm.state.value.orders)

        orderRepository.emitOrdersFor("o2", listOf(sampleOrder().copy(id = "owned-by-o2")))
        runCurrent()

        assertEquals(listOf("owned-by-o2"), vm.state.value.orders.map { it.id })
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
    fun tappingAll_clearsMyWorkFilterToo() = runTest {
        // "All" must mean everything: with My work on and no status filter, All was
        // a dead tap (status already null) — it now resets the orthogonal filters.
        setStaffSession()
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "s"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "other"),
        )
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnToggleMyWork)
        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })

        vm.onAction(OrderListAction.OnStatusFilterChange(null))

        assertFalse(vm.state.value.myWorkOnly)
        assertEquals(setOf("mine", "not-mine"), vm.state.value.orders.map { it.id }.toSet())
    }

    @Test
    fun deselectingStatusChip_keepsMyWorkActive() = runTest {
        // Backing out of one status chip is not a request for "everything" — the
        // my-work slice must survive a status-chip deselect.
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

        vm.onAction(OrderListAction.OnStatusFilterChange(OrderStatus.PENDING))

        assertTrue(vm.state.value.myWorkOnly)
        assertNull(vm.state.value.statusFilter)
        assertEquals(setOf("mine-pending", "mine-ready"), vm.state.value.orders.map { it.id }.toSet())
    }

    @Test
    fun myWorkOnly_staysActiveAcrossKillSwitchAsOwnerFilter() = runTest {
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

        // Kill switch: role flips to owner-of-self on the same authUid. myWorkOnly is left
        // on in state (the chip is now visible to owners too, so this is a clearable filter,
        // not a stranded one) and now filters the user's OWN tree by their own assignments —
        // sessionAuthUid is recomputed to the same "s" regardless of role.
        activeWorkshopProvider.setSession(WorkshopSession.ownerOfSelf("s"))
        runCurrent()

        assertEquals(listOf("owned-by-s-1"), vm.state.value.orders.map { it.id })
        assertTrue(vm.state.value.myWorkOnly)

        // The chip is visible to owners now, so the filter is clearable, not stranded.
        vm.onAction(OrderListAction.OnToggleMyWork)

        assertEquals(
            setOf("owned-by-s-1", "owned-by-s-2"),
            vm.state.value.orders.map { it.id }.toSet(),
        )
        assertFalse(vm.state.value.myWorkOnly)
    }

    @Test
    fun onToggleMyWork_fromArchivedView_withStaleMyWork_selectsInsteadOfClearing() = runTest {
        setStaffSession()
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "s"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "other"),
        )
        val vm = createViewModel()
        vm.onAction(OrderListAction.OnToggleMyWork)
        vm.onAction(OrderListAction.OnShowArchived)

        // The My-work chip renders unselected in the archived view even though
        // myWorkOnly is still true underneath — so this tap means "show my work",
        // not "clear the filter".
        vm.onAction(OrderListAction.OnToggleMyWork)

        assertFalse(vm.state.value.showArchived)
        assertTrue(vm.state.value.myWorkOnly)
        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun onToggleMyWork_forOwner_filtersToOwnAssignments() = runTest {
        // Default FakeActiveWorkshopProvider session is owner-of-self on "test-uid" — the
        // workshop owner became assignable in earlier tasks on this branch, so My-work
        // now works for owners exactly like it does for staff.
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "test-uid"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "someone-else"),
        )
        val vm = createViewModel()

        vm.onAction(OrderListAction.OnToggleMyWork)

        assertTrue(vm.state.value.myWorkOnly)
        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })
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
        // The seeded flag must survive observeActiveWorkshop's isActiveStaff/sessionAuthUid
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
    fun seededMyWorkFilter_isVisibleFromStateValueBeforeAnySubscriber() = runTest {
        // Regression: `state`'s stateIn(initialValue = ...) must mirror the seeded
        // `_state` construction, not a bare `OrderListState()`. With
        // SharingStarted.WhileSubscribed, `state.value` read before the first
        // collector attaches returns stateIn's own initialValue, never `_state`'s —
        // so this must be asserted WITHOUT going through `createViewModel()`'s
        // `backgroundScope.launch { vm.state.collect {} }` subscription.
        val vm = OrderListViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            activeWorkshopProvider = activeWorkshopProvider,
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.MY_WORK)),
        )

        assertTrue(vm.state.value.myWorkOnly)
    }

    @Test
    fun seededMyWorkFilter_forOwnerSession_filtersToOwnAssignments() = runTest {
        // Task 7: myWorkOnly now bites off sessionAuthUid alone (no isActiveStaff
        // conjunct) — a seeded flag on an owner session narrows the owner's own
        // order list to their own assignments, same as it does for staff.
        orderRepository.ordersList = listOf(
            sampleOrder().copy(id = "mine", assignedMemberId = "test-uid"),
            sampleOrder().copy(id = "not-mine", assignedMemberId = "someone-else"),
        )

        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialFilter" to OrderListFilter.MY_WORK)),
        )

        assertTrue(vm.state.value.myWorkOnly)
        assertEquals(listOf("mine"), vm.state.value.orders.map { it.id })
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

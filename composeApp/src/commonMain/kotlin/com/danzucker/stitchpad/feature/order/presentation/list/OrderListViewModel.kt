package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.ownedStoragePaths
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val activeWorkshopProvider: ActiveWorkshopProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var allOrders: List<Order> = emptyList()
    private var allArchivedOrders: List<Order> = emptyList()

    // Tracks the workshopUid each listener last subscribed under, so a genuine
    // A -> B switch (not the first-ever subscribe, and not a transition to/from
    // null — that's already handled by the `workshopUid == null` branch below)
    // can be told apart from a cold start. See the reset note in observeOrders.
    private var lastOrdersWorkshopUid: String? = null
    private var lastArchivedOrdersWorkshopUid: String? = null

    // Task 9 (staff-phase2-assignment): seeds the list's filter from
    // OrderListRoute.initialFilter — e.g. a dashboard tile deep-linking straight
    // into "in progress" or "my work" instead of landing on the unfiltered list.
    // Only these two are consumed today; "overdue"/"due-today" have no matching
    // filter in OrderListState yet (see OrderListFilter's kdoc) so they fall
    // through to the `else` and leave the list unfiltered, same as null.
    private val initialFilter: String? = savedStateHandle["initialFilter"]

    // Task 8: an `assignee:<memberId>` (or `assignee:none`) deep link — Task 9 navigates
    // here with one of these from a Team screen workload row. removePrefix() is a no-op
    // (returns the input unchanged) when the prefix isn't present, so the takeIf guard is
    // what actually gates this to null for every other initialFilter value.
    private val seededAssigneeFilter: String? = initialFilter
        ?.removePrefix(OrderListFilter.ASSIGNEE_PREFIX)
        ?.takeIf { initialFilter.startsWith(OrderListFilter.ASSIGNEE_PREFIX) }

    private val _state = MutableStateFlow(
        OrderListState(
            statusFilter = if (initialFilter == OrderListFilter.IN_PROGRESS) OrderStatus.IN_PROGRESS else null,
            myWorkOnly = initialFilter == OrderListFilter.MY_WORK,
            assigneeFilter = seededAssigneeFilter,
            // allOrders is still empty at construction time, so this resolves via the
            // pure helper's own fallback (the raw id) until the first snapshot lands.
            assigneeFilterName = seededAssigneeFilter?.let { assigneeFilterLabelName(allOrders, it) },
        )
    )

    private val _events = Channel<OrderListEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeActiveWorkshop()
                observeOrders()
                observeArchivedOrders()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            // Must mirror the seeded `_state` above (not a bare `OrderListState()`) — with
            // WhileSubscribed, a `state.value` read before the first collector attaches
            // returns THIS initial value, not `_state`'s. Reading `_state.value` here
            // (rather than duplicating the seed expression) makes the two impossible to
            // drift apart, unlike a second independently-maintained literal.
            initialValue = _state.value
        )

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun onAction(action: OrderListAction) {
        when (action) {
            is OrderListAction.OnStatusFilterChange -> changeStatusFilter(action.status)
            OrderListAction.OnShowArchived -> toggleArchivedView()
            is OrderListAction.OnRestoreOrderClick -> restoreOrder(action.order)
            is OrderListAction.OnOrderClick -> {
                viewModelScope.launch {
                    _events.send(OrderListEvent.NavigateToOrderDetail(action.order.id))
                }
            }
            OrderListAction.OnAddOrderClick -> {
                // Slice 6c: staff can't create orders. Defense-in-depth — the FAB
                // is hidden, and the empty-state add button no-ops here.
                if (_state.value.isActiveStaff) return
                viewModelScope.launch {
                    _events.send(
                        if (userHasActiveCustomer()) {
                            OrderListEvent.NavigateToOrderForm
                        } else {
                            OrderListEvent.NavigateToAddCustomerFirst
                        }
                    )
                }
            }
            is OrderListAction.OnDeleteOrderClick -> {
                // Slice 6c: staff can't delete orders. Defense-in-depth — swipe-to-delete
                // is disabled in the UI, and this arms the confirm dialog.
                if (_state.value.isActiveStaff) return
                _state.update { it.copy(showDeleteDialog = true, orderToDelete = action.order) }
            }
            OrderListAction.OnConfirmDelete -> {
                if (_state.value.isActiveStaff) return
                deleteOrder()
            }
            OrderListAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
            }
            OrderListAction.OnToggleHideAmounts -> {
                // Slice 6c: money is never exposed to active staff at all — the toggle
                // control is hidden for them, but guard here too so the action is
                // inert even if dispatched.
                if (_state.value.isActiveStaff) return
                _state.update { it.copy(hideAmounts = !it.hideAmounts) }
            }
            OrderListAction.OnToggleMyWork -> toggleMyWork()
            OrderListAction.OnClearAssigneeFilter -> clearAssigneeFilter()
            OrderListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun changeStatusFilter(status: OrderStatus?) {
        _state.update { state ->
            // Re-tapping the chip that is already selected (active view only — in the
            // archived view no status chip renders selected) deselects it back to All.
            val newStatus = status
                .takeUnless { !state.showArchived && it == state.statusFilter }
            // Tapping "All" (status == null) is a full reset: it also clears the
            // orthogonal my-work and assignee filters — "All" must mean everything,
            // not "no status, but still someone's slice" (owner smoke, 2026-08-09:
            // with My work on, tapping All looked dead because status was already
            // null). A status-chip DESELECT (newStatus == null via takeUnless above)
            // deliberately keeps them: backing out of Pending while in My work
            // should stay in My work.
            val isAllTap = status == null
            val myWorkOnly = if (isAllTap) false else state.myWorkOnly
            val assigneeFilter = if (isAllTap) null else state.assigneeFilter
            state.copy(
                statusFilter = newStatus,
                myWorkOnly = myWorkOnly,
                assigneeFilter = assigneeFilter,
                assigneeFilterName = if (isAllTap) null else state.assigneeFilterName,
                showArchived = false,
                orders = filterAndSort(
                    allOrders,
                    newStatus,
                    myWorkOnly,
                    state.sessionAuthUid,
                    assigneeFilter,
                )
            )
        }
    }

    private fun toggleArchivedView() {
        _state.update { state ->
            // Second tap on a selected Archived chip deselects it back to the
            // active view, keeping whatever status/my-work filters were set.
            if (state.showArchived) {
                state.copy(
                    showArchived = false,
                    orders = filterAndSort(
                        allOrders,
                        state.statusFilter,
                        state.myWorkOnly,
                        state.sessionAuthUid,
                        state.assigneeFilter,
                    )
                )
            } else {
                state.copy(showArchived = true, orders = allArchivedOrders)
            }
        }
    }

    // Task 7: My-work now works for owners too (they became assignable in earlier
    // tasks), so the isActiveStaff guard drops out — the archived-view select
    // semantics below are unchanged.
    private fun toggleMyWork() {
        _state.update { state ->
            // In the archived view the My-work chip always renders unselected, so a tap
            // there means "select" even when a stale myWorkOnly=true is carried over —
            // plain toggling would drop the user into the unfiltered active list.
            val myWorkOnly = if (state.showArchived) true else !state.myWorkOnly
            state.copy(
                myWorkOnly = myWorkOnly,
                // My work applies to the active list only — selecting it always brings the
                // user back to the active view, mirroring the status filter chips' own
                // showArchived reset.
                showArchived = false,
                orders = filterAndSort(
                    allOrders,
                    state.statusFilter,
                    myWorkOnly,
                    state.sessionAuthUid,
                    state.assigneeFilter,
                )
            )
        }
    }

    // Task 8: re-tapping the assignee chip is the only way to clear it — it has no
    // toggle semantics of its own (unlike status/Archived/My-work, it isn't reselectable
    // to a different value from the list screen; Task 9's Team screen is what sets it).
    private fun clearAssigneeFilter() {
        _state.update { state ->
            state.copy(
                assigneeFilter = null,
                assigneeFilterName = null,
                // Bugbot follow-up: the chip still renders (and is tappable) while
                // showArchived is true, since assigneeFilter != null doesn't check that —
                // recomputing via filterAndSort unconditionally would overwrite the
                // Archived view's own rows with the active list. Mirror the sibling
                // functions' handling (toggleArchivedView, observeOrders) and leave
                // `orders` alone when archived is showing; clearing the filter fields is
                // still correct so the active view is unfiltered once the user leaves Archived.
                orders = if (state.showArchived) {
                    state.orders
                } else {
                    filterAndSort(
                        allOrders,
                        state.statusFilter,
                        state.myWorkOnly,
                        state.sessionAuthUid,
                        assigneeFilter = null,
                    )
                }
            )
        }
    }

    private fun observeActiveWorkshop() {
        viewModelScope.launch {
            activeWorkshopProvider.flow.collect { session ->
                _state.update { state ->
                    state.copy(
                        isActiveStaff = session.isActiveStaff,
                        // Task 8; Task 7 dropped the staff-only gate — captured alongside
                        // isActiveStaff from the same session collection, for BOTH an owner
                        // and a staff session, and used by the "My work" filter to match
                        // assignedMemberId.
                        sessionAuthUid = session.authUid.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }

    // Kill-switch / staff-revocation must bite mid-session, not on next restart
    // (StitchPad exploration, 2026-08-08): the old `workshopUidOrNull()` one-shot
    // read pinned this listener to whatever tree resolved at subscribe time.
    // Re-subscribing on every `workshopUid` change (flatMapLatest cancels the
    // previous Firestore listener before starting the new one) keeps the list
    // correct for the LIVE session, not the one it started in.
    private fun observeOrders() {
        viewModelScope.launch {
            activeWorkshopProvider.flow
                .map { it.workshopUid.takeIf { uid -> uid.isNotBlank() } }
                .distinctUntilChanged()
                .flatMapLatest { workshopUid ->
                    // A -> B (both non-null, different uids): flatMapLatest cancels A's
                    // listener and starts B's, but B's first snapshot can take a beat to
                    // arrive — without this, `allOrders`/state keep showing A's rows in
                    // the meantime, which is only masked today by the role-change nav
                    // redirect. Emit the same null sentinel the sign-out branch already
                    // uses so the reset logic isn't duplicated. Skipped on cold start
                    // (lastOrdersWorkshopUid == null) so first load doesn't flash empty.
                    val isWorkshopSwitch = lastOrdersWorkshopUid != null &&
                        workshopUid != null &&
                        workshopUid != lastOrdersWorkshopUid
                    lastOrdersWorkshopUid = workshopUid
                    val liveFlow: Flow<Result<List<Order>, DataError.Network>?> =
                        if (workshopUid == null) flowOf(null) else orderRepository.observeOrders(workshopUid)
                    if (isWorkshopSwitch) liveFlow.onStart { emit(null) } else liveFlow
                }
                .collect { result ->
                    if (result == null) {
                        allOrders = emptyList()
                        _state.update { state ->
                            state.copy(
                                isLoading = false,
                                orders = if (state.showArchived) state.orders else emptyList(),
                                // Task 8: allOrders just reset — recompute the label so a
                                // still-active assignee filter falls back to the raw id
                                // instead of showing a name from the tree that just left.
                                assigneeFilterName = state.assigneeFilter
                                    ?.let { assigneeFilterLabelName(allOrders, it) },
                            )
                        }
                        return@collect
                    }
                    when (result) {
                        is Result.Success -> {
                            allOrders = result.data
                            _state.update { state ->
                                state.copy(
                                    // Active updates never override the archived view.
                                    orders = if (state.showArchived) {
                                        state.orders
                                    } else {
                                        filterAndSort(
                                            result.data,
                                            state.statusFilter,
                                            state.myWorkOnly,
                                            state.sessionAuthUid,
                                            state.assigneeFilter,
                                        )
                                    },
                                    isLoading = false,
                                    // Task 8: re-resolve the assignee chip's display name off
                                    // every fresh snapshot while the filter is active — the
                                    // matching order (and its assignedMemberName) may not have
                                    // been in the tree yet on an earlier snapshot.
                                    assigneeFilterName = state.assigneeFilter
                                        ?.let { assigneeFilterLabelName(result.data, it) },
                                )
                            }
                        }
                        is Result.Error -> {
                            _state.update { state ->
                                state.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                            }
                        }
                    }
                }
        }
    }

    private fun observeArchivedOrders() {
        viewModelScope.launch {
            activeWorkshopProvider.flow
                .map { it.workshopUid.takeIf { uid -> uid.isNotBlank() } }
                .distinctUntilChanged()
                .flatMapLatest { workshopUid ->
                    // Same workshop-switch reset as observeOrders above, mirrored for the
                    // archived stream's own backing cache.
                    val isWorkshopSwitch = lastArchivedOrdersWorkshopUid != null &&
                        workshopUid != null &&
                        workshopUid != lastArchivedOrdersWorkshopUid
                    lastArchivedOrdersWorkshopUid = workshopUid
                    val liveFlow: Flow<Result<List<Order>, DataError.Network>?> =
                        if (workshopUid == null) flowOf(null) else orderRepository.observeArchivedOrders(workshopUid)
                    if (isWorkshopSwitch) liveFlow.onStart { emit(null) } else liveFlow
                }
                .collect { result ->
                    if (result == null) {
                        allArchivedOrders = emptyList()
                        _state.update { state ->
                            state.copy(
                                isArchivedLoading = false,
                                orders = if (state.showArchived) emptyList() else state.orders,
                            )
                        }
                        return@collect
                    }
                    when (result) {
                        is Result.Success -> {
                            allArchivedOrders = result.data
                            _state.update { state ->
                                state.copy(
                                    isArchivedLoading = false,
                                    orders = if (state.showArchived) result.data else state.orders
                                )
                            }
                        }
                        is Result.Error -> {
                            // Clear loading so the view stops spinning. Surface the error
                            // only while the archived view is open — on the active view the
                            // active stream (same Firestore source) owns the error surface.
                            _state.update { state ->
                                state.copy(
                                    isArchivedLoading = false,
                                    errorMessage = if (state.showArchived) {
                                        result.error.toOrderUiText()
                                    } else {
                                        state.errorMessage
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun restoreOrder(order: Order) {
        viewModelScope.launch {
            val userId = activeWorkshopProvider.workshopUidOrNull() ?: return@launch
            when (val result = orderRepository.unarchiveOrder(userId, order.id)) {
                is Result.Success -> _events.send(OrderListEvent.OrderRestored)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }

    /**
     * Resolve whether the user has a *usable* customer before deciding where the
     * FAB goes. We match the order form's own picker criteria — only ACTIVE
     * customers are selectable (LOCKED freemium customers are read-only) — so a
     * user whose customers are all locked is gated rather than dropped on an
     * empty picker dead-end. We await the first snapshot rather than reading a
     * cached flag so a customer-owning user is never misrouted during initial
     * load, and fail open to the form on error (it surfaces whatever's cached).
     */
    private suspend fun userHasActiveCustomer(): Boolean {
        val userId = activeWorkshopProvider.workshopUidOrNull() ?: return false
        return when (val result = customerRepository.observeCustomers(userId).first()) {
            is Result.Success -> result.data.any { it.slotState == CustomerSlotState.ACTIVE }
            is Result.Error -> true
        }
    }

    private fun deleteOrder() {
        val order = _state.value.orderToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
        viewModelScope.launch {
            val userId = activeWorkshopProvider.workshopUidOrNull() ?: return@launch
            val result = orderRepository.deleteOrder(
                userId = userId,
                orderId = order.id,
                ownedStoragePaths = order.ownedStoragePaths(),
            )
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toOrderUiText()) }
            }
        }
    }

    private fun filterAndSort(
        orders: List<Order>,
        statusFilter: OrderStatus?,
        myWorkOnly: Boolean = false,
        sessionAuthUid: String? = null,
        assigneeFilter: String? = null,
    ): List<Order> {
        val statusFiltered = when (statusFilter) {
            null -> orders.filter { it.status != OrderStatus.DELIVERED }
            else -> orders.filter { it.status == statusFilter }
        }
        // Task 8; Task 7 dropped the isActiveStaff conjunct — "My work" narrows the active
        // list to the signed-in user's own assignments, owner or staff alike.
        val filtered = if (myWorkOnly && sessionAuthUid != null) {
            statusFiltered.filter { it.assignedMemberId == sessionAuthUid }
        } else {
            statusFiltered
        }
        // Task 8: the `assignee:` deep link's own filter, composed after the my-work step —
        // narrows to one member's assignments (or unassigned orders), independent of who's
        // signed in. Task 9's Team workload rows are what seed this.
        val assigneeFiltered = when (assigneeFilter) {
            null -> filtered
            OrderListFilter.ASSIGNEE_NONE_ID -> filtered.filter { it.assignedMemberId == null }
            else -> filtered.filter { it.assignedMemberId == assigneeFilter }
        }
        // Reuse the triage comparator so same-deadline ties resolve identically in both the
        // triage-grouped and the chip-filtered views (createdAt desc = newest-first).
        return assigneeFiltered.sortedWith(orderListComparator)
    }
}

package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // Task 9 (staff-phase2-assignment): seeds the list's filter from
    // OrderListRoute.initialFilter — e.g. a dashboard tile deep-linking straight
    // into "in progress" or "my work" instead of landing on the unfiltered list.
    // Only these two are consumed today; "overdue"/"due-today" have no matching
    // filter in OrderListState yet (see OrderListFilter's kdoc) so they fall
    // through to the `else` and leave the list unfiltered, same as null.
    private val initialFilter: String? = savedStateHandle["initialFilter"]

    private val _state = MutableStateFlow(
        OrderListState(
            statusFilter = if (initialFilter == OrderListFilter.IN_PROGRESS) OrderStatus.IN_PROGRESS else null,
            myWorkOnly = initialFilter == OrderListFilter.MY_WORK,
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
            is OrderListAction.OnStatusFilterChange -> {
                _state.update { state ->
                    state.copy(
                        statusFilter = action.status,
                        showArchived = false,
                        orders = filterAndSort(
                            allOrders,
                            action.status,
                            state.myWorkOnly,
                            state.staffAuthUid,
                            state.isActiveStaff,
                        )
                    )
                }
            }
            OrderListAction.OnShowArchived -> {
                _state.update { it.copy(showArchived = true, orders = allArchivedOrders) }
            }
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
            OrderListAction.OnToggleShowProfit -> {
                // Slice 6c: profit is money — never expose it to active staff. The
                // toggle control is hidden for them, but guard here too so the action
                // is inert even if dispatched.
                if (_state.value.isActiveStaff) return
                _state.update { it.copy(showProfit = !it.showProfit) }
            }
            OrderListAction.OnToggleMyWork -> toggleMyWork()
            OrderListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    // Staff-only filter — never rendered for an owner, but guarded here too so the action
    // is inert even if dispatched (Slice 6c defense-in-depth precedent).
    private fun toggleMyWork() {
        if (!_state.value.isActiveStaff) return
        _state.update { state ->
            val myWorkOnly = !state.myWorkOnly
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
                    state.staffAuthUid,
                    state.isActiveStaff,
                )
            )
        }
    }

    private fun observeActiveWorkshop() {
        viewModelScope.launch {
            activeWorkshopProvider.flow.collect { session ->
                _state.update {
                    it.copy(
                        isActiveStaff = session.isActiveStaff,
                        // Task 8: captured alongside isActiveStaff from the same session
                        // collection — used by the "My work" filter to match assignedMemberId.
                        staffAuthUid = session.authUid,
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
                    if (workshopUid == null) flowOf(null) else orderRepository.observeOrders(workshopUid)
                }
                .collect { result ->
                    if (result == null) {
                        allOrders = emptyList()
                        _state.update { state ->
                            state.copy(
                                isLoading = false,
                                orders = if (state.showArchived) state.orders else emptyList(),
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
                                            state.staffAuthUid,
                                            state.isActiveStaff,
                                        )
                                    },
                                    isLoading = false
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
                    if (workshopUid == null) flowOf(null) else orderRepository.observeArchivedOrders(workshopUid)
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
        staffAuthUid: String? = null,
        isActiveStaff: Boolean = false,
    ): List<Order> {
        val statusFiltered = when (statusFilter) {
            null -> orders.filter { it.status != OrderStatus.DELIVERED }
            else -> orders.filter { it.status == statusFilter }
        }
        // Task 8: "My work" narrows the active list to the signed-in staff member's own
        // assignments. Requires isActiveStaff too (not just a non-null staffAuthUid) —
        // a kill-switch revocation mid-session (staff -> owner-of-self) leaves myWorkOnly
        // and staffAuthUid stale in state with no chip left to show or clear them; without
        // this guard the ex-staff user's own order tree would silently stay filtered.
        val filtered = if (myWorkOnly && isActiveStaff && staffAuthUid != null) {
            statusFiltered.filter { it.assignedMemberId == staffAuthUid }
        } else {
            statusFiltered
        }
        // Reuse the triage comparator so same-deadline ties resolve identically in both the
        // triage-grouped and the chip-filtered views (createdAt desc = newest-first).
        return filtered.sortedWith(orderListComparator)
    }
}

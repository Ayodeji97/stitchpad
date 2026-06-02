package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.ownedStoragePaths
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrderListViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var allOrders: List<Order> = emptyList()

    // Tracks whether the user has any customer at all. The Orders-tab FAB gates
    // on this so a customer-less user is routed to "add a customer first" rather
    // than dropped on an order form with an empty, unusable customer picker.
    private var hasCustomers = false

    private val _state = MutableStateFlow(OrderListState())

    private val _events = Channel<OrderListEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeOrders()
                observeCustomers()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OrderListState()
        )

    fun onAction(action: OrderListAction) {
        when (action) {
            is OrderListAction.OnStatusFilterChange -> {
                _state.update {
                    it.copy(
                        statusFilter = action.status,
                        orders = filterAndSort(allOrders, action.status)
                    )
                }
            }
            is OrderListAction.OnOrderClick -> {
                viewModelScope.launch {
                    _events.send(OrderListEvent.NavigateToOrderDetail(action.order.id))
                }
            }
            OrderListAction.OnAddOrderClick -> {
                viewModelScope.launch {
                    _events.send(
                        if (hasCustomers) {
                            OrderListEvent.NavigateToOrderForm
                        } else {
                            OrderListEvent.NavigateToAddCustomerFirst
                        }
                    )
                }
            }
            is OrderListAction.OnDeleteOrderClick -> {
                _state.update { it.copy(showDeleteDialog = true, orderToDelete = action.order) }
            }
            OrderListAction.OnConfirmDelete -> deleteOrder()
            OrderListAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
            }
            OrderListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun observeOrders() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            orderRepository.observeOrders(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        allOrders = result.data
                        _state.update { state ->
                            state.copy(
                                orders = filterAndSort(result.data, state.statusFilter),
                                isLoading = false
                            )
                        }
                    }
                    is Result.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.error.toOrderUiText()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeCustomers() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customerRepository.observeCustomers(userId).collect { result ->
                if (result is Result.Success) {
                    hasCustomers = result.data.isNotEmpty()
                }
            }
        }
    }

    private fun deleteOrder() {
        val order = _state.value.orderToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
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

    private fun filterAndSort(orders: List<Order>, statusFilter: OrderStatus?): List<Order> {
        val filtered = when (statusFilter) {
            null -> orders.filter { it.status != OrderStatus.DELIVERED }
            else -> orders.filter { it.status == statusFilter }
        }
        // Reuse the triage comparator so same-deadline ties resolve identically in both the
        // triage-grouped and the chip-filtered views (createdAt desc = newest-first).
        return filtered.sortedWith(orderListComparator)
    }
}

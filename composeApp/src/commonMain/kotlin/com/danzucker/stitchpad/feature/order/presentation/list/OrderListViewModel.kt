package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlin.time.Clock
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var allOrders: List<Order> = emptyList()

    private val _state = MutableStateFlow(OrderListState())

    private val _events = Channel<OrderListEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeOrders()
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
                        showOverdueOnly = false,
                        orders = filterAndSortOrders(allOrders, action.status, showOverdue = false)
                    )
                }
            }
            is OrderListAction.OnToggleOverdueFilter -> {
                _state.update {
                    it.copy(
                        showOverdueOnly = action.showOverdue,
                        statusFilter = if (action.showOverdue) null else it.statusFilter,
                        orders = filterAndSortOrders(
                            allOrders,
                            statusFilter = if (action.showOverdue) null else it.statusFilter,
                            showOverdue = action.showOverdue
                        )
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
                    _events.send(OrderListEvent.NavigateToOrderForm)
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
                                orders = filterAndSortOrders(
                                    result.data,
                                    state.statusFilter,
                                    state.showOverdueOnly
                                ),
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

    private fun deleteOrder() {
        val order = _state.value.orderToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, orderToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = orderRepository.deleteOrder(userId, order.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toOrderUiText()) }
            }
        }
    }

    private fun filterAndSortOrders(
        orders: List<Order>,
        statusFilter: OrderStatus?,
        showOverdue: Boolean
    ): List<Order> {
        val now = Clock.System.now().toEpochMilliseconds()
        var result = orders

        if (showOverdue) {
            result = result.filter { it.isOverdue(now) }
        } else if (statusFilter != null) {
            result = result.filter { it.status == statusFilter }
        }

        return result.sortedWith(
            compareByDescending<Order> { it.isOverdue(now) }
                .thenBy { it.deadline ?: Long.MAX_VALUE }
                .thenByDescending { it.priority.ordinal }
                .thenByDescending { it.createdAt }
        )
    }

    private fun Order.isOverdue(now: Long): Boolean =
        deadline != null && deadline < now && status != OrderStatus.DELIVERED
}

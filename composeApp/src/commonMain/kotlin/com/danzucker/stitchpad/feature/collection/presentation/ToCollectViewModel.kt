package com.danzucker.stitchpad.feature.collection.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.collection.domain.CollectibleOrder
import com.danzucker.stitchpad.feature.collection.domain.CollectionCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_unknown
import kotlin.time.Clock

class ToCollectViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private var hasLoaded = false
    private var allCollectibles: List<CollectibleOrder> = emptyList()
    private var ordersById: Map<String, Order> = emptyMap()
    private var customersById: Map<String, Customer> = emptyMap()

    private val _state = MutableStateFlow(ToCollectState())
    val state = _state
        .onStart {
            if (!hasLoaded) {
                hasLoaded = true
                observe()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ToCollectState())

    private val _events = Channel<ToCollectEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ToCollectAction) {
        when (action) {
            ToCollectAction.OnBackClick ->
                viewModelScope.launch { _events.send(ToCollectEvent.NavigateBack) }
            is ToCollectAction.OnSortSelected -> {
                _state.update { it.copy(sort = action.sort) }
                recompute()
            }
            is ToCollectAction.OnFilterSelected -> {
                _state.update { it.copy(filter = action.filter) }
                recompute()
            }
            is ToCollectAction.OnRowClick ->
                viewModelScope.launch { _events.send(ToCollectEvent.NavigateToOrderDetail(action.orderId)) }
            is ToCollectAction.OnChaseClick -> onChaseClick(action.orderId)
        }
    }

    private fun observe() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            combine(
                orderRepository.observeOrders(uid),
                customerRepository.observeCustomers(uid),
            ) { ordersResult, customersResult -> ordersResult to customersResult }
                .collect { (ordersResult, customersResult) ->
                    val orders = (ordersResult as? Result.Success)?.data
                    val customers = (customersResult as? Result.Success)?.data
                    if (orders == null || customers == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = UiText.StringResourceText(Res.string.error_unknown),
                            )
                        }
                        return@collect
                    }
                    ordersById = orders.associateBy { it.id }
                    customersById = customers.associateBy { it.id }
                    allCollectibles = CollectionCalculator.collectibles(orders, customersById, nowMillis())
                    _state.update { it.copy(isLoading = false, errorMessage = null) }
                    recompute()
                }
        }
    }

    private fun recompute() {
        val current = _state.value
        val filtered = CollectionCalculator.filtered(allCollectibles, current.filter)
        val sorted = CollectionCalculator.sorted(filtered, current.sort)
        val options = allCollectibles
            .distinctBy { it.customerId }
            .map { CustomerFilterOption(it.customerId, it.customerName) }
            .sortedBy { it.name.lowercase() }
        _state.update {
            it.copy(
                items = sorted,
                summary = CollectionCalculator.summarize(allCollectibles),
                customerOptions = options,
            )
        }
    }

    private fun onChaseClick(orderId: String) {
        val order = ordersById[orderId] ?: return
        val customer = customersById[order.customerId] ?: return
        viewModelScope.launch { _events.send(ToCollectEvent.LaunchWhatsApp(order, customer)) }
    }
}

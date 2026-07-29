package com.danzucker.stitchpad.feature.collection.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
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
    private val userRepository: UserRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private var hasLoaded = false
    private var allCollectibles: List<CollectibleOrder> = emptyList()
    private var ordersById: Map<String, Order> = emptyMap()
    private var customersById: Map<String, Customer> = emptyMap()
    private var signature: String = ""

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
            val user = authRepository.getCurrentUser() ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val uid = user.id
            // Include the Firestore user doc in the combine so the chase signature
            // reflects the workshop's business name — the Auth user's businessName
            // is hardcoded null in production (real value only lives in Firestore).
            // Mirrors DashboardViewModel's loadData() combine.
            combine(
                orderRepository.observeOrders(uid),
                customerRepository.observeCustomers(uid),
                userRepository.observeUser(uid).onStart { emit(null) },
            ) { ordersResult, customersResult, firestoreUser ->
                Triple(ordersResult, customersResult, firestoreUser)
            }
                .collect { (ordersResult, customersResult, firestoreUser) ->
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
                    signature = firestoreUser?.businessName?.takeIf { it.isNotBlank() }
                        ?: firestoreUser?.displayName?.takeIf { it.isNotBlank() }
                        ?: user.displayName
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
        val customer = customersById[order.customerId]
        if (customer == null || customer.phone.isBlank()) {
            // No usable contact: an orphaned delivered order (customer deleted) or a
            // customer with no phone. Don't no-op silently or open WhatsApp's generic picker.
            viewModelScope.launch { _events.send(ToCollectEvent.ChaseUnavailable) }
            return
        }
        viewModelScope.launch { _events.send(ToCollectEvent.LaunchWhatsApp(order, customer, signature)) }
    }
}

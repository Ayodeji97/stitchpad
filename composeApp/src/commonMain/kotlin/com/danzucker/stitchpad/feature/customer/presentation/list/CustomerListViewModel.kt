package com.danzucker.stitchpad.feature.customer.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_delete_orders_load_failed
import stitchpad.composeapp.generated.resources.customer_delete_pending_orders_load

class CustomerListViewModel(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val freemiumRepository: FreemiumRepository,
) : ViewModel() {

    /** Cached count of non-delivered orders per customer id, maintained by [observeOrders]. */
    private var activeOrderCountByCustomerId: Map<String, Int> = emptyMap()

    private var hasLoadedInitialData = false
    private var allCustomers: List<Customer> = emptyList()
    private var allLockedCustomers: List<Customer> = emptyList()

    private val _state = MutableStateFlow(CustomerListState())

    private val _events = Channel<CustomerListEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeCustomers()
                observeOrders()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CustomerListState()
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: CustomerListAction) {
        when (action) {
            is CustomerListAction.OnSearchQueryChange -> {
                _state.update {
                    it.copy(
                        searchQuery = action.query,
                        customers = filterCustomers(allCustomers, action.query),
                        lockedCustomers = filterCustomers(allLockedCustomers, action.query),
                    )
                }
            }
            is CustomerListAction.OnCustomerClick -> {
                viewModelScope.launch {
                    _events.send(CustomerListEvent.NavigateToCustomerDetail(action.customer.id))
                }
            }
            CustomerListAction.OnAddCustomerClick -> {
                viewModelScope.launch {
                    _events.send(CustomerListEvent.NavigateToAddCustomer)
                }
            }
            is CustomerListAction.OnDeleteCustomerClick -> {
                val activeCount = activeOrderCountByCustomerId[action.customer.id] ?: 0
                _state.update {
                    it.copy(
                        showDeleteDialog = true,
                        customerToDelete = action.customer,
                        customerToDeleteActiveOrderCount = activeCount
                    )
                }
            }
            CustomerListAction.OnConfirmDelete -> deleteCustomer()
            CustomerListAction.OnDismissDeleteDialog -> {
                _state.update {
                    it.copy(
                        showDeleteDialog = false,
                        customerToDelete = null,
                        customerToDeleteActiveOrderCount = 0
                    )
                }
            }
            CustomerListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
            is CustomerListAction.OpenSwapSheetFor -> {
                _state.update { it.copy(swapSheetForId = action.lockedCustomerId) }
            }
            CustomerListAction.DismissSwapSheet -> {
                _state.update { it.copy(swapSheetForId = null) }
            }
            is CustomerListAction.ConfirmSwap -> {
                viewModelScope.launch {
                    val swapResult = freemiumRepository.swapCustomerSlot(
                        promote = action.lockedCustomerId,
                        demote = action.activeCustomerIdToDemote,
                    )
                    when (swapResult) {
                        is Result.Success -> {
                            val firstName = _state.value.lockedCustomers
                                .firstOrNull { it.id == action.lockedCustomerId }
                                ?.name
                                ?.substringBefore(" ")
                                ?: ""
                            _events.send(CustomerListEvent.SwapSucceeded(firstName))
                        }
                        is Result.Error -> _events.send(CustomerListEvent.SwapFailed)
                    }
                    _state.update { it.copy(swapSheetForId = null) }
                }
            }
        }
    }

    private fun observeCustomers() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            customerRepository.observeCustomers(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val (active, locked) = result.data.partition {
                            it.slotState == CustomerSlotState.ACTIVE
                        }
                        allCustomers = active
                        allLockedCustomers = locked
                        _state.update { state ->
                            state.copy(
                                customers = filterCustomers(active, state.searchQuery),
                                lockedCustomers = filterCustomers(locked, state.searchQuery),
                                isLoading = false
                            )
                        }
                    }
                    is Result.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.error.toCustomerUiText()
                            )
                        }
                    }
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun deleteCustomer() {
        val current = _state.value
        val customer = current.customerToDelete ?: return

        // Race guard #1: we don't yet have a trustworthy active-order count for this customer
        // (orders flow hasn't emitted Success). Refuse with a snackbar so we don't orphan
        // non-delivered orders by deleting on a stale empty count. The two failure modes get
        // distinct copy: still-loading vs. load-failed actionable for the user.
        if (!current.ordersLoaded) {
            val message = if (current.ordersLoadFailed) {
                Res.string.customer_delete_orders_load_failed
            } else {
                Res.string.customer_delete_pending_orders_load
            }
            _state.update {
                it.copy(errorMessage = UiText.StringResourceText(message))
            }
            return
        }

        // Race guard #2: the screen gates the confirm button on customerToDeleteActiveOrderCount,
        // but the cache may have updated since the dialog opened (orders flow re-emitted with
        // newly-created orders). Morph the dialog into the "blocked" variant by writing the
        // current count back into state — the screen already renders that branch.
        val activeCount = activeOrderCountByCustomerId[customer.id] ?: 0
        if (activeCount > 0) {
            _state.update {
                it.copy(customerToDeleteActiveOrderCount = activeCount)
            }
            return
        }

        _state.update {
            it.copy(
                showDeleteDialog = false,
                customerToDelete = null,
                customerToDeleteActiveOrderCount = 0
            )
        }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = customerRepository.deleteCustomer(userId, customer.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toCustomerUiText()) }
            }
        }
    }

    private fun observeOrders() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            orderRepository.observeOrders(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        activeOrderCountByCustomerId = result.data
                            .filter { it.status != OrderStatus.DELIVERED }
                            .groupingBy { it.customerId }
                            .eachCount()
                        _state.update { it.copy(ordersLoaded = true, ordersLoadFailed = false) }
                    }
                    is Result.Error -> {
                        // Don't flip ordersLoaded — `activeOrderCountByCustomerId` is still
                        // empty/stale, and FirebaseCustomerRepository.deleteCustomer is a single-
                        // doc delete with no cascade, so allowing deletion here would orphan any
                        // active orders on the customer. ordersLoadFailed surfaces a specific
                        // snackbar so the user understands why delete is blocked, distinct from
                        // the "still loading" first-emission case.
                        _state.update { it.copy(ordersLoadFailed = true) }
                    }
                }
            }
        }
    }

    private fun filterCustomers(
        customers: List<Customer>,
        query: String,
    ): List<Customer> {
        if (query.isBlank()) return customers
        val q = query.lowercase().trim()
        return customers.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
    }
}

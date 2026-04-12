package com.danzucker.stitchpad.feature.customer.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomerListViewModel(
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerListState())
    val state = _state.asStateFlow()

    private val _events = Channel<CustomerListEvent>()
    val events = _events.receiveAsFlow()

    private var allCustomers: List<Customer> = emptyList()

    init {
        observeCustomers()
    }

    fun onAction(action: CustomerListAction) {
        when (action) {
            is CustomerListAction.OnSearchQueryChange -> {
                _state.update {
                    it.copy(
                        searchQuery = action.query,
                        customers = filterCustomers(allCustomers, action.query, it.deliveryFilter)
                    )
                }
            }
            is CustomerListAction.OnDeliveryFilterChange -> {
                _state.update {
                    it.copy(
                        deliveryFilter = action.filter,
                        customers = filterCustomers(allCustomers, it.searchQuery, action.filter)
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
                _state.update { it.copy(showDeleteDialog = true, customerToDelete = action.customer) }
            }
            CustomerListAction.OnConfirmDelete -> deleteCustomer()
            CustomerListAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, customerToDelete = null) }
            }
            CustomerListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
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
                        allCustomers = result.data
                        _state.update { state ->
                            state.copy(
                                customers = filterCustomers(result.data, state.searchQuery, state.deliveryFilter),
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

    private fun deleteCustomer() {
        val customer = _state.value.customerToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, customerToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = customerRepository.deleteCustomer(userId, customer.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toCustomerUiText()) }
            }
        }
    }

    private fun filterCustomers(
        customers: List<Customer>,
        query: String,
        deliveryFilter: DeliveryPreference?
    ): List<Customer> {
        var result = customers
        if (query.isNotBlank()) {
            val q = query.lowercase().trim()
            result = result.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
        }
        if (deliveryFilter != null) {
            result = result.filter {
                it.deliveryPreference == deliveryFilter ||
                    it.deliveryPreference == DeliveryPreference.EITHER
            }
        }
        return result
    }
}

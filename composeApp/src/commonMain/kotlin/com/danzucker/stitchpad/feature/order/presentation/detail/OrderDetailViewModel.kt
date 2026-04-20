package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val receiptSharer: OrderReceiptSharer
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(OrderDetailState())

    private val _events = Channel<OrderDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadOrder()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OrderDetailState()
        )

    fun onAction(action: OrderDetailAction) {
        when (action) {
            OrderDetailAction.OnEditClick -> {
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToOrderForm(orderId))
                }
            }
            OrderDetailAction.OnDeleteClick -> {
                _state.update { it.copy(showDeleteDialog = true) }
            }
            OrderDetailAction.OnConfirmDelete -> deleteOrder()
            OrderDetailAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false) }
            }
            OrderDetailAction.OnUpdateStatusClick -> {
                _state.update { it.copy(showStatusUpdateDialog = true) }
            }
            is OrderDetailAction.OnSelectNewStatus -> {
                _state.update { it.copy(selectedNewStatus = action.status) }
            }
            OrderDetailAction.OnConfirmStatusUpdate -> updateStatus()
            OrderDetailAction.OnDismissStatusUpdate -> {
                _state.update { it.copy(showStatusUpdateDialog = false, selectedNewStatus = null) }
            }
            OrderDetailAction.OnCustomerClick -> {
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToCustomerDetail(customerId))
                }
            }
            OrderDetailAction.OnShareClick -> {
                val order = _state.value.order ?: return
                receiptSharer.shareReceipt(order)
            }
            OrderDetailAction.OnBackClick -> {
                viewModelScope.launch { _events.send(OrderDetailEvent.NavigateBack) }
            }
            OrderDetailAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun loadOrder() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            when (val result = orderRepository.getOrder(userId, orderId)) {
                is Result.Success -> {
                    _state.update { it.copy(order = result.data, isLoading = false) }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                    }
                }
            }
        }
    }

    private fun deleteOrder() {
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val result = orderRepository.deleteOrder(userId, orderId)) {
                is Result.Success -> _events.send(OrderDetailEvent.OrderDeleted)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }

    private fun updateStatus() {
        val newStatus = _state.value.selectedNewStatus ?: return
        _state.update { it.copy(showStatusUpdateDialog = false, selectedNewStatus = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val result = orderRepository.updateOrderStatus(userId, orderId, newStatus)) {
                is Result.Success -> loadOrder()
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }
}

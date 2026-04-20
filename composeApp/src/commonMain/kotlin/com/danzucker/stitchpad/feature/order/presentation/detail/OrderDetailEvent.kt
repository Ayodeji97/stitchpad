package com.danzucker.stitchpad.feature.order.presentation.detail

sealed interface OrderDetailEvent {
    data class NavigateToOrderForm(val orderId: String) : OrderDetailEvent
    data class NavigateToCustomerDetail(val customerId: String) : OrderDetailEvent
    data object NavigateBack : OrderDetailEvent
    data object OrderDeleted : OrderDetailEvent
}

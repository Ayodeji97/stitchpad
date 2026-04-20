package com.danzucker.stitchpad.feature.order.presentation.list

sealed interface OrderListEvent {
    data object NavigateToOrderForm : OrderListEvent
    data class NavigateToOrderDetail(val orderId: String) : OrderListEvent
}

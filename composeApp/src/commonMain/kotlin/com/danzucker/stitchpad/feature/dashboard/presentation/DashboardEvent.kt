package com.danzucker.stitchpad.feature.dashboard.presentation

sealed interface DashboardEvent {
    data class NavigateToOrderDetail(val orderId: String) : DashboardEvent
    data object NavigateToOrders : DashboardEvent
    data object NavigateToOrderForm : DashboardEvent
    data object NavigateToCustomerForm : DashboardEvent
}

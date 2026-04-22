package com.danzucker.stitchpad.feature.dashboard.presentation

sealed interface DashboardAction {
    data class OnOrderClick(val orderId: String) : DashboardAction
    data object OnSeeAllClick : DashboardAction
    data object OnOutstandingClick : DashboardAction
    data object OnNewOrderClick : DashboardAction
    data object OnNewCustomerClick : DashboardAction
    data object OnErrorDismiss : DashboardAction
}

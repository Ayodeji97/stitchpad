package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate

sealed interface DashboardEvent {
    data class NavigateToOrderDetail(val orderId: String) : DashboardEvent
    data class LaunchWhatsApp(val action: NextBestAction) : DashboardEvent
    data class LaunchWhatsAppForReconnect(val candidate: ReconnectCandidate) : DashboardEvent
    data object NavigateToOrders : DashboardEvent
    data object NavigateToOrderForm : DashboardEvent
    data object NavigateToCustomerForm : DashboardEvent
    data object NavigateToCustomers : DashboardEvent
    data object NavigateToGoalSetup : DashboardEvent
}

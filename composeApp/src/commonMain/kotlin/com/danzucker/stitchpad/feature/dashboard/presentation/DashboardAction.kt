package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate

sealed interface DashboardAction {
    data class OnOrderClick(val orderId: String) : DashboardAction
    data class OnNextActionPrimaryClick(val action: NextBestAction) : DashboardAction
    data class OnReconnectCandidateClick(val candidate: ReconnectCandidate) : DashboardAction
    data object OnSeeAllClick : DashboardAction
    data object OnOutstandingClick : DashboardAction
    data object OnNewOrderClick : DashboardAction
    data object OnNewCustomerClick : DashboardAction
    data object OnAddMeasurementClick : DashboardAction
    data object OnGoalsCardClick : DashboardAction
    data object OnFocusCtaClick : DashboardAction
    data object OnSettingsClick : DashboardAction
    data object OnErrorDismiss : DashboardAction
}

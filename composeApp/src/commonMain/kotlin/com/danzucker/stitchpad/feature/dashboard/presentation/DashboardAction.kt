package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate

sealed interface DashboardAction {
    data class OnOrderClick(val orderId: String) : DashboardAction
    data class OnNextActionPrimaryClick(val action: NextBestAction) : DashboardAction
    data class OnReconnectCandidateClick(val candidate: ReconnectCandidate) : DashboardAction

    /** Fired by ReconnectChipStrip — looks up the candidate by ID and launches WhatsApp. */
    data class OnReconnectClick(val customerId: String) : DashboardAction

    data object OnSeeAllClick : DashboardAction
    data object OnOutstandingClick : DashboardAction
    data object OnNewOrderClick : DashboardAction

    /** Alias used by the V2 pipeline empty-state CTA. Routes the same as [OnNewOrderClick]. */
    data object OnCreateOrderClick : DashboardAction

    data object OnNewCustomerClick : DashboardAction
    data object OnAddMeasurementClick : DashboardAction
    data object OnGoalsCardClick : DashboardAction
    data object OnFocusCtaClick : DashboardAction
    data object OnSettingsClick : DashboardAction
    data object OnErrorDismiss : DashboardAction

    /** Opens the Orders tab with no filter — "View all" in TodayWorkCard. */
    data object OnViewAllOrdersClick : DashboardAction

    /** Opens the Orders tab filtered to in-progress orders. */
    data object OnViewPipelineInProgressClick : DashboardAction

    /** Opens the Orders tab filtered to pending / not-started orders. */
    data object OnViewPipelineNotStartedClick : DashboardAction

    /** Opens the full reconnect list (trailing chevron in ReconnectChipStrip). */
    data object OnViewReconnectClick : DashboardAction
}

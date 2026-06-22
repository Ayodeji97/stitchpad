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
    data object OnInspirationClick : DashboardAction
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

    /** Tapped the body or "View customer" link on a reconnect hero card → open detail. */
    data class OnReconnectViewCustomerClick(val customerId: String) : DashboardAction

    /**
     * Tapped the active row of the FirstCustomer setup checklist.
     * Currently the only routable step is "Add first order"; the other
     * pending steps are not yet tappable in the UI.
     */
    data object OnSetupChecklistAdvance : DashboardAction

    /**
     * Tapped Setup Checklist step 3 (Set due date) or step 4 (Record
     * deposit) — both fields live on the Edit Order form, not on the
     * read-only Order Detail screen. Carries the first order's id so
     * the form opens pre-populated.
     */
    data class OnSetupOrderEditClick(val orderId: String) : DashboardAction

    /** Smart Suggestions section card tapped → open Draft Message screen. */
    data object OnDraftMessageClick : DashboardAction

    /** Welcome-ending banner CTA tapped → navigate to the Upgrade screen. */
    data object OpenUpgrade : DashboardAction

    /** Tapped the body of the "Your customer" card → open detail. */
    data class OnCustomerReadyClick(val customerId: String) : DashboardAction

    /**
     * Tapped the round Message button on the "Your customer" card →
     * launch WhatsApp using the customer's name + phone.
     */
    data class OnCustomerReadyMessageClick(val customerId: String) : DashboardAction

    /** Tapped the bell / notifications button → navigate to the in-app inbox. */
    data object OnNotificationsClick : DashboardAction

    /** Community banner Join tapped → open the invite + record + dismiss. */
    data object OnJoinCommunity : DashboardAction

    /** Community banner ✕ tapped → hide it for good (local flag). */
    data object OnDismissCommunityBanner : DashboardAction
}

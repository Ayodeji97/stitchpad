package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate

sealed interface DashboardEvent {
    data class NavigateToOrderDetail(val orderId: String) : DashboardEvent
    data class LaunchWhatsApp(val action: NextBestAction) : DashboardEvent
    data class LaunchWhatsAppForReconnect(val candidate: ReconnectCandidate) : DashboardEvent
    data object NavigateToOrders : DashboardEvent
    data object NavigateToOrderForm : DashboardEvent

    /**
     * Open the order edit form pre-populated for [orderId]. Used by the
     * Setup Checklist's SetDueDate / RecordDeposit step taps so the user
     * lands on the screen that actually edits those fields, instead of
     * the read-only Order Detail surface.
     */
    data class NavigateToEditOrder(val orderId: String) : DashboardEvent
    data object NavigateToCustomerForm : DashboardEvent
    data object NavigateToCustomers : DashboardEvent
    data object NavigateToInspiration : DashboardEvent
    data object NavigateToGoalSetup : DashboardEvent
    data object NavigateToSettings : DashboardEvent

    /**
     * BrandNew safety gate: when there are no customers yet, taps that
     * would dead-end on the order/measurement form (hero CTA, "Create
     * order" tile, "Measurement" tile) route here instead. The screen
     * itself routes onward to the customer form.
     */
    data object NavigateToAddCustomerFirst : DashboardEvent

    /** Open the customer detail screen — used by the FirstCustomer card. */
    data class NavigateToCustomerDetail(val customerId: String) : DashboardEvent

    /** Smart Suggestions: open the Draft Message screen. */
    data object NavigateToDraftMessage : DashboardEvent

    /** Welcome-ending banner CTA: navigate to the Upgrade / Tailor Pro screen. */
    data object NavigateToUpgrade : DashboardEvent

    /** Bell button tapped: navigate to the in-app notifications inbox. */
    data object NavigateToNotifications : DashboardEvent

    /** Open the WhatsApp community invite (chat.whatsapp.com) directly. */
    data class OpenCommunityLink(val url: String) : DashboardEvent

    /** Picker row with exactly one measurement → open it directly. */
    data class NavigateToMeasurementDetail(val customerId: String, val measurementId: String) : DashboardEvent

    /**
     * Picker row with a confirmed-zero count → straight to the create form. The
     * picker row already told the user the customer is empty ("+ Add"), so the
     * detail screen's empty state would be a redundant stop here — unlike the
     * customer actions sheet, which routes zero to the empty-mode detail.
     */
    data class NavigateToAddMeasurement(val customerId: String) : DashboardEvent
}

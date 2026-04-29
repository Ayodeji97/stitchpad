package com.danzucker.stitchpad.feature.reports.presentation

sealed interface ReportsEvent {
    data class NavigateToCustomerDetail(val customerId: String) : ReportsEvent
    data class LaunchWhatsAppReminder(val customerId: String) : ReportsEvent
}

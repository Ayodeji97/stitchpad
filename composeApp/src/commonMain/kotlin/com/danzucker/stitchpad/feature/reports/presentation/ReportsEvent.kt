package com.danzucker.stitchpad.feature.reports.presentation

import kotlinx.datetime.LocalDate

sealed interface ReportsEvent {
    data class NavigateToCustomerDetail(val customerId: String) : ReportsEvent

    /**
     * Carries everything ReportsRoot needs to launch a WhatsApp reminder for an
     * outstanding balance — phone, customer name, owed amount, and the oldest
     * deadline (nullable). The Root composable resolves the message template and
     * calls [WhatsAppLauncher.launch].
     */
    data class LaunchWhatsAppReminder(
        val customerName: String,
        val customerPhone: String,
        val totalOwed: Double,
        val oldestDeadline: LocalDate?
    ) : ReportsEvent

    /** Fired when a Free-tier tailor taps the paywall card's Upgrade CTA. */
    data object NavigateToUpgrade : ReportsEvent
}

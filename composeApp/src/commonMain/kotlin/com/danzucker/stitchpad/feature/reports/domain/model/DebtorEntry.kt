package com.danzucker.stitchpad.feature.reports.domain.model

import kotlinx.datetime.LocalDate

data class DebtorEntry(
    val customerId: String,
    val customerName: String,
    val totalOwed: Double,
    val orderCount: Int,
    val oldestDeadline: LocalDate?,
    // Drives whether the row renders a WhatsApp button — without a phone
    // the deep-link can't go anywhere, so the button is hidden rather
    // than silently no-op'ing on tap.
    val canSendWhatsAppReminder: Boolean
)

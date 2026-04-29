package com.danzucker.stitchpad.feature.reports.domain.model

import kotlinx.datetime.LocalDate

data class DebtorEntry(
    val customerId: String,
    val customerName: String,
    val totalOwed: Double,
    val oldestDeadline: LocalDate?
)

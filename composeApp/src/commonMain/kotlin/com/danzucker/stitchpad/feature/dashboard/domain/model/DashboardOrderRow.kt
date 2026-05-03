package com.danzucker.stitchpad.feature.dashboard.domain.model

data class DashboardOrderRow(
    val orderId: String,
    val customerName: String,
    val primaryLabel: String,
    /** Positive number of days the order is overdue, or null when not overdue. */
    val daysLate: Int? = null,
    /** Positive number of days until the deadline (future only). Null otherwise. */
    val daysUntilDeadline: Int? = null,
    /**
     * Order creation timestamp in epoch millis. Surfaced in the V2 pipeline
     * row's metadata footer ("Created 2 May"). Defaults to 0 for legacy
     * call-sites / tests that don't supply it.
     */
    val createdAtEpochMillis: Long = 0L
)

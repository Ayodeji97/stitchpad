package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Compact view of an open Order used by the Draft Message picker. Already-
 * formatted strings — the picker doesn't compute currency or dates itself.
 */
data class OrderSummary(
    val id: String,
    val customerId: String,
    val garmentLabel: String,
    val balanceFormatted: String,
    val deadlineFormatted: String,
)

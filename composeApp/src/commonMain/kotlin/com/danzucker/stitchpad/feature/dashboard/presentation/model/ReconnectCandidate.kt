package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * A past customer suggested for re-engagement on quiet days. Surfaced in
 * [ReconnectChipStrip]; tapping launches WhatsApp with a friendly template.
 *
 * Filtered to customers with [daysSinceLastInteraction] >= a threshold so we
 * don't nag customers who collected an order recently.
 */
data class ReconnectCandidate(
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val daysSinceLastInteraction: Int,
    val hasOrderHistory: Boolean
)

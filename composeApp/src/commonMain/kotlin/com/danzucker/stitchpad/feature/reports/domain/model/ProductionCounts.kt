package com.danzucker.stitchpad.feature.reports.domain.model

/**
 * All-time order counts grouped by [com.danzucker.stitchpad.core.domain.model.OrderStatus].
 * Drives the Production Status card on Reports V2.
 */
data class ProductionCounts(
    val pending: Int,
    val inProgress: Int,
    val ready: Int,
    val delivered: Int
) {
    val total: Int get() = pending + inProgress + ready + delivered
}

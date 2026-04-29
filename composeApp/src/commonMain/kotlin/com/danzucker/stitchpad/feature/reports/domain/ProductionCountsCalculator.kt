package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.reports.domain.model.ProductionCounts

/**
 * Counts orders by [OrderStatus] across all time. The 4 buckets mirror the
 * existing OrderStatus enum (no migration to a 6-stage tailor model — that's
 * a separate feature).
 */
object ProductionCountsCalculator {

    fun compute(orders: List<Order>): ProductionCounts {
        var pending = 0
        var inProgress = 0
        var ready = 0
        var delivered = 0
        for (order in orders) {
            when (order.status) {
                OrderStatus.PENDING -> pending++
                OrderStatus.IN_PROGRESS -> inProgress++
                OrderStatus.READY -> ready++
                OrderStatus.DELIVERED -> delivered++
            }
        }
        return ProductionCounts(
            pending = pending,
            inProgress = inProgress,
            ready = ready,
            delivered = delivered
        )
    }
}

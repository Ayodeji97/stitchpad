package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

/**
 * Money-free production-stage counts for the STAFF dashboard's pipeline strip.
 * Mirrors how a tailor narrates work: cutting -> sewing -> fitting -> ready.
 *
 * [inProgressTotal] is the sum of the three IN_PROGRESS sub-stages, so the
 * "In progress" count tile and the pipeline strip always agree.
 */
data class StaffPipelineCounts(
    val cutting: Int = 0,
    val sewing: Int = 0,
    val fitting: Int = 0,
    val ready: Int = 0,
) {
    val inProgressTotal: Int get() = cutting + sewing + fitting
    val isEmpty: Boolean get() = inProgressTotal + ready == 0
}

/**
 * Pure aggregation of [Order]s into [StaffPipelineCounts]. No I/O, no clock —
 * the staff dashboard is a work view, so this counts stages only and never
 * touches money.
 *
 *  - cutting / sewing / fitting: IN_PROGRESS orders grouped by [OrderSubStatus].
 *    A null sub-status counts as CUTTING (the first stage) — the default an
 *    order lands on when work begins.
 *  - ready: orders in READY status (finished, awaiting pickup).
 *  - PENDING orders are "not started" and DELIVERED orders are done, so neither
 *    contributes to the live-work strip.
 */
object StaffPipelineCalculator {
    fun compute(orders: List<Order>): StaffPipelineCounts {
        val inProgress = orders.filter { it.status == OrderStatus.IN_PROGRESS }
        return StaffPipelineCounts(
            cutting = inProgress.count { it.subStatus == null || it.subStatus == OrderSubStatus.CUTTING },
            sewing = inProgress.count { it.subStatus == OrderSubStatus.SEWING },
            fitting = inProgress.count { it.subStatus == OrderSubStatus.FITTING },
            ready = orders.count { it.status == OrderStatus.READY },
        )
    }
}

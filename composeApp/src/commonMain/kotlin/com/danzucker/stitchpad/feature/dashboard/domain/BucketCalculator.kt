package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.displayGarmentName
import com.danzucker.stitchpad.feature.dashboard.domain.internal.simpleLabel
import com.danzucker.stitchpad.feature.dashboard.domain.internal.toLocalDate
import com.danzucker.stitchpad.feature.dashboard.domain.model.Buckets
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.domain.model.stageOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

private const val PIPELINE_PREVIEW_LIMIT = 3

/**
 * Buckets `orders` into the triage / pipeline / outstanding views the dashboard
 * renders. Pure function — no I/O, no clock side effects. The caller owns
 * deciding `today` and `timeZone`.
 *
 *  - `overdue`: active orders with deadline strictly before `today`, sorted by deadline.
 *  - `dueToday`: active orders with deadline == today.
 *  - `ready`: orders in READY status (regardless of deadline).
 *  - `outstandingAmount` / `outstandingOrderCount`: active orders with positive balance.
 *  - `pipelineInProgress` / `pipelinePending`: capped to [PIPELINE_PREVIEW_LIMIT]
 *    each, excluding anything already surfaced in triage (overdue / due-today /
 *    ready). Totals reflect the full count before the cap.
 */
object BucketCalculator {

    @Suppress("LongMethod")
    fun compute(
        orders: List<Order>,
        today: LocalDate,
        timeZone: TimeZone
    ): Buckets {
        val active = orders.filter { it.status != OrderStatus.DELIVERED }

        val overdue = active
            .filter { order ->
                val deadlineDate = order.deadline?.toLocalDate(timeZone)
                deadlineDate != null && deadlineDate < today
            }
            .sortedBy { it.deadline }
            .map { it.toRow(today, timeZone) }

        val dueToday = active
            .filter { order ->
                order.deadline?.toLocalDate(timeZone) == today
            }
            .map { it.toRow(today, timeZone) }

        val ready = orders
            .filter { it.status == OrderStatus.READY }
            .map { it.toRow(today, timeZone) }

        val unpaid = active.filter { it.balanceRemaining > 0.0 }

        val triageOrderIds = buildSet {
            active.forEach { order ->
                val deadlineDate = order.deadline?.toLocalDate(timeZone)
                if (deadlineDate != null && deadlineDate <= today) add(order.id)
            }
            orders.filter { it.status == OrderStatus.READY }.forEach { add(it.id) }
        }

        val pipelineCandidates = active
            .filter { it.id !in triageOrderIds }
            .sortedWith(compareBy(nullsLast()) { it.deadline })

        val pipelineInProgressAll = pipelineCandidates.filter { it.status == OrderStatus.IN_PROGRESS }
        val pipelinePendingAll = pipelineCandidates.filter { it.status == OrderStatus.PENDING }

        val openQueue = active
            .filter { it.status != OrderStatus.READY }
            .map { it.toQueueRow(today, timeZone) }

        return Buckets(
            overdue = overdue,
            dueToday = dueToday,
            ready = ready,
            outstandingAmount = unpaid.sumOf { it.balanceRemaining },
            outstandingOrderCount = unpaid.size,
            pipelineInProgress = pipelineInProgressAll.take(PIPELINE_PREVIEW_LIMIT)
                .map { it.toPipelineRow(today, timeZone) },
            pipelineInProgressTotal = pipelineInProgressAll.size,
            pipelinePending = pipelinePendingAll.take(PIPELINE_PREVIEW_LIMIT)
                .map { it.toPipelineRow(today, timeZone) },
            pipelinePendingTotal = pipelinePendingAll.size,
            openQueue = openQueue,
        )
    }
}

private fun Order.toRow(today: LocalDate, tz: TimeZone): DashboardOrderRow {
    val garment = items.firstOrNull()?.displayGarmentName { it.simpleLabel() }.orEmpty()
    val deadlineDate = deadline?.toLocalDate(tz)
    val daysLate = deadlineDate
        ?.takeIf { it < today }
        ?.daysUntil(today)
    return DashboardOrderRow(
        orderId = id,
        customerName = customerName,
        primaryLabel = garment,
        daysLate = daysLate,
        orderValue = payableTotal,
        paymentStatus = pipelinePaymentStatusOf(depositPaid, payableTotal),
        assignedMemberId = assignedMemberId,
        assignedMemberName = assignedMemberName,
        stage = stageOf(status, subStatus),
    )
}

private fun Order.toPipelineRow(today: LocalDate, tz: TimeZone): DashboardOrderRow {
    val garment = items.firstOrNull()?.displayGarmentName { it.simpleLabel() }.orEmpty()
    val deadlineDate = deadline?.toLocalDate(tz)
    val daysUntil = deadlineDate
        ?.takeIf { it > today }
        ?.let { today.daysUntil(it) }
    return DashboardOrderRow(
        orderId = id,
        customerName = customerName,
        primaryLabel = garment,
        daysUntilDeadline = daysUntil,
        createdAtEpochMillis = createdAt,
        orderValue = payableTotal,
        paymentStatus = pipelinePaymentStatusOf(depositPaid, payableTotal),
        assignedMemberId = assignedMemberId,
        assignedMemberName = assignedMemberName,
        stage = stageOf(status, subStatus),
    )
}

/**
 * Row builder for [Buckets.openQueue] — the staff dashboard focus-queue's full
 * candidate pool. Unlike [toRow] (daysLate only) and [toPipelineRow]
 * (daysUntilDeadline only, future-only), this exposes whichever field applies
 * to the order's actual deadline relationship to `today`, including 0 for "due
 * today" (so the urgency calibration's `soon` tier can render "Due today").
 */
private fun Order.toQueueRow(today: LocalDate, tz: TimeZone): DashboardOrderRow {
    val garment = items.firstOrNull()?.displayGarmentName { it.simpleLabel() }.orEmpty()
    val deadlineDate = deadline?.toLocalDate(tz)
    val daysLate = deadlineDate
        ?.takeIf { it < today }
        ?.daysUntil(today)
    val daysUntilDeadline = deadlineDate
        ?.takeIf { it >= today }
        ?.let { today.daysUntil(it) }
    return DashboardOrderRow(
        orderId = id,
        customerName = customerName,
        primaryLabel = garment,
        daysLate = daysLate,
        daysUntilDeadline = daysUntilDeadline,
        createdAtEpochMillis = createdAt,
        orderValue = payableTotal,
        paymentStatus = pipelinePaymentStatusOf(depositPaid, payableTotal),
        assignedMemberId = assignedMemberId,
        assignedMemberName = assignedMemberName,
        stage = stageOf(status, subStatus),
    )
}

package com.danzucker.stitchpad.feature.dashboard.domain.model

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

/**
 * The five-stage production pipeline shown on the staff dashboard's focus-queue
 * hero stepper and ticket footers. Mirrors the private `TimelineStage` /
 * `TransitionStage` enums in the order-detail feature (production timeline +
 * status transition sheet) — those stay file-private there since they also
 * model DELIVERED for that screen's own "back to any stage" picker. This one
 * omits DELIVERED: delivered orders never reach a staff dashboard queue row
 * (they're filtered out of [com.danzucker.stitchpad.feature.dashboard.domain.BucketCalculator]'s
 * `active` set before a stage is ever assigned), and READY orders — while
 * modelled here for a complete dot-fill sequence — are excluded from hero and
 * queue selection by [com.danzucker.stitchpad.feature.dashboard.domain.computeFocusQueue]
 * (they're awaiting owner pickup, nothing left for staff to advance).
 */
enum class PipelineStage {
    PENDING,
    CUTTING,
    SEWING,
    FITTING,
    READY,
    ;

    /** The stage the "Mark done" CTA advances to. Null when already at the terminal READY stage. */
    fun next(): PipelineStage? = entries.getOrNull(ordinal + 1)
}

/**
 * Maps an order's (status, subStatus) onto its [PipelineStage]. Null sub-status
 * under IN_PROGRESS counts as CUTTING (the first sub-stage), matching
 * [com.danzucker.stitchpad.feature.dashboard.domain.StaffPipelineCalculator]'s
 * convention. Returns null for DELIVERED — that order is terminal and never
 * surfaced on the staff dashboard queue.
 */
fun stageOf(status: OrderStatus, subStatus: OrderSubStatus?): PipelineStage? = when (status) {
    OrderStatus.PENDING -> PipelineStage.PENDING
    OrderStatus.IN_PROGRESS -> when (subStatus) {
        OrderSubStatus.SEWING -> PipelineStage.SEWING
        OrderSubStatus.FITTING -> PipelineStage.FITTING
        OrderSubStatus.CUTTING, null -> PipelineStage.CUTTING
    }
    OrderStatus.READY -> PipelineStage.READY
    OrderStatus.DELIVERED -> null
}

/** The (status, subStatus) pair to write when the "Mark done" CTA advances TO this stage. */
fun PipelineStage.toOrderStatusAndSubStatus(): Pair<OrderStatus, OrderSubStatus?> = when (this) {
    PipelineStage.PENDING -> OrderStatus.PENDING to null
    PipelineStage.CUTTING -> OrderStatus.IN_PROGRESS to OrderSubStatus.CUTTING
    PipelineStage.SEWING -> OrderStatus.IN_PROGRESS to OrderSubStatus.SEWING
    PipelineStage.FITTING -> OrderStatus.IN_PROGRESS to OrderSubStatus.FITTING
    PipelineStage.READY -> OrderStatus.READY to null
}

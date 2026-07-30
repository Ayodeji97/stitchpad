package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

/** A move available from the current (status, subStatus) tuple. */
data class StatusTransition(
    val toStatus: OrderStatus,
    val toSubStatus: OrderSubStatus?,
)

/**
 * Whether advancing to [toStatus] should raise the "balance still owed" warning
 * before proceeding. Only Ready / Delivered with a positive balance warrants it.
 *
 * Active staff never see money (Slice 6c), so the warning — which reads the balance
 * aloud — is suppressed for them and the transition proceeds directly.
 */
internal fun requiresBalanceWarning(
    isActiveStaff: Boolean,
    balanceRemaining: Double,
    toStatus: OrderStatus,
): Boolean = !isActiveStaff &&
    balanceRemaining > 0.0 &&
    (toStatus == OrderStatus.READY || toStatus == OrderStatus.DELIVERED)

/**
 * The full production pipeline, one entry per stage, in forward order. This is
 * the canonical ordering the status sheet renders.
 */
private val ALL_STAGES: List<StatusTransition> = listOf(
    StatusTransition(OrderStatus.PENDING, null),
    StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
    StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
    StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
    StatusTransition(OrderStatus.READY, null),
    StatusTransition(OrderStatus.DELIVERED, null),
)

/**
 * Every stage in the pipeline except the order's current one, in forward order.
 *
 * Unlike a next-only picker, the complete list is always shown so a tailor can
 * jump straight to any stage — forward OR back — in a single tap instead of
 * stepping through the intermediate states one "back" at a time. The current
 * stage is omitted (selecting it would be a no-op; the sheet title already
 * names it). This also means a DELIVERED order can be moved back, which the
 * order-detail card relies on to keep an "Update Status" action available.
 */
internal fun allStatusTransitions(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
): List<StatusTransition> {
    // Normalise IN_PROGRESS with no sub-status to CUTTING so the current stage
    // is matched (and excluded) correctly — mirrors resolveTransitionStage.
    val normalizedSub = currentSubStatus
        ?: if (currentStatus == OrderStatus.IN_PROGRESS) OrderSubStatus.CUTTING else null
    return ALL_STAGES.filterNot {
        it.toStatus == currentStatus && it.toSubStatus == normalizedSub
    }
}

package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

/** A move available from the current (status, subStatus) tuple. */
data class StatusTransition(
    val toStatus: OrderStatus,
    val toSubStatus: OrderSubStatus?,
)

/**
 * Picker contents for moving an order forward (or back) from its current
 * stage. Forward moves come first, back moves at the end. Empty list means
 * no transitions are available (i.e. the order is delivered).
 */
internal fun nextStatusTransitions(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
): List<StatusTransition> {
    val effectiveSub = currentSubStatus
        ?: if (currentStatus == OrderStatus.IN_PROGRESS) OrderSubStatus.CUTTING else null
    return when (currentStatus) {
        OrderStatus.PENDING -> listOf(
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
            StatusTransition(OrderStatus.READY, null),
            StatusTransition(OrderStatus.DELIVERED, null),
        )
        OrderStatus.IN_PROGRESS -> when (effectiveSub) {
            OrderSubStatus.CUTTING -> listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.PENDING, null),
            )
            OrderSubStatus.SEWING -> listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            )
            OrderSubStatus.FITTING -> listOf(
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            )
            null -> emptyList() // unreachable: handled by the elvis above
        }
        OrderStatus.READY -> listOf(
            StatusTransition(OrderStatus.DELIVERED, null),
            StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
        )
        OrderStatus.DELIVERED -> emptyList()
    }
}

@file:Suppress("MatchingDeclarationName", "Filename")

package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

enum class TriageGroup {
    OVERDUE,
    DUE_THIS_WEEK,
    IN_PROGRESS,
    READY_FOR_PICKUP,
    PENDING
}

private val displayOrder = listOf(
    TriageGroup.OVERDUE,
    TriageGroup.DUE_THIS_WEEK,
    TriageGroup.IN_PROGRESS,
    TriageGroup.READY_FOR_PICKUP,
    TriageGroup.PENDING
)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val DUE_SOON_DAYS = 7L

fun groupOrdersIntoTriage(orders: List<Order>, now: Long): Map<TriageGroup, List<Order>> {
    val buckets = linkedMapOf<TriageGroup, MutableList<Order>>()
    displayOrder.forEach { buckets[it] = mutableListOf() }

    for (order in orders) {
        val group = classify(order, now) ?: continue
        buckets.getValue(group).add(order)
    }

    val deadlineComparator = Comparator<Order> { a, b ->
        val deadlineCompare = compareValuesBy(a, b, nullsLast<Long>()) { it.deadline }
        if (deadlineCompare != 0) deadlineCompare else b.createdAt.compareTo(a.createdAt)
    }

    return buckets
        .mapValues { (_, list) -> list.sortedWith(deadlineComparator) }
        .filterValues { it.isNotEmpty() }
}

private fun classify(order: Order, now: Long): TriageGroup? = when {
    order.status == OrderStatus.DELIVERED -> null
    order.status == OrderStatus.READY -> TriageGroup.READY_FOR_PICKUP
    order.deadline != null && order.deadline < now -> TriageGroup.OVERDUE
    order.status == OrderStatus.IN_PROGRESS -> TriageGroup.IN_PROGRESS
    order.status == OrderStatus.PENDING &&
        order.deadline != null &&
        (order.deadline - now) / MILLIS_PER_DAY in 0..DUE_SOON_DAYS -> TriageGroup.DUE_THIS_WEEK
    else -> TriageGroup.PENDING
}

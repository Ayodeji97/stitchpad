package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

/**
 * Counts open orders per assignee for the Team screen's "who is working on what"
 * overview (Task 9). Keyed by [Order.assignedMemberId] — the same id space as a
 * roster row's `TeamMember.id` — with `null` bucketing the unassigned orders.
 *
 * Open = not archived AND status != DELIVERED. [OrderRepository.observeOrders] already
 * excludes archived orders, but the `archivedAt == null` check is kept here too: it's a
 * cheap, defensive match to the spec's stated definition rather than an implicit trust
 * in the caller's stream having already filtered it out.
 */
internal fun openOrderCountsByAssignee(orders: List<Order>): Map<String?, Int> =
    orders
        .filter { it.archivedAt == null && it.status != OrderStatus.DELIVERED }
        .groupingBy { it.assignedMemberId }
        .eachCount()

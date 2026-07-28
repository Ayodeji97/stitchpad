package com.danzucker.stitchpad.feature.collection.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

/**
 * Single source of truth for "money to collect": orders that are done
 * (Ready or Delivered) but still owe a balance. Pure and time-injected so it
 * is trivially testable and shared by the dashboard card and the To-Collect list.
 */
object CollectionCalculator {

    const val OVERDUE_THRESHOLD_DAYS = 7
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun collectibles(
        orders: List<Order>,
        customersById: Map<String, Customer>,
        now: Long,
    ): List<CollectibleOrder> =
        orders
            .filter { it.status == OrderStatus.READY || it.status == OrderStatus.DELIVERED }
            .filter { it.balanceRemaining > 0.0 }
            .map { order ->
                val owedSince = owedSince(order)
                val daysOwed = daysBetween(owedSince, now)
                CollectibleOrder(
                    orderId = order.id,
                    customerId = order.customerId,
                    customerName = order.customerName,
                    customerPhone = customersById[order.customerId]?.phone.orEmpty(),
                    balanceRemaining = order.balanceRemaining,
                    owedSince = owedSince,
                    daysOwed = daysOwed,
                    isOverdue = daysOwed >= OVERDUE_THRESHOLD_DAYS,
                    status = order.status,
                )
            }

    /** The moment the garment first became collectible (Ready or Delivered). */
    private fun owedSince(order: Order): Long {
        val readyOrDelivered = order.statusHistory
            .filter { it.status == OrderStatus.READY || it.status == OrderStatus.DELIVERED }
            .minOfOrNull { it.changedAt }
        return readyOrDelivered
            ?: order.updatedAt.takeIf { it > 0L }
            ?: order.createdAt
    }

    private fun daysBetween(from: Long, to: Long): Int {
        if (to <= from) return 0
        return ((to - from) / MILLIS_PER_DAY).toInt()
    }
}

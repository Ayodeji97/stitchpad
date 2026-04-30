package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.dashboard.domain.internal.toLocalDate
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

private const val RECONNECT_LIMIT = 5
private const val RECONNECT_MIN_DAYS = 14

/**
 * Builds the list of customers to surface in the reconnect chip strip. A candidate is
 * a customer with no active (non-DELIVERED) order. Customers with order history
 * must have been inactive for at least [RECONNECT_MIN_DAYS] days; customers
 * with no order history (e.g. just-added) always pass. Capped at
 * [RECONNECT_LIMIT].
 *
 * The single pass over `orders` builds two facts at once: which customers have
 * any active order (so they can be excluded), and the max `updatedAt` per
 * customer (so days-since can be computed without a per-customer scan of the
 * full order list).
 */
object ReconnectCalculator {

    fun compute(
        orders: List<Order>,
        customers: List<Customer>,
        today: LocalDate,
        timeZone: TimeZone
    ): List<ReconnectCandidate> {
        if (customers.isEmpty()) return emptyList()

        val activeOrderCustomerIds = HashSet<String>(orders.size)
        val mostRecentByCustomer = HashMap<String, Long>(customers.size)
        for (order in orders) {
            if (order.status != OrderStatus.DELIVERED) {
                activeOrderCustomerIds.add(order.customerId)
            }
            val previous = mostRecentByCustomer[order.customerId]
            if (previous == null || order.updatedAt > previous) {
                mostRecentByCustomer[order.customerId] = order.updatedAt
            }
        }

        return customers
            .asSequence()
            .filter { it.id !in activeOrderCustomerIds }
            .filter { it.phone.isNotBlank() }
            .map { customer ->
                val mostRecentMillis = mostRecentByCustomer[customer.id]
                val daysSince = if (mostRecentMillis != null && mostRecentMillis > 0L) {
                    mostRecentMillis.toLocalDate(timeZone).daysUntil(today).coerceAtLeast(0)
                } else {
                    0
                }
                ReconnectCandidate(
                    customerId = customer.id,
                    customerName = customer.name,
                    customerPhone = customer.phone,
                    daysSinceLastInteraction = daysSince,
                    hasOrderHistory = mostRecentMillis != null
                )
            }
            .filter { !it.hasOrderHistory || it.daysSinceLastInteraction >= RECONNECT_MIN_DAYS }
            .sortedByDescending { it.daysSinceLastInteraction }
            .take(RECONNECT_LIMIT)
            .toList()
    }
}

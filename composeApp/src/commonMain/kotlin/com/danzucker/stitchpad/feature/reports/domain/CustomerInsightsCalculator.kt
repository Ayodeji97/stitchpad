package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DEFAULT_LIMIT = 5

/**
 * Builds the two customer-facing lists shown on Reports: top earners within
 * the selected window, and (period-independent) biggest debtors. Both are
 * pure transformations over the existing order + customer snapshots.
 */
object CustomerInsightsCalculator {

    fun topCustomers(
        orders: List<Order>,
        customers: List<Customer>,
        period: ReportsPeriod,
        today: LocalDate,
        timeZone: TimeZone,
        limit: Int = DEFAULT_LIMIT
    ): List<CustomerRanking> {
        if (customers.isEmpty()) return emptyList()
        val (windowStart, windowEnd) = reportsWindow(period, today, timeZone, periodsBack = 0)
        val customersById = customers.associateBy { it.id }

        data class Acc(var total: Double = 0.0, var count: Int = 0)
        val accByCustomer = HashMap<String, Acc>()
        for (order in orders) {
            if (order.updatedAt !in windowStart until windowEnd) continue
            val collected = (order.totalPrice - order.balanceRemaining).coerceAtLeast(0.0)
            val acc = accByCustomer.getOrPut(order.customerId) { Acc() }
            acc.total += collected
            acc.count += 1
        }

        return accByCustomer
            .asSequence()
            .mapNotNull { (customerId, acc) ->
                // Drop customers who placed orders this period but haven't paid anything yet.
                // The list is "top earners", not "everyone who showed up".
                if (acc.total <= 0.0) return@mapNotNull null
                val customer = customersById[customerId] ?: return@mapNotNull null
                // No phone filter here. Reports is analytical and tap navigates to
                // CustomerDetail — no WhatsApp launch — so a missing phone shouldn't
                // hide a top earner. Otherwise the hero revenue total and the list
                // would silently disagree.
                CustomerRanking(
                    customerId = customer.id,
                    customerName = customer.name,
                    totalCollected = acc.total,
                    orderCount = acc.count
                )
            }
            .sortedWith(
                compareByDescending<CustomerRanking> { it.totalCollected }
                    .thenBy { it.customerName }
            )
            .take(limit)
            .toList()
    }

    fun debtors(
        orders: List<Order>,
        customers: List<Customer>,
        timeZone: TimeZone,
        limit: Int = DEFAULT_LIMIT
    ): List<DebtorEntry> {
        if (customers.isEmpty()) return emptyList()
        val customersById = customers.associateBy { it.id }

        data class Acc(var totalOwed: Double = 0.0, var oldestDeadlineMillis: Long? = null)
        val accByCustomer = HashMap<String, Acc>()
        orders
            .filter { it.status != OrderStatus.DELIVERED && it.balanceRemaining > 0.0 }
            .forEach { order ->
                val acc = accByCustomer.getOrPut(order.customerId) { Acc() }
                acc.totalOwed += order.balanceRemaining
                val deadline = order.deadline
                if (deadline != null) {
                    val previous = acc.oldestDeadlineMillis
                    if (previous == null || deadline < previous) {
                        acc.oldestDeadlineMillis = deadline
                    }
                }
            }

        return accByCustomer
            .asSequence()
            .mapNotNull { (customerId, acc) ->
                val customer = customersById[customerId] ?: return@mapNotNull null
                DebtorEntry(
                    customerId = customer.id,
                    customerName = customer.name,
                    totalOwed = acc.totalOwed,
                    oldestDeadline = acc.oldestDeadlineMillis?.let { millis ->
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
                    }
                )
            }
            .sortedWith(
                compareByDescending<DebtorEntry> { it.totalOwed }
                    .thenBy { it.customerName }
            )
            .take(limit)
            .toList()
    }
}

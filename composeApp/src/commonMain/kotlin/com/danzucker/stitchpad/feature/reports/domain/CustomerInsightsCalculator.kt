package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.reports.domain.model.CappedList
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerBadge
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DEFAULT_LIMIT = 5

// Hybrid VIP / Repeat thresholds — defaults locked for V2.
// VIP if lifetime orders >= 5 OR lifetime spend >= 500_000.
// Repeat if lifetime orders >= 2 (and not VIP).
// Future: a Settings screen will let each tailor configure their own
// thresholds (different price ranges across cities/markets); these
// constants will become the seed values for that.
private const val VIP_ORDER_COUNT = 5
private const val VIP_LIFETIME_SPEND = 500_000.0
private const val REPEAT_ORDER_COUNT = 2

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
        limit: Int = DEFAULT_LIMIT,
        customRange: CustomRange? = null
    ): CappedList<CustomerRanking> {
        if (customers.isEmpty()) return CappedList.empty()
        val (windowStart, windowEnd) = reportsWindow(period, today, timeZone, 0, customRange)
        val customersById = customers.associateBy { it.id }

        // Lifetime stats drive the VIP / Repeat badge — badges describe the
        // overall customer relationship, not a single window. Period totals
        // still drive the ranking sort order.
        data class Lifetime(var orders: Int = 0, var collected: Double = 0.0)
        val lifetimeByCustomer = HashMap<String, Lifetime>()
        for (order in orders) {
            val l = lifetimeByCustomer.getOrPut(order.customerId) { Lifetime() }
            l.orders += 1
            l.collected += order.depositPaid
        }

        data class Acc(var total: Double = 0.0, var count: Int = 0)
        val accByCustomer = HashMap<String, Acc>()
        for (order in orders) {
            if (order.updatedAt !in windowStart until windowEnd) continue
            val collected = order.depositPaid
            val acc = accByCustomer.getOrPut(order.customerId) { Acc() }
            acc.total += collected
            acc.count += 1
        }

        // Realise the full ranked list once so totalCount reflects every eligible
        // customer (post-filter, pre-cap). The card uses totalCount to decide
        // whether "View all" is meaningful and what overflow count to show.
        val ranked = accByCustomer
            .asSequence()
            .mapNotNull { (customerId, acc) ->
                // Drop customers who placed orders this period but haven't paid anything yet.
                // The list is "top earners", not "everyone who showed up".
                if (acc.total <= 0.0) return@mapNotNull null
                val customer = customersById[customerId] ?: return@mapNotNull null
                val lifetime = lifetimeByCustomer[customerId] ?: Lifetime()
                CustomerRanking(
                    customerId = customer.id,
                    customerName = customer.name,
                    totalCollected = acc.total,
                    orderCount = acc.count,
                    badge = badgeFor(lifetime.orders, lifetime.collected)
                )
            }
            .sortedWith(
                compareByDescending<CustomerRanking> { it.totalCollected }
                    .thenBy { it.customerName }
            )
            .toList()

        return CappedList(items = ranked.take(limit), totalCount = ranked.size)
    }

    private fun badgeFor(lifetimeOrders: Int, lifetimeSpend: Double): CustomerBadge = when {
        lifetimeOrders >= VIP_ORDER_COUNT || lifetimeSpend >= VIP_LIFETIME_SPEND -> CustomerBadge.VIP
        lifetimeOrders >= REPEAT_ORDER_COUNT -> CustomerBadge.REPEAT
        else -> CustomerBadge.NONE
    }

    fun debtors(
        orders: List<Order>,
        customers: List<Customer>,
        timeZone: TimeZone,
        limit: Int = DEFAULT_LIMIT
    ): CappedList<DebtorEntry> {
        if (customers.isEmpty()) return CappedList.empty()
        val customersById = customers.associateBy { it.id }

        data class Acc(
            var totalOwed: Double = 0.0,
            var orderCount: Int = 0,
            var oldestDeadlineMillis: Long? = null
        )
        val accByCustomer = HashMap<String, Acc>()
        orders
            .filter { it.status != OrderStatus.DELIVERED && it.balanceRemaining > 0.0 }
            .forEach { order ->
                val acc = accByCustomer.getOrPut(order.customerId) { Acc() }
                acc.totalOwed += order.balanceRemaining
                acc.orderCount += 1
                val deadline = order.deadline
                if (deadline != null) {
                    val previous = acc.oldestDeadlineMillis
                    if (previous == null || deadline < previous) {
                        acc.oldestDeadlineMillis = deadline
                    }
                }
            }

        val ranked = accByCustomer
            .asSequence()
            .mapNotNull { (customerId, acc) ->
                val customer = customersById[customerId] ?: return@mapNotNull null
                DebtorEntry(
                    customerId = customer.id,
                    customerName = customer.name,
                    totalOwed = acc.totalOwed,
                    orderCount = acc.orderCount,
                    oldestDeadline = acc.oldestDeadlineMillis?.let { millis ->
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
                    },
                    canSendWhatsAppReminder = customer.phone.isNotBlank()
                )
            }
            .sortedWith(
                compareByDescending<DebtorEntry> { it.totalOwed }
                    .thenBy { it.customerName }
            )
            .toList()

        return CappedList(items = ranked.take(limit), totalCount = ranked.size)
    }
}

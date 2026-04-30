package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomerInsightsCalculatorTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)
    private val priorWeek = LocalDate(2026, 4, 14)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun customer(id: String, name: String, phone: String = "+2348012345678") = Customer(
        id = id,
        userId = "u",
        name = name,
        phone = phone
    )

    private fun order(
        id: String = "o",
        customerId: String = "c1",
        updatedAt: Long = millisAt(today),
        totalPrice: Double = 10_000.0,
        balanceRemaining: Double = 0.0,
        status: OrderStatus = OrderStatus.PENDING,
        deadline: Long? = null
    ): Order = Order(
        id = id,
        userId = "u",
        customerId = customerId,
        customerName = "Test",
        items = listOf(
            OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = totalPrice)
        ),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = totalPrice,
        depositPaid = totalPrice - balanceRemaining,
        balanceRemaining = balanceRemaining,
        deadline = deadline,
        notes = null,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    // -------- topCustomers --------

    @Test
    fun topCustomersEmptyInputGivesEmpty() {
        val result = CustomerInsightsCalculator.topCustomers(
            orders = emptyList(),
            customers = emptyList(),
            period = ReportsPeriod.WEEK,
            today = today,
            timeZone = tz
        )
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun topCustomersSortedDescByTotal() {
        val customers = listOf(
            customer("c1", "Adaeze"),
            customer("c2", "Bola"),
            customer("c3", "Chiamaka")
        )
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(id = "o2", customerId = "c2", totalPrice = 50_000.0, balanceRemaining = 0.0),
            order(id = "o3", customerId = "c3", totalPrice = 20_000.0, balanceRemaining = 0.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(listOf("Bola", "Chiamaka", "Adaeze"), result.items.map { it.customerName })
    }

    @Test
    fun topCustomersAggregatesMultipleOrdersPerCustomer() {
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(id = "o2", customerId = "c1", totalPrice = 15_000.0, balanceRemaining = 0.0),
            order(id = "o3", customerId = "c1", totalPrice = 20_000.0, balanceRemaining = 5_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(1, result.items.size)
        assertEquals(40_000.0, result.items[0].totalCollected)
        assertEquals(3, result.items[0].orderCount)
    }

    @Test
    fun topCustomersRespectsPeriodWindow() {
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = listOf(
            order(id = "in", customerId = "c1", updatedAt = millisAt(today), totalPrice = 5_000.0),
            order(id = "out", customerId = "c1", updatedAt = millisAt(priorWeek), totalPrice = 50_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(1, result.items.size)
        assertEquals(5_000.0, result.items[0].totalCollected)
        assertEquals(1, result.items[0].orderCount)
    }

    @Test
    fun topCustomersIncludesBlankPhoneCustomers() {
        // Reports is analytical; clicks navigate to CustomerDetail, not WhatsApp.
        // A top earner without a phone must still appear, otherwise the hero
        // revenue total and the list would disagree silently.
        val customers = listOf(
            customer("c1", "WithPhone", phone = "+2348012345678"),
            customer("c2", "NoPhone", phone = "")
        )
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0),
            order(id = "o2", customerId = "c2", totalPrice = 99_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(listOf("NoPhone", "WithPhone"), result.items.map { it.customerName })
    }

    @Test
    fun topCustomersTiedTotalsStableAlphabetical() {
        val customers = listOf(
            customer("c1", "Zoe"),
            customer("c2", "Adaeze"),
            customer("c3", "Mary")
        )
        val sameAmount = 10_000.0
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = sameAmount),
            order(id = "o2", customerId = "c2", totalPrice = sameAmount),
            order(id = "o3", customerId = "c3", totalPrice = sameAmount)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(listOf("Adaeze", "Mary", "Zoe"), result.items.map { it.customerName })
    }

    @Test
    fun topCustomersExcludesCustomersWithZeroCollected() {
        // Customer placed an order this week but hasn't paid yet — shouldn't appear in
        // top earners. They'll still show up in debtors.
        val customers = listOf(
            customer("c1", "PaidUp"),
            customer("c2", "OnlyDebt")
        )
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(id = "o2", customerId = "c2", totalPrice = 50_000.0, balanceRemaining = 50_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(listOf("PaidUp"), result.items.map { it.customerName })
    }

    @Test
    fun topCustomersCustomRangeWindowExcludesOrdersOutsideRange() {
        // Sanity-check that ReportsPeriod.CUSTOM plumbs through to the window helper.
        val customers = listOf(customer("c1", "Adaeze"))
        val range = com.danzucker.stitchpad.feature.reports.domain.model.CustomRange(
            start = LocalDate(2026, 4, 1),
            end = LocalDate(2026, 4, 30)
        )
        val orders = listOf(
            order(id = "in", customerId = "c1", updatedAt = millisAt(LocalDate(2026, 4, 15)),
                totalPrice = 10_000.0),
            order(id = "out", customerId = "c1", updatedAt = millisAt(LocalDate(2026, 5, 5)),
                totalPrice = 999_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.CUSTOM, today, tz, customRange = range
        )
        assertEquals(1, result.items.size)
        assertEquals(10_000.0, result.items[0].totalCollected)
    }

    @Test
    fun topCustomersBadgeVipFromLifetimeOrderCount() {
        // 5+ lifetime orders → VIP, regardless of spend.
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = (1..5).map { i ->
            order(id = "o$i", customerId = "c1", totalPrice = 10_000.0)
        }
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(CustomerBadge.VIP, result.items.single().badge)
    }

    @Test
    fun topCustomersBadgeVipFromLifetimeSpend() {
        // Single big order pushing >= ₦500k → VIP via spend rule.
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 600_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(CustomerBadge.VIP, result.items.single().badge)
    }

    @Test
    fun topCustomersBadgeRepeatBelowVipThresholds() {
        // 2-4 lifetime orders, < ₦500k spend → Repeat.
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = (1..3).map { i ->
            order(id = "o$i", customerId = "c1", totalPrice = 10_000.0)
        }
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(CustomerBadge.REPEAT, result.items.single().badge)
    }

    @Test
    fun topCustomersBadgeNoneBelowAllThresholds() {
        // 1 order, modest spend → no badge.
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 5_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(CustomerBadge.NONE, result.items.single().badge)
    }

    @Test
    fun topCustomersRespectsLimit() {
        val customers = (1..10).map { customer("c$it", "Customer$it") }
        val orders = (1..10).map {
            order(id = "o$it", customerId = "c$it", totalPrice = (it * 1_000.0))
        }
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz, limit = 3
        )
        assertEquals(3, result.items.size)
        assertEquals(listOf("Customer10", "Customer9", "Customer8"), result.items.map { it.customerName })
    }

    @Test
    fun topCustomersTotalCountReflectsAllEligibleEvenWhenCapped() {
        // 10 paying customers, capped at 3. totalCount must report all 10 — that's
        // what powers "View all (10)" on the Reports card.
        val customers = (1..10).map { customer("c$it", "Customer$it") }
        val orders = (1..10).map {
            order(id = "o$it", customerId = "c$it", totalPrice = (it * 1_000.0))
        }
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz, limit = 3
        )
        assertEquals(10, result.totalCount)
        assertTrue(result.hasMore)
    }

    @Test
    fun topCustomersHasMoreFalseWhenAllFitWithinLimit() {
        // 2 paying customers, default limit (5). Nothing hidden → hasMore == false,
        // so the card hides "View all" entirely.
        val customers = listOf(customer("c1", "Adaeze"), customer("c2", "Bola"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0),
            order(id = "o2", customerId = "c2", totalPrice = 20_000.0)
        )
        val result = CustomerInsightsCalculator.topCustomers(
            orders, customers, ReportsPeriod.WEEK, today, tz
        )
        assertEquals(2, result.totalCount)
        assertEquals(2, result.items.size)
        assertEquals(false, result.hasMore)
    }

    // -------- debtors --------

    @Test
    fun debtorsEmptyWhenNoUnpaidActiveOrders() {
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(
                id = "o2", customerId = "c1", totalPrice = 5_000.0,
                balanceRemaining = 5_000.0, status = OrderStatus.DELIVERED
            )
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun debtorsAggregatesPerCustomer() {
        val customers = listOf(customer("c1", "Bola"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 30_000.0, balanceRemaining = 20_000.0),
            order(id = "o2", customerId = "c1", totalPrice = 50_000.0, balanceRemaining = 25_000.0),
            order(
                id = "o3", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 10_000.0,
                status = OrderStatus.DELIVERED  // excluded
            )
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertEquals(1, result.items.size)
        assertEquals(45_000.0, result.items[0].totalOwed)
        // Counts only the unpaid, non-delivered orders.
        assertEquals(2, result.items[0].orderCount)
    }

    @Test
    fun debtorsOrderCountReflectsUnpaidActiveOrdersOnly() {
        val customers = listOf(customer("c1", "Bola"))
        val orders = listOf(
            // Unpaid, active — counted
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 5_000.0),
            // Fully paid, active — excluded (balance == 0)
            order(id = "o2", customerId = "c1", totalPrice = 20_000.0, balanceRemaining = 0.0),
            // Unpaid but delivered — excluded
            order(
                id = "o3", customerId = "c1", totalPrice = 8_000.0, balanceRemaining = 8_000.0,
                status = OrderStatus.DELIVERED
            )
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertEquals(1, result.items.size)
        assertEquals(1, result.items[0].orderCount)
    }

    @Test
    fun debtorsSortedDescByOwed() {
        val customers = listOf(
            customer("c1", "Adaeze"),
            customer("c2", "Bola"),
            customer("c3", "Chiamaka")
        )
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 30_000.0, balanceRemaining = 30_000.0),
            order(id = "o2", customerId = "c2", totalPrice = 50_000.0, balanceRemaining = 50_000.0),
            order(id = "o3", customerId = "c3", totalPrice = 10_000.0, balanceRemaining = 10_000.0)
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertEquals(listOf("Bola", "Adaeze", "Chiamaka"), result.items.map { it.customerName })
    }

    @Test
    fun debtorsTracksOldestDeadline() {
        val customers = listOf(customer("c1", "Bola"))
        val olderDeadline = millisAt(LocalDate(2026, 3, 1))
        val newerDeadline = millisAt(LocalDate(2026, 4, 5))
        val orders = listOf(
            order(
                id = "o1", customerId = "c1",
                totalPrice = 10_000.0, balanceRemaining = 10_000.0, deadline = newerDeadline
            ),
            order(
                id = "o2", customerId = "c1",
                totalPrice = 20_000.0, balanceRemaining = 20_000.0, deadline = olderDeadline
            )
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertEquals(1, result.items.size)
        assertEquals(LocalDate(2026, 3, 1), result.items[0].oldestDeadline)
    }

    @Test
    fun debtorsHandlesNullDeadlines() {
        val customers = listOf(customer("c1", "Bola"))
        val orders = listOf(
            order(
                id = "o1", customerId = "c1",
                totalPrice = 10_000.0, balanceRemaining = 10_000.0, deadline = null
            )
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertEquals(1, result.items.size)
        assertNull(result.items[0].oldestDeadline)
    }

    @Test
    fun debtorsExcludesZeroBalanceOrders() {
        val customers = listOf(customer("c1", "Bola"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 0.0)
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun debtorsIgnoresUnknownCustomerIds() {
        val customers = listOf(customer("c1", "Bola"))
        val orders = listOf(
            order(id = "o1", customerId = "ghost", totalPrice = 99_000.0, balanceRemaining = 99_000.0)
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun debtorsTotalCountReflectsAllOwingEvenWhenCapped() {
        // 8 debtors, capped at 3. totalCount must surface 8 so the card can
        // render "View all (8)" instead of a misleading bare "View all".
        val customers = (1..8).map { customer("c$it", "Debtor$it") }
        val orders = (1..8).map {
            order(
                id = "o$it",
                customerId = "c$it",
                totalPrice = (it * 1_000.0),
                balanceRemaining = (it * 1_000.0)
            )
        }
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz, limit = 3)
        assertEquals(3, result.items.size)
        assertEquals(8, result.totalCount)
        assertTrue(result.hasMore)
    }

    @Test
    fun debtorsHasMoreFalseWhenAllFitWithinLimit() {
        val customers = listOf(customer("c1", "Adaeze"), customer("c2", "Bola"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 10_000.0, balanceRemaining = 5_000.0),
            order(id = "o2", customerId = "c2", totalPrice = 20_000.0, balanceRemaining = 20_000.0)
        )
        val result = CustomerInsightsCalculator.debtors(orders, customers, tz)
        assertEquals(2, result.totalCount)
        assertEquals(2, result.items.size)
        assertEquals(false, result.hasMore)
    }
}

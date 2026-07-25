package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KpiCalculatorTest {

    private fun depositPayment(amount: Double, recordedAt: Long = 0L): Payment = Payment(
        id = "test-deposit",
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = recordedAt,
    )

    private val tz = TimeZone.UTC

    // 2026-04-22 is a Wednesday — current week is Mon 2026-04-20 to Mon 2026-04-27.
    private val today = LocalDate(2026, 4, 22)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun order(
        id: String = "o",
        updatedAt: Long,
        totalPrice: Double = 0.0,
        balanceRemaining: Double = 0.0,
        status: OrderStatus = OrderStatus.PENDING,
        costs: List<OrderCost> = emptyList()
    ): Order {
        val depositAmount = (totalPrice - balanceRemaining).coerceAtLeast(0.0)
        return Order(
            id = id,
            userId = "u",
            customerId = "c1",
            customerName = "Test",
            items = listOf(
                OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = totalPrice)
            ),
            status = status,
            priority = OrderPriority.NORMAL,
            statusHistory = emptyList(),
            totalPrice = totalPrice,
            payments = if (depositAmount > 0.0) listOf(depositPayment(depositAmount)) else emptyList(),
            costs = costs,
            deadline = null,
            notes = null,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )
    }

    @Test
    fun emptyOrdersGivesZeroedKpis() {
        val summary = KpiCalculator.computeSummary(
            orders = emptyList(),
            period = ReportsPeriod.WEEK,
            today = today,
            timeZone = tz
        )
        assertEquals(0.0, summary.revenue.current)
        assertEquals(0.0, summary.collected.current)
        assertEquals(0.0, summary.outstanding.current)
        assertEquals(0.0, summary.orders.current)
        assertNull(summary.revenue.deltaPercent)
        assertEquals(8, summary.revenue.sparkline.size)
    }

    @Test
    fun revenueIsTotalPriceCollectedIsPaidPortion() {
        val orders = listOf(
            // Fully paid 10k
            order(id = "a", updatedAt = millisAt(today), totalPrice = 10_000.0, balanceRemaining = 0.0),
            // 30k order, 10k paid → 20k collected, 10k outstanding
            order(id = "b", updatedAt = millisAt(today), totalPrice = 30_000.0, balanceRemaining = 10_000.0)
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(40_000.0, summary.revenue.current)
        assertEquals(30_000.0, summary.collected.current)
        assertEquals(10_000.0, summary.outstanding.current)
        assertEquals(2.0, summary.orders.current)
    }

    @Test
    fun monthSparklineHasSixBucketsAndLastEqualsCurrent() {
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 50_000.0, balanceRemaining = 0.0)
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.MONTH, today, tz)
        assertEquals(6, summary.revenue.sparkline.size)
        assertEquals(summary.revenue.current, summary.revenue.sparkline.last())
    }

    @Test
    fun customRangeUsesGivenWindowAndEmptySparkline() {
        val range = CustomRange(LocalDate(2026, 4, 10), LocalDate(2026, 4, 20))
        val orders = listOf(
            order(id = "in", updatedAt = millisAt(LocalDate(2026, 4, 15)),
                totalPrice = 60_000.0, balanceRemaining = 0.0),
            order(id = "out", updatedAt = millisAt(LocalDate(2026, 4, 25)),
                totalPrice = 99_000.0, balanceRemaining = 0.0)
        )
        val summary = KpiCalculator.computeSummary(
            orders, ReportsPeriod.CUSTOM, today, tz, customRange = range
        )
        assertEquals(60_000.0, summary.revenue.current)
        assertTrue(summary.revenue.sparkline.isEmpty())
    }

    @Test
    fun previousPeriodIsCountedSeparately() {
        val priorWeek = LocalDate(2026, 4, 14)
        val orders = listOf(
            order(id = "p", updatedAt = millisAt(priorWeek), totalPrice = 100_000.0, balanceRemaining = 0.0),
            order(id = "c", updatedAt = millisAt(today), totalPrice = 120_000.0, balanceRemaining = 0.0)
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(120_000.0, summary.revenue.current)
        assertEquals(100_000.0, summary.revenue.previous)
        // 20% growth
        assertEquals(20.0, summary.revenue.deltaPercent!!, absoluteTolerance = 0.01)
    }

    @Test
    fun zeroPaymentsMeansZeroCollectedAndFullOutstanding() {
        // Order with no payments: collected = 0, outstanding = totalPrice.
        // The old "balance > totalPrice" pathological case can no longer occur because
        // balanceRemaining is now computed as (totalPrice - depositPaid).coerceAtLeast(0).
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 5_000.0, balanceRemaining = 5_000.0)
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(0.0, summary.collected.current)
        assertEquals(5_000.0, summary.outstanding.current)
    }

    @Test
    fun `discount reduces revenue and never inflates collected`() {
        // totalPrice=10_000, discount=2_000 → payableTotal=8_000
        // deposit paid=2_000 → balanceRemaining=6_000
        // Expected: revenue=8_000 (net), collected=2_000 (cash only), outstanding=6_000
        val discountedOrder = Order(
            id = "disc",
            userId = "u",
            customerId = "c1",
            customerName = "Test",
            items = listOf(
                OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = 10_000.0)
            ),
            status = OrderStatus.PENDING,
            priority = OrderPriority.NORMAL,
            statusHistory = emptyList(),
            totalPrice = 10_000.0,
            discount = 2_000.0,
            payments = listOf(depositPayment(amount = 2_000.0)),
            deadline = null,
            notes = null,
            createdAt = millisAt(today),
            updatedAt = millisAt(today),
        )
        val summary = KpiCalculator.computeSummary(
            orders = listOf(discountedOrder),
            period = ReportsPeriod.WEEK,
            today = today,
            timeZone = tz
        )
        assertEquals(8_000.0, summary.revenue.current)
        assertEquals(2_000.0, summary.collected.current)
        assertEquals(6_000.0, summary.outstanding.current)
    }

    @Test
    fun `profit sums only orders with costs in window`() {
        val orders = listOf(
            // 50k order, 44k fabric cost → profit 6k. Costed.
            order(
                id = "a",
                updatedAt = millisAt(today),
                totalPrice = 50_000.0,
                balanceRemaining = 0.0,
                costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0))
            ),
            // 30k order, no costs recorded. Excluded from profit + coverage numerator.
            order(
                id = "b",
                updatedAt = millisAt(today),
                totalPrice = 30_000.0,
                balanceRemaining = 0.0
            )
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.MONTH, today, tz)
        assertEquals(6_000.0, summary.profit.current)
        assertEquals(1, summary.ordersWithCosts)
        assertEquals(2, summary.ordersInWindow)
        assertEquals(6_000.0 / 50_000.0 * 100.0, summary.profitMarginPercent)
    }

    @Test
    fun `profit margin is null when no costed orders in window`() {
        val orders = listOf(
            order(
                id = "a",
                updatedAt = millisAt(today),
                totalPrice = 30_000.0,
                balanceRemaining = 0.0
            )
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.MONTH, today, tz)
        assertEquals(0.0, summary.profit.current)
        assertEquals(0, summary.ordersWithCosts)
        assertNull(summary.profitMarginPercent)
    }
}

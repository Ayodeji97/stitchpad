package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
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
        status: OrderStatus = OrderStatus.PENDING
    ): Order = Order(
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
        depositPaid = totalPrice - balanceRemaining,
        balanceRemaining = balanceRemaining,
        deadline = null,
        notes = null,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

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
    fun negativeBalanceCoercedToZeroForCollectedAndOutstanding() {
        // Pathological: balance > totalPrice (refund/overpay) — collected & outstanding both clamp.
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 5_000.0, balanceRemaining = 8_000.0)
        )
        val summary = KpiCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(0.0, summary.collected.current)
        // balanceRemaining itself is positive, so outstanding still reflects it.
        assertEquals(8_000.0, summary.outstanding.current)
    }
}

package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevenueCalculatorTest {

    private val tz = TimeZone.UTC

    // 2026-04-22 is a Wednesday — current week is Mon 2026-04-20 to Mon 2026-04-27.
    private val today = LocalDate(2026, 4, 22)

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun order(
        id: String = "o1",
        updatedAt: Long,
        totalPrice: Double = 0.0,
        balanceRemaining: Double = 0.0,
        status: OrderStatus = OrderStatus.PENDING,
        customerId: String = "c1"
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
        deadline = null,
        notes = null,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    @Test
    fun emptyOrdersGivesZeroes() {
        val summary = RevenueCalculator.computeSummary(
            orders = emptyList(),
            period = ReportsPeriod.WEEK,
            today = today,
            timeZone = tz
        )
        assertEquals(0.0, summary.current)
        assertEquals(0.0, summary.previous)
        assertEquals(0.0, summary.deltaAmount)
        assertNull(summary.deltaPercent)
        assertEquals(8, summary.sparkline.size)
        assertTrue(summary.sparkline.all { it == 0.0 })
    }

    @Test
    fun singleOrderInCurrentWeekCountedInCurrent() {
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 20_000.0, balanceRemaining = 5_000.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(15_000.0, summary.current)
        assertEquals(0.0, summary.previous)
    }

    @Test
    fun orderInPreviousWeekCountedInPrevious() {
        val priorWeek = LocalDate(2026, 4, 14)  // Tuesday in week of Mon 2026-04-13
        val orders = listOf(
            order(updatedAt = millisAt(priorWeek), totalPrice = 30_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(0.0, summary.current)
        assertEquals(30_000.0, summary.previous)
        assertEquals(-30_000.0, summary.deltaAmount)
    }

    @Test
    fun orderInEarlierSparklineBucketIsCountedThereButNotInCurrentOrPrevious() {
        val threeWeeksAgo = LocalDate(2026, 4, 1)
        val orders = listOf(
            order(updatedAt = millisAt(threeWeeksAgo), totalPrice = 10_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(0.0, summary.current)
        assertEquals(0.0, summary.previous)
        // Sparkline includes 8 weeks back, so 3 weeks ago is in there.
        assertTrue(summary.sparkline.any { it == 10_000.0 })
    }

    @Test
    fun sparklineLengthMatchesPeriod() {
        val weekSummary = RevenueCalculator.computeSummary(
            orders = emptyList(),
            period = ReportsPeriod.WEEK,
            today = today,
            timeZone = tz
        )
        val monthSummary = RevenueCalculator.computeSummary(
            orders = emptyList(),
            period = ReportsPeriod.MONTH,
            today = today,
            timeZone = tz
        )
        assertEquals(8, weekSummary.sparkline.size)
        assertEquals(6, monthSummary.sparkline.size)
    }

    @Test
    fun sparklineLastBucketEqualsCurrent() {
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 50_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(summary.current, summary.sparkline.last())
    }

    @Test
    fun deltaPercentNullWhenPreviousIsZero() {
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 10_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertNull(summary.deltaPercent)
    }

    @Test
    fun deltaPercentComputedWhenPreviousIsNonZero() {
        val priorWeek = LocalDate(2026, 4, 14)
        val orders = listOf(
            order(id = "prev", updatedAt = millisAt(priorWeek), totalPrice = 100_000.0, balanceRemaining = 0.0),
            order(id = "curr", updatedAt = millisAt(today), totalPrice = 120_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(120_000.0, summary.current)
        assertEquals(100_000.0, summary.previous)
        val pct = summary.deltaPercent
        assertNotNull(pct)
        // 20% increase
        assertEquals(20.0, pct, absoluteTolerance = 0.01)
    }

    @Test
    fun monthWindowsSpanCalendarMonths() {
        val priorMonth = LocalDate(2026, 3, 15)
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 80_000.0, balanceRemaining = 0.0),
            order(id = "prev", updatedAt = millisAt(priorMonth), totalPrice = 60_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.MONTH, today, tz)
        assertEquals(80_000.0, summary.current)
        assertEquals(60_000.0, summary.previous)
    }

    @Test
    fun negativePaidAmountCoercedToZero() {
        // Pathological data: balanceRemaining > totalPrice (refund / overpayment edge).
        val orders = listOf(
            order(updatedAt = millisAt(today), totalPrice = 5_000.0, balanceRemaining = 8_000.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        assertEquals(0.0, summary.current)
    }

    @Test
    fun multipleOrdersAggregateCorrectly() {
        val orders = listOf(
            order(id = "a", updatedAt = millisAt(today), totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(id = "b", updatedAt = millisAt(today), totalPrice = 20_000.0, balanceRemaining = 5_000.0),
            order(id = "c", updatedAt = millisAt(today), totalPrice = 30_000.0, balanceRemaining = 30_000.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.WEEK, today, tz)
        // 10k + 15k + 0 = 25k
        assertEquals(25_000.0, summary.current)
    }

    // -------- Year window --------

    @Test
    fun yearSparklineHasTwelveBuckets() {
        val summary = RevenueCalculator.computeSummary(
            orders = emptyList(),
            period = ReportsPeriod.YEAR,
            today = today,
            timeZone = tz
        )
        assertEquals(12, summary.sparkline.size)
    }

    @Test
    fun yearWindowSpansCalendarYears() {
        val priorYear = LocalDate(2025, 6, 15)
        val orders = listOf(
            order(id = "curr", updatedAt = millisAt(today), totalPrice = 200_000.0, balanceRemaining = 0.0),
            order(id = "prev", updatedAt = millisAt(priorYear), totalPrice = 80_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.YEAR, today, tz)
        assertEquals(200_000.0, summary.current)
        assertEquals(80_000.0, summary.previous)
    }

    @Test
    fun yearOrderJustBeforeJanFirstFallsInPreviousYear() {
        // 2025-12-31 23:00 UTC must be classified as previous year, not current.
        val edge = LocalDateTime(LocalDate(2025, 12, 31), LocalTime(23, 0))
            .toInstant(tz).toEpochMilliseconds()
        val orders = listOf(
            order(id = "edge", updatedAt = edge, totalPrice = 50_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.computeSummary(orders, ReportsPeriod.YEAR, today, tz)
        assertEquals(0.0, summary.current)
        assertEquals(50_000.0, summary.previous)
    }

    // -------- allTimeSummary --------

    private fun customer(id: String, name: String) = Customer(
        id = id, userId = "u", name = name, phone = "+2348012345678"
    )

    @Test
    fun allTimeSummaryEmptyInputsGivesZeroes() {
        val summary = RevenueCalculator.allTimeSummary(emptyList(), emptyList())
        assertEquals(0.0, summary.totalCollected)
        assertEquals(0, summary.orderCount)
        assertNull(summary.topCustomerName)
        assertEquals(0.0, summary.topCustomerTotal)
    }

    @Test
    fun allTimeSummaryAggregatesAcrossAllOrders() {
        val customers = listOf(customer("c1", "Adaeze"), customer("c2", "Bola"))
        val orders = listOf(
            order(id = "o1", updatedAt = millisAt(today), totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(id = "o2", updatedAt = millisAt(today), totalPrice = 30_000.0, balanceRemaining = 10_000.0),
            // Old order outside any rolling window — still counted in lifetime totals.
            order(id = "o3", updatedAt = millisAt(LocalDate(2024, 1, 1)),
                totalPrice = 5_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.allTimeSummary(orders, customers)
        // 10k + 20k (paid portion of o2) + 5k = 35k
        assertEquals(35_000.0, summary.totalCollected)
        assertEquals(3, summary.orderCount)
    }

    @Test
    fun allTimeSummaryPicksHighestPayingCustomer() {
        val customers = listOf(customer("c1", "Adaeze"), customer("c2", "Bola"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", updatedAt = millisAt(today),
                totalPrice = 10_000.0, balanceRemaining = 0.0),
            order(id = "o2", customerId = "c2", updatedAt = millisAt(today),
                totalPrice = 30_000.0, balanceRemaining = 0.0),
            order(id = "o3", customerId = "c2", updatedAt = millisAt(today),
                totalPrice = 25_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.allTimeSummary(orders, customers)
        assertEquals("Bola", summary.topCustomerName)
        assertEquals(55_000.0, summary.topCustomerTotal)
    }

    @Test
    fun allTimeSummaryIgnoresOrdersFromUnknownCustomers() {
        val customers = listOf(customer("c1", "Adaeze"))
        val orders = listOf(
            order(id = "o1", customerId = "c1", updatedAt = millisAt(today),
                totalPrice = 10_000.0, balanceRemaining = 0.0),
            // Customer was deleted but their orders linger — total still counts but
            // they shouldn't surface as the top customer.
            order(id = "o2", customerId = "ghost", updatedAt = millisAt(today),
                totalPrice = 99_000.0, balanceRemaining = 0.0)
        )
        val summary = RevenueCalculator.allTimeSummary(orders, customers)
        assertEquals("Adaeze", summary.topCustomerName)
        assertEquals(10_000.0, summary.topCustomerTotal)
    }
}

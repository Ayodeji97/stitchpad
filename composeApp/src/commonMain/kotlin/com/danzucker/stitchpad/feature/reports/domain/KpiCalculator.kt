package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.Kpi
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

private const val SPARKLINE_WEEK_BUCKETS = 8
private const val SPARKLINE_MONTH_BUCKETS = 6

/**
 * Computes the KPI grid for Reports V2 in one pass per window.
 *
 * Definitions:
 *  - Revenue:     Σ payableTotal (net of discount)  (work earned, including unpaid)
 *  - Collected:   Σ depositPaid (cash paid)
 *  - Outstanding: Σ balanceRemaining.coerceAtLeast(0.0)                  (debt)
 *  - Orders:      count
 *  - Profit:      Σ profit for orders with recorded costs only (uncosted orders are
 *                 excluded from both the numerator and [KpiSummary.profitMarginPercent]'s
 *                 denominator; [KpiSummary.ordersWithCosts] / [KpiSummary.ordersInWindow]
 *                 expose the coverage so the UI can show "N of M orders costed").
 *
 * Orders are bucketed by `updatedAt` — same approximation Dashboard and the V1.1
 * RevenueCalculator use. Sparkline lengths match V1.1 (8 weeks / 6 months);
 * Custom returns an empty sparkline since there's no natural sub-bucketing.
 */
object KpiCalculator {

    fun computeSummary(
        orders: List<Order>,
        period: ReportsPeriod,
        today: LocalDate,
        timeZone: TimeZone,
        customRange: CustomRange? = null
    ): KpiSummary {
        val bucketCount = when (period) {
            ReportsPeriod.WEEK -> SPARKLINE_WEEK_BUCKETS
            ReportsPeriod.MONTH -> SPARKLINE_MONTH_BUCKETS
            ReportsPeriod.CUSTOM -> 0
        }

        val sparklines = computeSparklines(orders, period, today, timeZone, bucketCount, customRange)

        val (currentStart, currentEnd) = reportsWindow(period, today, timeZone, 0, customRange)
        val (previousStart, previousEnd) = reportsWindow(period, today, timeZone, 1, customRange)
        val current = accumulateWindow(orders, currentStart, currentEnd)
        val previous = accumulateWindow(orders, previousStart, previousEnd)

        val profitMarginPercent = if (current.costedRevenue > 0.0) {
            current.profit / current.costedRevenue * 100.0
        } else {
            null
        }

        return KpiSummary(
            revenue = kpi(current.revenue, previous.revenue, sparklines.revenue),
            collected = kpi(current.collected, previous.collected, sparklines.collected),
            outstanding = kpi(current.outstanding, previous.outstanding, sparklines.outstanding),
            orders = kpi(current.orders, previous.orders, sparklines.orders),
            profit = kpi(current.profit, previous.profit, sparklines.profit),
            ordersWithCosts = current.ordersWithCosts,
            ordersInWindow = current.orders.toInt(),
            profitMarginPercent = profitMarginPercent
        )
    }

    /** Per-bucket aggregates (oldest-to-newest); last entry = current window's total when non-empty. */
    private data class BucketTotals(
        val revenue: List<Double>,
        val collected: List<Double>,
        val outstanding: List<Double>,
        val orders: List<Double>,
        val profit: List<Double>
    )

    /** Totals for a single window, shared by both the sparkline pass and the current/previous pass. */
    private data class WindowTotals(
        val revenue: Double,
        val collected: Double,
        val outstanding: Double,
        val orders: Double,
        val profit: Double,
        val ordersWithCosts: Int,
        val costedRevenue: Double
    )

    private fun computeSparklines(
        orders: List<Order>,
        period: ReportsPeriod,
        today: LocalDate,
        timeZone: TimeZone,
        bucketCount: Int,
        customRange: CustomRange?
    ): BucketTotals {
        val revenueSpark = MutableList(bucketCount) { 0.0 }
        val collectedSpark = MutableList(bucketCount) { 0.0 }
        val outstandingSpark = MutableList(bucketCount) { 0.0 }
        val ordersSpark = MutableList(bucketCount) { 0.0 }
        val profitSpark = MutableList(bucketCount) { 0.0 }

        for (i in 0 until bucketCount) {
            val periodsBack = bucketCount - 1 - i
            val (start, end) = reportsWindow(period, today, timeZone, periodsBack, customRange)
            val totals = accumulateWindow(orders, start, end)
            revenueSpark[i] = totals.revenue
            collectedSpark[i] = totals.collected
            outstandingSpark[i] = totals.outstanding
            ordersSpark[i] = totals.orders
            profitSpark[i] = totals.profit
        }

        return BucketTotals(revenueSpark, collectedSpark, outstandingSpark, ordersSpark, profitSpark)
    }

    private fun accumulateWindow(orders: List<Order>, start: Long, end: Long): WindowTotals {
        var revenue = 0.0
        var collected = 0.0
        var outstanding = 0.0
        var count = 0.0
        var profit = 0.0
        var ordersWithCosts = 0
        var costedRevenue = 0.0

        for (order in orders) {
            if (order.updatedAt !in start until end) continue
            revenue += order.payableTotal
            collected += order.depositPaid
            outstanding += order.balanceRemaining.coerceAtLeast(0.0)
            count += 1.0
            if (order.hasCosts) {
                profit += order.profit
                ordersWithCosts += 1
                costedRevenue += order.payableTotal
            }
        }

        return WindowTotals(revenue, collected, outstanding, count, profit, ordersWithCosts, costedRevenue)
    }

    private fun kpi(current: Double, previous: Double, sparkline: List<Double>): Kpi = Kpi(
        current = current,
        previous = previous,
        deltaPercent = if (previous == 0.0) null else (current - previous) / previous * 100.0,
        sparkline = sparkline
    )
}

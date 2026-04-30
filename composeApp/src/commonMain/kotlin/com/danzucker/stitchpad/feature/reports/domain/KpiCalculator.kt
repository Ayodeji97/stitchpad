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
 * Computes the four-tile KPI grid for Reports V2 in one pass per window.
 *
 * Definitions:
 *  - Revenue:     Σ totalPrice         (work earned, including unpaid)
 *  - Collected:   Σ (totalPrice - balanceRemaining).coerceAtLeast(0.0)  (cash paid)
 *  - Outstanding: Σ balanceRemaining.coerceAtLeast(0.0)                  (debt)
 *  - Orders:      count
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

        // Per-bucket aggregates (oldest-to-newest); last entry = current when non-empty.
        val revenueSpark = MutableList(bucketCount) { 0.0 }
        val collectedSpark = MutableList(bucketCount) { 0.0 }
        val outstandingSpark = MutableList(bucketCount) { 0.0 }
        val ordersSpark = MutableList(bucketCount) { 0.0 }

        for (i in 0 until bucketCount) {
            val periodsBack = bucketCount - 1 - i
            val (start, end) = reportsWindow(period, today, timeZone, periodsBack, customRange)
            for (order in orders) {
                if (order.updatedAt !in start until end) continue
                revenueSpark[i] += order.totalPrice
                collectedSpark[i] += (order.totalPrice - order.balanceRemaining).coerceAtLeast(0.0)
                outstandingSpark[i] += order.balanceRemaining.coerceAtLeast(0.0)
                ordersSpark[i] += 1.0
            }
        }

        val (currentStart, currentEnd) = reportsWindow(period, today, timeZone, 0, customRange)
        val (previousStart, previousEnd) = reportsWindow(period, today, timeZone, 1, customRange)

        var currentRevenue = 0.0
        var previousRevenue = 0.0
        var currentCollected = 0.0
        var previousCollected = 0.0
        var currentOutstanding = 0.0
        var previousOutstanding = 0.0
        var currentOrders = 0.0
        var previousOrders = 0.0

        for (order in orders) {
            val collected = (order.totalPrice - order.balanceRemaining).coerceAtLeast(0.0)
            val outstanding = order.balanceRemaining.coerceAtLeast(0.0)
            if (order.updatedAt in currentStart until currentEnd) {
                currentRevenue += order.totalPrice
                currentCollected += collected
                currentOutstanding += outstanding
                currentOrders += 1.0
            }
            if (order.updatedAt in previousStart until previousEnd) {
                previousRevenue += order.totalPrice
                previousCollected += collected
                previousOutstanding += outstanding
                previousOrders += 1.0
            }
        }

        return KpiSummary(
            revenue = kpi(currentRevenue, previousRevenue, revenueSpark),
            collected = kpi(currentCollected, previousCollected, collectedSpark),
            outstanding = kpi(currentOutstanding, previousOutstanding, outstandingSpark),
            orders = kpi(currentOrders, previousOrders, ordersSpark)
        )
    }

    private fun kpi(current: Double, previous: Double, sparkline: List<Double>): Kpi = Kpi(
        current = current,
        previous = previous,
        deltaPercent = if (previous == 0.0) null else (current - previous) / previous * 100.0,
        sparkline = sparkline
    )
}

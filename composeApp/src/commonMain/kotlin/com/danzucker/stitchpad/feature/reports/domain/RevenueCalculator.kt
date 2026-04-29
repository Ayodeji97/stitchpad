package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.RevenueSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

private const val SPARKLINE_WEEK_BUCKETS = 8
private const val SPARKLINE_MONTH_BUCKETS = 6

/**
 * Computes revenue summaries for the Reports tab over Week / Month windows.
 *
 * "Cash collected" is approximated as `(totalPrice - balanceRemaining)` summed
 * across orders whose `updatedAt` falls in the window. Same approximation the
 * dashboard's WeeklyGoalsCard uses — known limitation: an order paid weeks ago
 * but `updatedAt`-touched today is counted in today's window. Acceptable until
 * a per-payment ledger lands; revisit then.
 */
object RevenueCalculator {

    fun computeSummary(
        orders: List<Order>,
        period: ReportsPeriod,
        today: LocalDate,
        timeZone: TimeZone
    ): RevenueSummary {
        val bucketCount = when (period) {
            ReportsPeriod.WEEK -> SPARKLINE_WEEK_BUCKETS
            ReportsPeriod.MONTH -> SPARKLINE_MONTH_BUCKETS
        }
        val sparkline = (0 until bucketCount).map { index ->
            // index 0 = oldest, index bucketCount-1 = current.
            val periodsBack = bucketCount - 1 - index
            val (start, end) = reportsWindow(period, today, timeZone, periodsBack)
            revenueBetween(orders, start, end)
        }
        val current = sparkline.last()
        val (prevStart, prevEnd) = reportsWindow(period, today, timeZone, periodsBack = 1)
        val previous = revenueBetween(orders, prevStart, prevEnd)
        val deltaAmount = current - previous
        val deltaPercent = if (previous == 0.0) null else (current - previous) / previous * 100.0

        return RevenueSummary(
            current = current,
            previous = previous,
            deltaAmount = deltaAmount,
            deltaPercent = deltaPercent,
            sparkline = sparkline
        )
    }

    private fun revenueBetween(orders: List<Order>, startMillis: Long, endMillis: Long): Double =
        orders
            .filter { it.updatedAt in startMillis until endMillis }
            .sumOf { (it.totalPrice - it.balanceRemaining).coerceAtLeast(0.0) }
}

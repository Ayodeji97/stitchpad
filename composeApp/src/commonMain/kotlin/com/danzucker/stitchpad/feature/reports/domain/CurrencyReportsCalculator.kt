package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.feature.reports.domain.model.CurrencyReports
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.Kpi
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Partition before every monetary calculation, including previous periods and sparklines. */
object CurrencyReportsCalculator {
    fun compute(
        orders: List<Order>,
        customers: List<Customer>,
        period: ReportsPeriod,
        today: LocalDate,
        timeZone: TimeZone,
        customRange: CustomRange? = null,
    ): List<CurrencyReports> = orders.groupBy { it.currency }
        .toList()
        .sortedBy { (currency, _) -> currency.code }
        .map { (currency, currencyOrders) ->
            CurrencyReports(
                currency = currency,
                kpiSummary = KpiCalculator.computeSummary(currencyOrders, period, today, timeZone, customRange),
                topCustomers = CustomerInsightsCalculator.topCustomers(
                    orders = currencyOrders,
                    customers = customers,
                    period = period,
                    today = today,
                    timeZone = timeZone,
                    customRange = customRange,
                    lifetimeOrders = orders,
                ),
                debtors = CustomerInsightsCalculator.debtors(currencyOrders, customers, timeZone),
            )
        }

    /** Counts can be combined across currencies; monetary KPI values never are. */
    fun orderCount(reports: List<CurrencyReports>): Kpi? {
        if (reports.isEmpty()) return null
        val counts = reports.map { it.kpiSummary.orders }
        val current = counts.sumOf { it.current }
        val previous = counts.sumOf { it.previous }
        val bucketCount = counts.maxOf { it.sparkline.size }
        return Kpi(
            current = current,
            previous = previous,
            deltaPercent = if (previous == 0.0) null else (current - previous) / previous * 100.0,
            sparkline = List(bucketCount) { i -> counts.sumOf { it.sparkline.getOrElse(i) { 0.0 } } },
        )
    }
}

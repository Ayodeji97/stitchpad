package com.danzucker.stitchpad.feature.reports.domain.model

/**
 * One KPI tile's worth of data for the Reports V2 grid.
 *
 * @property current     Value in the selected window.
 * @property previous    Value in the prior window of the same shape.
 * @property deltaPercent `null` when [previous] is 0.0, otherwise the % change.
 * @property sparkline   Per-bucket history. 8 entries for Week, 6 for Month, empty for Custom.
 *                       Last entry equals [current] when non-empty.
 */
data class Kpi(
    val current: Double,
    val previous: Double,
    val deltaPercent: Double?,
    val sparkline: List<Double>
) {
    val deltaAmount: Double get() = current - previous
}

/**
 * Aggregate of the KPI tiles shown on Reports V2 (Revenue, Collected,
 * Outstanding, Orders, Profit). Computed in a single pass over orders by [KpiCalculator].
 *
 * @property ordersWithCosts Count of current-window orders that have at least one recorded
 *                            cost line (i.e. [com.danzucker.stitchpad.core.domain.model.Order.hasCosts]).
 * @property ordersInWindow  Count of ALL current-window orders, costed or not — the coverage denominator.
 * @property profitMarginPercent [profit]'s current value as a % of the payableTotal of costed
 *                                orders only; `null` when [ordersWithCosts] is 0 (no meaningful %).
 */
data class KpiSummary(
    val revenue: Kpi,
    val collected: Kpi,
    val outstanding: Kpi,
    val orders: Kpi,
    val profit: Kpi,
    val ordersWithCosts: Int,
    val ordersInWindow: Int,
    val profitMarginPercent: Double?
)

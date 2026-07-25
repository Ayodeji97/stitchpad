package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.Kpi
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_kpi_collected
import stitchpad.composeapp.generated.resources.reports_kpi_orders
import stitchpad.composeapp.generated.resources.reports_kpi_outstanding
import stitchpad.composeapp.generated.resources.reports_kpi_profit
import stitchpad.composeapp.generated.resources.reports_kpi_revenue
import stitchpad.composeapp.generated.resources.reports_profit_coverage
import stitchpad.composeapp.generated.resources.reports_profit_empty
import kotlin.math.roundToInt

@Composable
fun KpiGrid(
    summary: KpiSummary,
    deltaSuffix: String,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    // PTSP-39 "Calm" direction: every KPI icon chip is indigo (the unifying
    // brand accent). Colour is reserved for meaning — the Outstanding value
    // renders red, and the delta chips stay green/red. Sparklines all use the
    // primary accent so the grid reads as one calm family (no orphan purple).
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer
    val iconBg = MaterialTheme.colorScheme.primaryContainer
    val spark = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_revenue),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconTint = iconTint,
                iconBackground = iconBg,
                kpi = summary.revenue,
                valueFormatter = ::formatNaira,
                deltaSuffix = deltaSuffix,
                periodLabel = periodLabel,
                sparklineColor = spark
            )
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_collected),
                icon = Icons.Default.AccountBalanceWallet,
                iconTint = iconTint,
                iconBackground = iconBg,
                kpi = summary.collected,
                valueFormatter = ::formatNaira,
                deltaSuffix = deltaSuffix,
                periodLabel = periodLabel,
                sparklineColor = spark
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_outstanding),
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                iconTint = iconTint,
                iconBackground = iconBg,
                kpi = summary.outstanding,
                valueFormatter = ::formatNaira,
                deltaSuffix = deltaSuffix,
                periodLabel = periodLabel,
                sparklineColor = spark,
                // Outstanding is money owed — render the value red so it reads as
                // the figure that needs action.
                valueColor = DesignTokens.error500,
                // Outstanding rising = bad: a + delta should render red.
                invertDeltaSign = true
            )
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_orders),
                icon = Icons.Default.ShoppingBag,
                iconTint = iconTint,
                iconBackground = iconBg,
                kpi = summary.orders,
                valueFormatter = ::formatCount,
                deltaSuffix = deltaSuffix,
                periodLabel = periodLabel,
                sparklineColor = spark
            )
        }
        // Profit spans the full grid width — it carries a coverage caption the
        // fixed-shape tiles above never need, so it doesn't share their 2-up row.
        val profitMarginSuffix = summary.profitMarginPercent?.let { " · margin ${it.roundToInt()}%" }.orEmpty()
        ProfitKpiTile(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.reports_kpi_profit) + profitMarginSuffix,
            icon = Icons.Default.Payments,
            iconTint = iconTint,
            iconBackground = iconBg,
            kpi = summary.profit,
            isCoverageEmpty = summary.ordersWithCosts == 0,
            coverageCaption = stringResource(
                Res.string.reports_profit_coverage,
                summary.ordersWithCosts,
                summary.ordersInWindow
            ),
            emptyStateText = stringResource(Res.string.reports_profit_empty),
            deltaSuffix = deltaSuffix,
            periodLabel = periodLabel,
            sparklineColor = spark
        )
    }
}

private fun formatNaira(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000.0 -> "₦${shorten(value, 1_000_000.0)}M"
        abs >= 1_000.0 -> "₦${shorten(value, 1_000.0)}K"
        else -> "₦" + formatPrice(value)
    }
}

private fun shorten(value: Double, divisor: Double): String {
    val scaled = value / divisor
    val rounded = (scaled * 10).roundToInt() / 10.0
    // Trim trailing .0 for cleaner display: 1.0M -> 1M, 1.5M -> 1.5M
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

private fun formatCount(value: Double): String = value.roundToInt().toString()

// --------------- Previews ---------------

private val previewGridKpi = Kpi(
    current = 5_360_000.0,
    previous = 4_770_000.0,
    deltaPercent = 12.4,
    sparkline = listOf(
        2_100_000.0,
        3_400_000.0,
        2_800_000.0,
        4_200_000.0,
        3_900_000.0,
        4_770_000.0,
        5_100_000.0,
        5_360_000.0
    )
)

private val previewGridProfitKpi = Kpi(
    current = 2_010_000.0,
    previous = 1_900_000.0,
    deltaPercent = 5.8,
    sparkline = listOf(
        800_000.0,
        1_300_000.0,
        1_050_000.0,
        1_600_000.0,
        1_450_000.0,
        1_750_000.0,
        1_900_000.0,
        2_010_000.0
    )
)

private val previewGridSummaryWithProfit = KpiSummary(
    revenue = previewGridKpi,
    collected = previewGridKpi.copy(current = 3_510_000.0, previous = 3_196_000.0, deltaPercent = 9.8),
    outstanding = previewGridKpi.copy(current = 1_850_000.0, previous = 1_752_000.0, deltaPercent = 5.6),
    orders = Kpi(
        current = 18.0,
        previous = 15.0,
        deltaPercent = 20.0,
        sparkline = listOf(5.0, 12.0, 8.0, 14.0, 10.0, 15.0, 16.0, 18.0)
    ),
    profit = previewGridProfitKpi,
    ordersWithCosts = 14,
    ordersInWindow = 18,
    profitMarginPercent = 37.5
)

// Zero-coverage case: orders exist in the window, but none has a recorded cost yet
// (a tailor who hasn't touched the new costing UI at all). Profit and margin must
// read as "unknown", never as a false "₦0".
private val previewGridSummaryEmptyProfit = previewGridSummaryWithProfit.copy(
    profit = Kpi(current = 0.0, previous = 0.0, deltaPercent = null, sparkline = emptyList()),
    ordersWithCosts = 0,
    profitMarginPercent = null
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun KpiGridWithProfitLightPreview() {
    StitchPadTheme {
        KpiGrid(summary = previewGridSummaryWithProfit, deltaSuffix = "vs last week", periodLabel = "This week")
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun KpiGridWithProfitDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        KpiGrid(summary = previewGridSummaryWithProfit, deltaSuffix = "vs last week", periodLabel = "This week")
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun KpiGridProfitEmptyLightPreview() {
    StitchPadTheme {
        KpiGrid(summary = previewGridSummaryEmptyProfit, deltaSuffix = "vs last week", periodLabel = "This week")
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun KpiGridProfitEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        KpiGrid(summary = previewGridSummaryEmptyProfit, deltaSuffix = "vs last week", periodLabel = "This week")
    }
}

package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_kpi_collected
import stitchpad.composeapp.generated.resources.reports_kpi_orders
import stitchpad.composeapp.generated.resources.reports_kpi_outstanding
import stitchpad.composeapp.generated.resources.reports_kpi_revenue
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

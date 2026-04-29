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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier
) {
    val purple = Color(0xFF7B4DB5)
    val purpleBg = Color(0x227B4DB5)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_revenue),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconTint = DesignTokens.primary500,
                iconBackground = DesignTokens.primary50,
                kpi = summary.revenue,
                valueFormatter = ::formatNaira,
                deltaSuffix = deltaSuffix,
                sparklineColor = DesignTokens.success500
            )
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_collected),
                icon = Icons.Default.AccountBalanceWallet,
                iconTint = DesignTokens.success500,
                iconBackground = DesignTokens.success50,
                kpi = summary.collected,
                valueFormatter = ::formatNaira,
                deltaSuffix = deltaSuffix,
                sparklineColor = DesignTokens.success500
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_outstanding),
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                iconTint = DesignTokens.error500,
                iconBackground = DesignTokens.error50,
                kpi = summary.outstanding,
                valueFormatter = ::formatNaira,
                deltaSuffix = deltaSuffix,
                sparklineColor = DesignTokens.error500,
                // Outstanding rising = bad: a + delta should render red.
                invertDeltaSign = true
            )
            KpiTile(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.reports_kpi_orders),
                icon = Icons.Default.ShoppingBag,
                iconTint = purple,
                iconBackground = purpleBg,
                kpi = summary.orders,
                valueFormatter = ::formatCount,
                deltaSuffix = deltaSuffix,
                sparklineColor = purple
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

package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.RevenueSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_month
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_week
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_year
import stitchpad.composeapp.generated.resources.reports_label_this_month
import stitchpad.composeapp.generated.resources.reports_label_this_week
import stitchpad.composeapp.generated.resources.reports_label_this_year

@Composable
fun RevenueHeroCard(
    summary: RevenueSummary,
    period: ReportsPeriod,
    modifier: Modifier = Modifier
) {
    val labelRes = when (period) {
        ReportsPeriod.WEEK -> Res.string.reports_label_this_week
        ReportsPeriod.MONTH -> Res.string.reports_label_this_month
        ReportsPeriod.YEAR -> Res.string.reports_label_this_year
    }
    val deltaSuffixRes = when (period) {
        ReportsPeriod.WEEK -> Res.string.reports_delta_vs_last_week
        ReportsPeriod.MONTH -> Res.string.reports_delta_vs_last_month
        ReportsPeriod.YEAR -> Res.string.reports_delta_vs_last_year
    }
    val mono = JetBrainsMonoFamily()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(DesignTokens.space1))
        Text(
            text = "₦" + formatPrice(summary.current),
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(DesignTokens.space1))
        DeltaLine(
            deltaAmount = summary.deltaAmount,
            suffix = stringResource(deltaSuffixRes)
        )
        Spacer(Modifier.height(DesignTokens.space3))
        Sparkline(
            values = summary.sparkline,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeltaLine(deltaAmount: Double, suffix: String) {
    val neutralColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
    val (arrow, color) = when {
        deltaAmount > 0 -> "▲" to DesignTokens.success500
        deltaAmount < 0 -> "▼" to DesignTokens.error500
        else -> "—" to neutralColor
    }
    val absText = if (deltaAmount == 0.0) "" else "₦" + formatPrice(kotlin.math.abs(deltaAmount)) + "  "
    Text(
        text = "$arrow $absText$suffix",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

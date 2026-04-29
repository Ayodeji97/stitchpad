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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.RevenueSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_delta_first_month
import stitchpad.composeapp.generated.resources.reports_delta_first_week
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_month
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_week
import stitchpad.composeapp.generated.resources.reports_label_this_month
import stitchpad.composeapp.generated.resources.reports_label_this_week

private const val MIN_NONZERO_BUCKETS_FOR_SPARKLINE = 2

@Composable
fun RevenueHeroCard(
    summary: RevenueSummary,
    period: ReportsPeriod,
    modifier: Modifier = Modifier
) {
    val labelRes = when (period) {
        ReportsPeriod.WEEK -> Res.string.reports_label_this_week
        ReportsPeriod.MONTH -> Res.string.reports_label_this_month
    }
    val deltaSuffixRes = when (period) {
        ReportsPeriod.WEEK -> Res.string.reports_delta_vs_last_week
        ReportsPeriod.MONTH -> Res.string.reports_delta_vs_last_month
    }
    val firstPeriodRes = when (period) {
        ReportsPeriod.WEEK -> Res.string.reports_delta_first_week
        ReportsPeriod.MONTH -> Res.string.reports_delta_first_month
    }
    val mono = JetBrainsMonoFamily()
    // First-period rule: when there's revenue this period but the prior period was
    // empty, the ▲ delta (e.g. "▲ ₦5M vs last week") reads as a brag from zero —
    // it's mathematically correct but visually misleading. Show neutral copy instead.
    val isFirstPeriod = summary.previous == 0.0 && summary.current > 0.0
    // Sparkline visibility: with all-zero or one-spike data the line renders as a
    // flat baseline with a tick at the end and looks broken. Only show it when at
    // least two buckets carry a real value, so the line tells an actual story.
    val showSparkline = summary.sparkline.count { it > 0.0 } >= MIN_NONZERO_BUCKETS_FOR_SPARKLINE

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
        if (isFirstPeriod) {
            FirstPeriodLine(text = stringResource(firstPeriodRes))
        } else {
            DeltaLine(
                deltaAmount = summary.deltaAmount,
                suffix = stringResource(deltaSuffixRes)
            )
        }
        if (showSparkline) {
            Spacer(Modifier.height(DesignTokens.space3))
            Sparkline(
                values = summary.sparkline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FirstPeriodLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.Kpi
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun KpiTile(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    kpi: Kpi,
    valueFormatter: (Double) -> String,
    deltaSuffix: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
    sparklineColor: Color = DesignTokens.success500,
    valueColor: Color? = null,
    invertDeltaSign: Boolean = false
) {
    val mono = JetBrainsMonoFamily()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = valueFormatter(kpi.current),
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.5).sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DeltaChip(
                kpi = kpi,
                suffix = deltaSuffix,
                periodLabel = periodLabel,
                invertSign = invertDeltaSign
            )
            if (kpi.sparkline.isNotEmpty()) {
                Spacer(Modifier.width(DesignTokens.space2))
                Sparkline(
                    values = kpi.sparkline,
                    color = sparklineColor,
                    height = 20.dp,
                    modifier = Modifier
                        .width(56.dp)
                        .height(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DeltaChip(kpi: Kpi, suffix: String, periodLabel: String, invertSign: Boolean) {
    val delta = kpi.deltaPercent
    // No prior-period data: show the period name (e.g. "This week") instead
    // of a dash placeholder. This is the first-launch state — the sparkline
    // still renders so the user gets a feel for movement within the period.
    if (delta == null) {
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val (arrow, color) = when {
        delta > 0.0 -> "▲" to deltaColor(positive = !invertSign)
        delta < 0.0 -> "▼" to deltaColor(positive = invertSign)
        else -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pctText = "${(abs(delta) * 10).roundToInt() / 10.0}%"
    Text(
        text = "$arrow $pctText  $suffix",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

@Composable
private fun deltaColor(positive: Boolean): Color =
    if (positive) DesignTokens.success500 else DesignTokens.error500

/**
 * Full-width Profit tile. Kept separate from [KpiTile] rather than bolting more optional
 * params onto it: Profit needs a coverage caption that always renders (not just on hover
 * of a tooltip) and an honest empty state that replaces the money value entirely — two
 * shapes the four fixed tiles never need. This keeps [KpiTile] itself, and the four
 * call sites in [KpiGrid], completely unchanged.
 *
 * @param isCoverageEmpty True when no order in the window has recorded costs yet
 *                        ([com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary.ordersWithCosts] == 0).
 *                        Swaps the money value for [emptyStateText] and hides the
 *                        delta/sparkline row — there's nothing meaningful to trend yet.
 * @param coverageCaption "On N of M orders with costs recorded" — always shown, so the
 *                        tailor can see partial coverage even once profit starts appearing.
 */
@Composable
fun ProfitKpiTile(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    kpi: Kpi,
    isCoverageEmpty: Boolean,
    coverageCaption: String,
    emptyStateText: String,
    deltaSuffix: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
    valueColor: Color = profitValueColor(),
    sparklineColor: Color = DesignTokens.success500
) {
    val mono = JetBrainsMonoFamily()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg)
            )
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isCoverageEmpty) {
            // No costed orders yet in this window: a "₦0" here would read as "you made
            // nothing", which is false — it just isn't recorded. Say that instead.
            Text(
                text = emptyStateText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "₦${formatPrice(kpi.current)}",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
                color = valueColor
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DeltaChip(
                    kpi = kpi,
                    suffix = deltaSuffix,
                    periodLabel = periodLabel,
                    invertSign = false
                )
                if (kpi.sparkline.isNotEmpty()) {
                    Spacer(Modifier.width(DesignTokens.space2))
                    Sparkline(
                        values = kpi.sparkline,
                        color = sparklineColor,
                        height = 20.dp,
                        modifier = Modifier
                            .width(56.dp)
                            .height(20.dp)
                    )
                }
            }
        }
        Text(
            text = coverageCaption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun profitValueColor(): Color =
    if (isSystemInDarkTheme()) DesignTokens.successDarkText else DesignTokens.success500

private val previewProfitSpark = listOf(
    800_000.0,
    1_300_000.0,
    1_050_000.0,
    1_600_000.0,
    1_450_000.0,
    1_750_000.0,
    1_900_000.0,
    2_010_000.0
)

private val previewProfitKpi = Kpi(
    current = 2_010_000.0,
    previous = 1_900_000.0,
    deltaPercent = 5.8,
    sparkline = previewProfitSpark
)

private val previewEmptyProfitKpi = Kpi(
    current = 0.0,
    previous = 0.0,
    deltaPercent = null,
    sparkline = emptyList()
)

@Composable
private fun ProfitKpiTilePreview(darkTheme: Boolean, isCoverageEmpty: Boolean) {
    StitchPadTheme(darkTheme = darkTheme) {
        ProfitKpiTile(
            label = if (isCoverageEmpty) "Profit" else "Profit · margin 38%",
            icon = Icons.Filled.Payments,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBackground = MaterialTheme.colorScheme.primaryContainer,
            kpi = if (isCoverageEmpty) previewEmptyProfitKpi else previewProfitKpi,
            isCoverageEmpty = isCoverageEmpty,
            coverageCaption = if (isCoverageEmpty) {
                "On 0 of 18 orders with costs recorded"
            } else {
                "On 14 of 18 orders with costs recorded"
            },
            emptyStateText = "Add costs to an order to see profit",
            deltaSuffix = "vs last week",
            periodLabel = "This week"
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfitKpiTileFilledLightPreview() {
    ProfitKpiTilePreview(darkTheme = false, isCoverageEmpty = false)
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfitKpiTileFilledDarkPreview() {
    ProfitKpiTilePreview(darkTheme = true, isCoverageEmpty = false)
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfitKpiTileEmptyLightPreview() {
    ProfitKpiTilePreview(darkTheme = false, isCoverageEmpty = true)
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfitKpiTileEmptyDarkPreview() {
    ProfitKpiTilePreview(darkTheme = true, isCoverageEmpty = true)
}

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
 * Direction of the Profit tile's trend chip, decided from the SIGNED amount change
 * ([Kpi.deltaAmount]) rather than [Kpi.deltaPercent].
 *
 * [Kpi.deltaPercent] is `(current - previous) / previous * 100`, which silently assumes a
 * non-negative baseline. That's fine for Revenue/Collected/Outstanding/Orders (never
 * negative), but Profit CAN be negative (a loss period). Against a negative baseline the
 * ratio inverts: -5000 -> -10000 (the loss doubled — worse) yields +100%, and
 * -5000 -> -2000 (the loss shrank — better) yields a negative %. The percentage-driven
 * arrow points backwards in exactly the periods a tailor most needs to trust it.
 * The signed amount change has no such blind spot: it is simply "did profit go up or down",
 * regardless of what side of zero it started on.
 */
internal enum class ProfitTrend { UP, DOWN, FLAT }

internal fun profitTrend(deltaAmount: Double): ProfitTrend = when {
    deltaAmount > 0.0 -> ProfitTrend.UP
    deltaAmount < 0.0 -> ProfitTrend.DOWN
    else -> ProfitTrend.FLAT
}

/**
 * Profit-only trend chip. Mirrors [DeltaChip]'s "no prior period" empty state (shown when
 * [Kpi.deltaPercent] is `null`, i.e. [Kpi.previous] == 0.0) but otherwise renders a signed
 * ₦ amount driven by [profitTrend] instead of a percentage — see [profitTrend] for why.
 */
@Composable
private fun ProfitDeltaChip(kpi: Kpi, suffix: String, periodLabel: String) {
    if (kpi.deltaPercent == null) {
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val trend = profitTrend(kpi.deltaAmount)
    val (arrow, color) = when (trend) {
        ProfitTrend.UP -> "▲" to deltaColor(positive = true)
        ProfitTrend.DOWN -> "▼" to deltaColor(positive = false)
        ProfitTrend.FLAT -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val amountText = "₦${formatPrice(abs(kpi.deltaAmount))}"
    Text(
        text = "$arrow $amountText  $suffix",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

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
                text = formatProfitAmount(kpi.current),
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
                color = profitValueColor(isLoss = kpi.current < 0.0)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfitDeltaChip(
                    kpi = kpi,
                    suffix = deltaSuffix,
                    periodLabel = periodLabel
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

/**
 * The Profit value's color: a loss period must read as loss (error/red), not as a false
 * positive success (green) — mirrors [com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderCostsCard]'s
 * per-order `ProfitBand` light/dark loss coloring so the Reports tile and the order-level
 * card agree on what a loss looks like.
 */
@Composable
private fun profitValueColor(isLoss: Boolean): Color {
    val dark = isSystemInDarkTheme()
    return when {
        isLoss && dark -> DesignTokens.errorDarkText
        isLoss -> DesignTokens.error500
        dark -> DesignTokens.successDarkText
        else -> DesignTokens.success500
    }
}

/**
 * Formats the Profit tile's headline amount, sign-aware: a loss renders as `"−₦8,000"`
 * (leading minus, magnitude only) rather than `"₦-8,000"`, which reads like a positive
 * figure at a glance. Mirrors [com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderCostsCard]'s
 * `ProfitBand` amount formatting (minus the profit-margin suffix, which the Reports tile
 * doesn't show inline). Pure and unit-tested — see `ProfitAmountFormatTest`.
 */
internal fun formatProfitAmount(profit: Double): String = buildString {
    if (profit < 0.0) append('−')
    append('₦')
    append(formatPrice(abs(profit)))
}

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

// Loss-period regression preview: previous window was already a loss (-5000) and it got
// worse (-10000). The old percent-driven chip showed a lying "▲ +100%" here; the
// deltaAmount-driven ProfitDeltaChip must show "▼ ₦5,000" instead.
private val previewWorseningLossProfitKpi = Kpi(
    current = -10_000.0,
    previous = -5_000.0,
    deltaPercent = 100.0,
    sparkline = listOf(-2_000.0, -3_500.0, -4_200.0, -4_800.0, -5_000.0, -6_500.0, -8_200.0, -10_000.0)
)

@Composable
private fun ProfitKpiTileWorseningLossPreview(darkTheme: Boolean) {
    StitchPadTheme(darkTheme = darkTheme) {
        ProfitKpiTile(
            label = "Profit",
            icon = Icons.Filled.Payments,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBackground = MaterialTheme.colorScheme.primaryContainer,
            kpi = previewWorseningLossProfitKpi,
            isCoverageEmpty = false,
            coverageCaption = "On 9 of 12 orders with costs recorded",
            emptyStateText = "Add costs to an order to see profit",
            deltaSuffix = "vs last week",
            periodLabel = "This week"
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfitKpiTileWorseningLossLightPreview() {
    ProfitKpiTileWorseningLossPreview(darkTheme = false)
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfitKpiTileWorseningLossDarkPreview() {
    ProfitKpiTileWorseningLossPreview(darkTheme = true)
}

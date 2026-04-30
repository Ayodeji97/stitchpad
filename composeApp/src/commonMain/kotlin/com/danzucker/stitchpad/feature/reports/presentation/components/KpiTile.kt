package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.feature.reports.domain.model.Kpi
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
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
            color = MaterialTheme.colorScheme.onSurface
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

package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.TodayWorkBucket
import com.danzucker.stitchpad.feature.dashboard.presentation.model.TodayWorkRowUi
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val ACCENT_BAR_WIDTH = 4.dp
private val AVATAR_SIZE = 56.dp
private val AVATAR_SHAPE_RADIUS = 14.dp
private val CHEVRON_BUTTON_SIZE = 36.dp
private val CHIP_ICON_SIZE = 14.dp
private val META_ICON_SIZE = 14.dp

/**
 * V2 Today's Work row. Replaces the simpler [com.danzucker.stitchpad.ui.components.AccentedOrderRow]
 * used previously in [TodayWorkCard]. Mirrors the visual language of
 * [PipelineOrderRow] (left accent bar, divider + footer) but with:
 *   - Solid brand-primary rounded-square avatar (vs. dashed circle).
 *   - Status pill on the right that carries an icon matching the bucket
 *     (clock for overdue, calendar for due today, check for ready).
 *   - Bucket-driven footer status hint + circular chevron button.
 *
 * The whole card is one tap target; the chevron is decorative.
 */
@Composable
fun TodayWorkRichRow(
    row: TodayWorkRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(DesignTokens.radiusLg)

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(ACCENT_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(row.accentColor),
            )
            Column(modifier = Modifier.padding(DesignTokens.space4)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                ) {
                    AvatarTile(initials = todayWorkInitialsOf(row.customerName))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = row.customerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        GarmentRowSlim(label = row.primaryLabel)
                    }
                    StatusChip(
                        bucket = row.bucket,
                        text = row.chipText,
                        accent = row.chipTextColor,
                        background = row.chipBackground,
                    )
                }
                Spacer(Modifier.height(DesignTokens.space3))
                FooterDivider()
                Spacer(Modifier.height(DesignTokens.space3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatusHint(bucket = row.bucket, accent = row.accentColor)
                    ChevronButton()
                }
            }
        }
    }
}

@Composable
private fun AvatarTile(initials: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(RoundedCornerShape(AVATAR_SHAPE_RADIUS))
            .background(scheme.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

@Composable
private fun GarmentRowSlim(label: String) {
    if (label.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Checkroom,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(META_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusChip(
    bucket: TodayWorkBucket,
    text: String,
    accent: Color,
    background: Color,
) {
    val icon = chipIconFor(bucket)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(background)
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(CHIP_ICON_SIZE),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

@Composable
private fun FooterDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun StatusHint(bucket: TodayWorkBucket, accent: Color) {
    val (icon, text) = statusHintFor(bucket)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(META_ICON_SIZE),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChevronButton() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(CHEVRON_BUTTON_SIZE)
            .clip(CircleShape)
            .background(scheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun chipIconFor(bucket: TodayWorkBucket): ImageVector = when (bucket) {
    TodayWorkBucket.Overdue -> Icons.Default.Schedule
    TodayWorkBucket.DueToday -> Icons.Default.CalendarToday
    TodayWorkBucket.Ready -> Icons.Default.CheckCircle
}

/**
 * Footer status hint copy + icon. Hardcoded English to match the existing
 * [com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiMappers]
 * pattern (mapper produces presentation strings without compose-resources).
 */
private fun statusHintFor(bucket: TodayWorkBucket): Pair<ImageVector, String> = when (bucket) {
    TodayWorkBucket.Overdue -> Icons.Default.Error to "Pickup not ready"
    TodayWorkBucket.DueToday -> Icons.Default.Schedule to "Working on it today"
    TodayWorkBucket.Ready -> Icons.Default.CheckCircle to "Ready for pickup"
}

private fun todayWorkInitialsOf(name: String): String {
    val parts = name.trim().split(' ', '\t').filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TodayWorkRichRowOverduePreview() {
    StitchPadTheme(darkTheme = true) {
        TodayWorkRichRow(
            row = TodayWorkRowUi.preview(
                orderId = "1",
                customerName = "Gose Wale",
                primaryLabel = "Agbada",
                accent = DesignTokens.error500,
                chip = "1 day late",
                bucket = TodayWorkBucket.Overdue,
            ),
            onClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TodayWorkRichRowReadyPreview() {
    StitchPadTheme(darkTheme = true) {
        TodayWorkRichRow(
            row = TodayWorkRowUi.preview(
                orderId = "2",
                customerName = "Adaeze Okoro",
                primaryLabel = "Buba & Skirt",
                accent = DesignTokens.success500,
                chip = "Ready",
                bucket = TodayWorkBucket.Ready,
            ),
            onClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

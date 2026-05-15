package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_due_in_days
import stitchpad.composeapp.generated.resources.dashboard_pipeline_row_bespoke
import stitchpad.composeapp.generated.resources.dashboard_pipeline_row_created
import stitchpad.composeapp.generated.resources.dashboard_pipeline_row_custom_size

private val AVATAR_SIZE = 56.dp
private val DUE_PILL_ICON_SIZE = 14.dp
private val META_ICON_SIZE = 12.dp
private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * V2 pipeline order row. Replaces the AccentedOrderRow used previously in
 * the Work Pipeline section. Visually brand-aligned: dashed-brand-primary
 * avatar, garment label with hanger icon, due-date pill on the right,
 * and a metadata footer ("Custom size · Bespoke · Created 2 May").
 *
 * The "Custom size" + "Bespoke" labels are static for now — every order
 * StitchPad models is bespoke + custom-measured. If/when we support
 * standard sizes or ready-to-wear, they should become conditional.
 */
@Composable
fun PipelineOrderRow(
    row: DashboardOrderRow,
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
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                DashedAvatar(initials = pipelineInitialsOf(row.customerName))
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
                    GarmentRow(label = row.primaryLabel)
                }
                if (row.daysUntilDeadline != null) {
                    DueInPill(days = row.daysUntilDeadline)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(DesignTokens.space3))
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(DesignTokens.space3))
            MetadataFooter(createdAt = row.createdAtEpochMillis)
        }
    }
}

@Composable
private fun DashedAvatar(initials: String) {
    val scheme = MaterialTheme.colorScheme
    val ring = scheme.primary
    val bg = scheme.primary.copy(alpha = 0.14f)
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 1.5.dp.toPx() }
    val dashOnPx = with(density) { 4.dp.toPx() }
    val dashOffPx = with(density) { 3.dp.toPx() }

    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .background(color = bg, shape = CircleShape)
            .drawBehind {
                drawCircle(
                    color = ring,
                    radius = (size.minDimension / 2f) - (strokeWidthPx / 2f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashOnPx, dashOffPx),
                            0f,
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ring,
        )
    }
}

@Composable
private fun GarmentRow(label: String) {
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
private fun DueInPill(days: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(color = scheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(DUE_PILL_ICON_SIZE),
        )
        Text(
            text = stringResource(Res.string.dashboard_due_in_days, days),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
    }
}

@Composable
private fun HorizontalDivider(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
private fun MetadataFooter(createdAt: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MetaItem(
            icon = Icons.Default.Straighten,
            label = stringResource(Res.string.dashboard_pipeline_row_custom_size),
        )
        MetaSeparator()
        MetaItem(
            icon = Icons.Default.Checkroom,
            label = stringResource(Res.string.dashboard_pipeline_row_bespoke),
        )
        if (createdAt > 0L) {
            MetaSeparator()
            MetaItem(
                icon = Icons.Default.Schedule,
                label = stringResource(
                    Res.string.dashboard_pipeline_row_created,
                    formatCreatedDate(createdAt),
                ),
            )
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(META_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetaSeparator() {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "1746137600000" → "2 May" using the device's TZ. */
private fun formatCreatedDate(epochMillis: Long): String {
    val date: LocalDate = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = MONTH_ABBREV.getOrNull(date.monthNumber - 1) ?: ""
    return "${date.dayOfMonth} $month"
}

private fun pipelineInitialsOf(name: String): String {
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
private fun PipelineOrderRowPreview() {
    StitchPadTheme(darkTheme = true) {
        PipelineOrderRow(
            row = DashboardOrderRow(
                orderId = "1",
                customerName = "Omobolanle Johnson",
                primaryLabel = "Kaftan",
                daysUntilDeadline = 17,
                createdAtEpochMillis = 1746144000000L,
            ),
            onClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_production_timeline
import stitchpad.composeapp.generated.resources.order_stage_cutting
import stitchpad.composeapp.generated.resources.order_stage_delivered
import stitchpad.composeapp.generated.resources.order_stage_fitting
import stitchpad.composeapp.generated.resources.order_stage_pending
import stitchpad.composeapp.generated.resources.order_stage_ready
import stitchpad.composeapp.generated.resources.order_stage_sewing

// ── Private timeline model ────────────────────────────────────────────────────

private enum class TimelineStage {
    Pending,
    Cutting,
    Sewing,
    Fitting,
    Ready,
    Delivered,
}

private fun activeStage(status: OrderStatus, subStatus: OrderSubStatus?): TimelineStage =
    when (status) {
        OrderStatus.PENDING -> TimelineStage.Pending
        OrderStatus.IN_PROGRESS -> when (subStatus) {
            OrderSubStatus.SEWING -> TimelineStage.Sewing
            OrderSubStatus.FITTING -> TimelineStage.Fitting
            OrderSubStatus.CUTTING, null -> TimelineStage.Cutting
        }
        OrderStatus.READY -> TimelineStage.Ready
        OrderStatus.DELIVERED -> TimelineStage.Delivered
    }

private val ALL_STAGES = TimelineStage.entries

private fun stageIcon(stage: TimelineStage): ImageVector = when (stage) {
    TimelineStage.Pending -> Icons.Default.HourglassTop
    TimelineStage.Cutting -> Icons.Default.ContentCut
    TimelineStage.Sewing -> Icons.Default.PrecisionManufacturing
    TimelineStage.Fitting -> Icons.Default.Accessibility
    TimelineStage.Ready -> Icons.Default.Inventory2
    TimelineStage.Delivered -> Icons.Default.CheckCircle
}

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun OrderProductionTimeline(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
    isOverdue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = activeStage(currentStatus, currentSubStatus)
    val accentColor = when {
        current == TimelineStage.Delivered -> DesignTokens.success500
        isOverdue -> DesignTokens.error500
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    SectionIconTile(
                        imageVector = Icons.Default.PrecisionManufacturing,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(Res.string.order_detail_production_timeline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (currentStatus != OrderStatus.DELIVERED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Update",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(DesignTokens.iconInline),
                        )
                    }
                }
            }

            // ── Timeline nodes ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.space4),
            ) {
                // Connecting line — offset 13dp from top to bisect the 28dp nodes (centre at 14dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 13.dp)
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ALL_STAGES.forEach { stage ->
                        TimelineStep(
                            stage = stage,
                            current = current,
                            accentColor = accentColor,
                        )
                    }
                }
            }
        }
    }
}

// ── Step composable ───────────────────────────────────────────────────────────

@Composable
private fun TimelineStep(
    stage: TimelineStage,
    current: TimelineStage,
    accentColor: Color,
) {
    val stageOrdinal = stage.ordinal
    val currentOrdinal = current.ordinal

    val isCurrent = stageOrdinal == currentOrdinal
    val isDone = stageOrdinal < currentOrdinal

    val nodeSize = if (isCurrent) 32.dp else 28.dp

    val nodeBackground = when {
        isCurrent -> accentColor
        isDone -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    val nodeBorderColor = when {
        isCurrent -> accentColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val labelColor = when {
        isCurrent -> accentColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val labelWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Node circle — Surface handles fill + 2dp border
        Surface(
            shape = CircleShape,
            color = nodeBackground,
            border = BorderStroke(2.dp, nodeBorderColor),
            modifier = Modifier.size(nodeSize),
        ) {
            Box(
                modifier = Modifier.size(nodeSize),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isCurrent -> Icon(
                        imageVector = stageIcon(stage),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    isDone -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    // Upcoming: no icon
                    else -> Unit
                }
            }
        }

        // Stage label
        Text(
            text = stageLabel(stage),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = labelWeight,
            color = labelColor,
        )
    }
}

@Composable
private fun stageLabel(stage: TimelineStage): String = when (stage) {
    TimelineStage.Pending -> stringResource(Res.string.order_stage_pending)
    TimelineStage.Cutting -> stringResource(Res.string.order_stage_cutting)
    TimelineStage.Sewing -> stringResource(Res.string.order_stage_sewing)
    TimelineStage.Fitting -> stringResource(Res.string.order_stage_fitting)
    TimelineStage.Ready -> stringResource(Res.string.order_stage_ready)
    TimelineStage.Delivered -> stringResource(Res.string.order_stage_delivered)
}

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderProductionTimelinePendingLightPreview() {
    StitchPadTheme {
        OrderProductionTimeline(
            currentStatus = OrderStatus.PENDING,
            currentSubStatus = null,
            isOverdue = false,
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderProductionTimelineCuttingLightPreview() {
    StitchPadTheme {
        OrderProductionTimeline(
            currentStatus = OrderStatus.IN_PROGRESS,
            currentSubStatus = OrderSubStatus.CUTTING,
            isOverdue = false,
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderProductionTimelineFittingOverdueLightPreview() {
    StitchPadTheme {
        OrderProductionTimeline(
            currentStatus = OrderStatus.IN_PROGRESS,
            currentSubStatus = OrderSubStatus.FITTING,
            isOverdue = true,
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderProductionTimelineDeliveredLightPreview() {
    StitchPadTheme {
        OrderProductionTimeline(
            currentStatus = OrderStatus.DELIVERED,
            currentSubStatus = null,
            isOverdue = false,
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderProductionTimelineSewingDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderProductionTimeline(
            currentStatus = OrderStatus.IN_PROGRESS,
            currentSubStatus = OrderSubStatus.SEWING,
            isOverdue = false,
            onClick = {},
        )
    }
}

// endregion

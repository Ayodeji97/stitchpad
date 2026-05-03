package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.feature.order.presentation.detail.StatusTransition
import com.danzucker.stitchpad.feature.order.presentation.detail.nextStatusTransitions
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_stage_cutting
import stitchpad.composeapp.generated.resources.order_stage_delivered
import stitchpad.composeapp.generated.resources.order_stage_fitting
import stitchpad.composeapp.generated.resources.order_stage_pending
import stitchpad.composeapp.generated.resources.order_stage_ready
import stitchpad.composeapp.generated.resources.order_stage_sewing
import stitchpad.composeapp.generated.resources.status_sheet_subtitle_cutting
import stitchpad.composeapp.generated.resources.status_sheet_subtitle_delivered
import stitchpad.composeapp.generated.resources.status_sheet_subtitle_fitting
import stitchpad.composeapp.generated.resources.status_sheet_subtitle_pending
import stitchpad.composeapp.generated.resources.status_sheet_subtitle_ready
import stitchpad.composeapp.generated.resources.status_sheet_subtitle_sewing
import stitchpad.composeapp.generated.resources.status_sheet_title

// ── Private timeline stage model ─────────────────────────────────────────────

private enum class TransitionStage {
    Pending,
    Cutting,
    Sewing,
    Fitting,
    Ready,
    Delivered,
}

private fun resolveTransitionStage(
    status: OrderStatus,
    subStatus: OrderSubStatus?,
): TransitionStage = when (status) {
    OrderStatus.PENDING -> TransitionStage.Pending
    OrderStatus.IN_PROGRESS -> when (subStatus) {
        OrderSubStatus.SEWING -> TransitionStage.Sewing
        OrderSubStatus.FITTING -> TransitionStage.Fitting
        OrderSubStatus.CUTTING, null -> TransitionStage.Cutting
    }
    OrderStatus.READY -> TransitionStage.Ready
    OrderStatus.DELIVERED -> TransitionStage.Delivered
}

private fun transitionToStage(transition: StatusTransition): TransitionStage =
    resolveTransitionStage(transition.toStatus, transition.toSubStatus)

private fun stageIcon(stage: TransitionStage): ImageVector = when (stage) {
    TransitionStage.Pending -> Icons.Default.HourglassTop
    TransitionStage.Cutting -> Icons.Default.ContentCut
    TransitionStage.Sewing -> Icons.Default.PrecisionManufacturing
    TransitionStage.Fitting -> Icons.Default.Accessibility
    TransitionStage.Ready -> Icons.Default.Inventory2
    TransitionStage.Delivered -> Icons.Default.CheckCircle
}

// ── Public composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTransitionSheet(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
    onTransitionSelected: (StatusTransition) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StatusTransitionSheetContent(
            currentStatus = currentStatus,
            currentSubStatus = currentSubStatus,
            onTransitionSelected = onTransitionSelected,
        )
    }
}

@Composable
private fun StatusTransitionSheetContent(
    currentStatus: OrderStatus,
    currentSubStatus: OrderSubStatus?,
    onTransitionSelected: (StatusTransition) -> Unit,
) {
    val currentStage = resolveTransitionStage(currentStatus, currentSubStatus)
    val currentStageLabel = stageName(currentStage)
    val transitions = nextStatusTransitions(currentStatus, currentSubStatus)

    Column(
        modifier = Modifier
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.status_sheet_title, currentStageLabel),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = DesignTokens.space2),
        )

        transitions.forEach { transition ->
            TransitionRow(
                transition = transition,
                currentStage = currentStage,
                onTransitionSelected = onTransitionSelected,
            )
        }
    }
}

@Composable
private fun TransitionRow(
    transition: StatusTransition,
    currentStage: TransitionStage,
    onTransitionSelected: (StatusTransition) -> Unit,
) {
    val targetStage = transitionToStage(transition)
    val isBackMove = targetStage.ordinal < currentStage.ordinal
    val iconTint = if (isBackMove) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    val titlePrefix = if (isBackMove) "Back to " else ""
    val title = titlePrefix + stageName(targetStage)
    val subtitle = stageSubtitle(targetStage)

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTransitionSelected(transition) },
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionIconTile(
                imageVector = stageIcon(targetStage),
                contentDescription = null,
                tint = iconTint,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun stageName(stage: TransitionStage): String = when (stage) {
    TransitionStage.Pending -> stringResource(Res.string.order_stage_pending)
    TransitionStage.Cutting -> stringResource(Res.string.order_stage_cutting)
    TransitionStage.Sewing -> stringResource(Res.string.order_stage_sewing)
    TransitionStage.Fitting -> stringResource(Res.string.order_stage_fitting)
    TransitionStage.Ready -> stringResource(Res.string.order_stage_ready)
    TransitionStage.Delivered -> stringResource(Res.string.order_stage_delivered)
}

@Composable
private fun stageSubtitle(stage: TransitionStage): String = when (stage) {
    TransitionStage.Pending -> stringResource(Res.string.status_sheet_subtitle_pending)
    TransitionStage.Cutting -> stringResource(Res.string.status_sheet_subtitle_cutting)
    TransitionStage.Sewing -> stringResource(Res.string.status_sheet_subtitle_sewing)
    TransitionStage.Fitting -> stringResource(Res.string.status_sheet_subtitle_fitting)
    TransitionStage.Ready -> stringResource(Res.string.status_sheet_subtitle_ready)
    TransitionStage.Delivered -> stringResource(Res.string.status_sheet_subtitle_delivered)
}

// ── SectionIconTile (local copy with tint parameter) ─────────────────────────

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = tint.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

// region — Previews (render content only — ModalBottomSheet needs an Activity)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StatusTransitionSheetFromPendingLightPreview() {
    StitchPadTheme {
        Surface {
            StatusTransitionSheetContent(
                currentStatus = OrderStatus.PENDING,
                currentSubStatus = null,
                onTransitionSelected = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StatusTransitionSheetFromSewingLightPreview() {
    StitchPadTheme {
        Surface {
            StatusTransitionSheetContent(
                currentStatus = OrderStatus.IN_PROGRESS,
                currentSubStatus = OrderSubStatus.SEWING,
                onTransitionSelected = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StatusTransitionSheetFromFittingDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            StatusTransitionSheetContent(
                currentStatus = OrderStatus.IN_PROGRESS,
                currentSubStatus = OrderSubStatus.FITTING,
                onTransitionSelected = {},
            )
        }
    }
}

// endregion

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.staff_stage_sheet_title

/** Stage dot diameter inside a sheet row — larger than the compact ticket-footer [StageDots]. */
private val STAGE_ROW_DOT_SIZE = 10.dp

/**
 * Tappable-stepper stage sheet (Decision 2B, 2026-08-16): lets staff move an
 * order to ANY pipeline stage in one tap, not just the next one forward —
 * opened from [UpNextHero]'s stepper tap ([DashboardAction.OnStageStepperClick]).
 * One row per [PipelineStage.entries]; tapping a row (including the current
 * stage, which the ViewModel no-ops on `toStage == fromStage`) fires
 * [onSelect] — the caller always closes the sheet in response, even when the
 * ViewModel's stale/re-entrancy guards then no-op the write (see
 * [com.danzucker.stitchpad.feature.dashboard.presentation.DashboardViewModel.handleSetStage]).
 *
 * Colour rule mirrors [StageDots] exactly: done = primary dot (+ a checkmark
 * so "already past this stage" reads without relying on colour alone),
 * current = saffron dot with a `primaryContainer` highlight + border (the
 * heritage accent's one sanctioned non-text use on this screen), upcoming =
 * neutral `outlineVariant` dot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageSheet(
    currentStage: PipelineStage,
    customerName: String,
    orderCode: String,
    onSelect: (PipelineStage) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StageSheetContent(
            currentStage = currentStage,
            customerName = customerName,
            orderCode = orderCode,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun StageSheetContent(
    currentStage: PipelineStage,
    customerName: String,
    orderCode: String,
    onSelect: (PipelineStage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space6),
    ) {
        Text(
            text = stringResource(Res.string.staff_stage_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = customerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = orderCode,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space3))
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
            PipelineStage.entries.forEach { stage ->
                StageSheetRow(
                    stage = stage,
                    currentStage = currentStage,
                    onClick = { onSelect(stage) },
                )
            }
        }
    }
}

@Composable
private fun StageSheetRow(
    stage: PipelineStage,
    currentStage: PipelineStage,
    onClick: () -> Unit,
) {
    val isCurrent = stage == currentStage
    val isDone = stage.ordinal < currentStage.ordinal
    val dotColor = when {
        isDone -> MaterialTheme.colorScheme.primary
        isCurrent -> DesignTokens.saffron500
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Box(Modifier.size(STAGE_ROW_DOT_SIZE).clip(CircleShape).background(dotColor))
            Text(
                text = stringResource(stageLabelRes(stage)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (isDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// region — Previews (render content only — ModalBottomSheet needs an Activity)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StageSheetContentLightPreview() {
    StitchPadTheme {
        Surface {
            StageSheetContent(
                currentStage = PipelineStage.FITTING,
                customerName = "Chidi Okafor",
                orderCode = "ORD-C3D4",
                onSelect = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StageSheetContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            StageSheetContent(
                currentStage = PipelineStage.SEWING,
                customerName = "Amaka Nwosu",
                orderCode = "ORD-A1B2",
                onSelect = {},
            )
        }
    }
}

// endregion

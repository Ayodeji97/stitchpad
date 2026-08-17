package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_stage_cutting
import stitchpad.composeapp.generated.resources.order_stage_fitting
import stitchpad.composeapp.generated.resources.order_stage_pending
import stitchpad.composeapp.generated.resources.order_stage_ready
import stitchpad.composeapp.generated.resources.order_stage_sewing
import stitchpad.composeapp.generated.resources.staff_stage_progress_cd

/** "Sewing, stage 3 of 5" — shared content description for the hero stepper and ticket stage dots. */
@Composable
fun stageProgressDescription(stage: PipelineStage): String = stringResource(
    Res.string.staff_stage_progress_cd,
    stageLabel(stage),
    stage.ordinal + 1,
    PipelineStage.entries.size,
)

/**
 * The one stage-progress colour rule, shared by every surface that paints the
 * pipeline: these dots, the staff dashboard's hero stepper segments, and the stage
 * sheet's rows. Done = primary (indigo), current = saffron500 (the heritage
 * accent's sanctioned non-text use), upcoming = neutral outlineVariant.
 *
 * [done] and [current] are mutually exclusive; [done] wins if both are passed.
 */
@Composable
fun stageIndicatorColor(done: Boolean, current: Boolean): Color = when {
    done -> MaterialTheme.colorScheme.primary
    current -> DesignTokens.saffron500
    else -> MaterialTheme.colorScheme.outlineVariant
}

/** `●●●○○` — see [stageIndicatorColor] for the colour rule. */
@Composable
fun StageDots(stage: PipelineStage, modifier: Modifier = Modifier) {
    val progressDescription = stageProgressDescription(stage)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = progressDescription },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PipelineStage.entries.forEach { s ->
            val color = stageIndicatorColor(done = s.ordinal < stage.ordinal, current = s == stage)
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
    }
}

/**
 * The single [PipelineStage] -> label-resource mapping, shared by the staff
 * dashboard's hero stepper, the stage sheet, and the order rows' stage dots.
 * Exposed as the resource id (not a resolved string) so DashboardScreen's
 * undo-snackbar message can call `getString` from a coroutine outside
 * composition. Kept here — not in `PipelineStage.kt` — because that file
 * sits in the domain layer, which can't reference compose resources.
 */
fun stageLabelRes(stage: PipelineStage): StringResource = when (stage) {
    PipelineStage.PENDING -> Res.string.order_stage_pending
    PipelineStage.CUTTING -> Res.string.order_stage_cutting
    PipelineStage.SEWING -> Res.string.order_stage_sewing
    PipelineStage.FITTING -> Res.string.order_stage_fitting
    PipelineStage.READY -> Res.string.order_stage_ready
}

@Composable
fun stageLabel(stage: PipelineStage): String = stringResource(stageLabelRes(stage))

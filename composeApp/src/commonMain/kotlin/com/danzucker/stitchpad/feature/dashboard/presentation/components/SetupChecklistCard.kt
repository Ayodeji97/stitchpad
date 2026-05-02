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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.setup_checklist_progress
import stitchpad.composeapp.generated.resources.setup_checklist_title
import stitchpad.composeapp.generated.resources.setup_step_customer_desc_done
import stitchpad.composeapp.generated.resources.setup_step_customer_label
import stitchpad.composeapp.generated.resources.setup_step_deposit_desc
import stitchpad.composeapp.generated.resources.setup_step_deposit_label
import stitchpad.composeapp.generated.resources.setup_step_due_date_desc
import stitchpad.composeapp.generated.resources.setup_step_due_date_label
import stitchpad.composeapp.generated.resources.setup_step_order_desc
import stitchpad.composeapp.generated.resources.setup_step_order_label

private val ICON_BADGE_SIZE = 36.dp
private val PROGRESS_TRACK_WIDTH = 80.dp
private val PROGRESS_TRACK_HEIGHT = 6.dp

/**
 * The four onboarding steps shown on the FirstCustomer (and possibly
 * later) state. Stable enum so callers can wire taps without coupling
 * to row index.
 */
enum class SetupStepKey {
    CustomerCreated,
    AddFirstOrder,
    SetDueDate,
    RecordDeposit,
}

enum class SetupStepStatus {
    Done,
    Active,
    Pending,
}

data class SetupStep(
    val key: SetupStepKey,
    val number: Int,
    val status: SetupStepStatus,
)

/**
 * Setup checklist for the onboarding state — 4 stacked rows + progress bar.
 *
 * Visual rules (matches the mockup):
 *  - Done: green-tinted circle with check, supporting text reads "Completed",
 *    no chevron, row not tappable.
 *  - Active: saffron-tinted icon circle + saffron border + saffron-tinted
 *    background on the row, chevron in saffron, row tappable.
 *  - Pending: neutral tinted icon, no row border, muted chevron, not tappable.
 */
@Composable
fun SetupChecklistCard(
    steps: List<SetupStep>,
    onActiveStepClick: (SetupStepKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneCount = steps.count { it.status == SetupStepStatus.Done }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            ChecklistHeader(done = doneCount, total = steps.size)
            steps.forEach { step ->
                ChecklistRow(
                    step = step,
                    onClick = { onActiveStepClick(step.key) },
                )
            }
        }
    }
}

@Composable
private fun ChecklistHeader(done: Int, total: Int) {
    val ratio = if (total <= 0) 0f else done.toFloat() / total.toFloat()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.setup_checklist_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.setup_checklist_progress, done, total),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .width(PROGRESS_TRACK_WIDTH)
                .height(PROGRESS_TRACK_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(PROGRESS_TRACK_HEIGHT)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ),
            )
        }
    }
}

@Composable
private fun ChecklistRow(step: SetupStep, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(DesignTokens.radiusMd)
    val rowBg: Color
    val rowBorder: Color
    when (step.status) {
        SetupStepStatus.Done -> {
            rowBg = scheme.surface
            rowBorder = scheme.outlineVariant
        }
        SetupStepStatus.Active -> {
            rowBg = scheme.primary.copy(alpha = 0.06f)
            rowBorder = scheme.primary.copy(alpha = 0.4f)
        }
        SetupStepStatus.Pending -> {
            rowBg = scheme.surface
            rowBorder = scheme.outlineVariant
        }
    }

    val tappable = step.status == SetupStepStatus.Active
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(rowShape)
        .then(
            if (tappable) {
                Modifier.clickable(onClick = onClick, role = Role.Button)
            } else {
                Modifier
            },
        )

    Surface(
        shape = rowShape,
        color = rowBg,
        border = BorderStroke(1.dp, rowBorder),
        modifier = rowModifier,
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            StepIconBadge(step = step)
            StepText(step = step, modifier = Modifier.weight(1f))
            if (step.status != SetupStepStatus.Done) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (step.status == SetupStepStatus.Active) {
                        scheme.primary
                    } else {
                        scheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StepIconBadge(step: SetupStep) {
    val scheme = MaterialTheme.colorScheme
    val (background, tint, icon) = when (step.status) {
        SetupStepStatus.Done -> Triple(SUCCESS_TINT_BG, SUCCESS_TINT_FG, Icons.Default.Check)
        SetupStepStatus.Active -> Triple(
            scheme.primary.copy(alpha = 0.14f),
            scheme.primary,
            iconForKey(step.key),
        )
        SetupStepStatus.Pending -> Triple(
            scheme.outlineVariant.copy(alpha = 0.5f),
            scheme.onSurfaceVariant,
            iconForKey(step.key),
        )
    }
    Box(
        modifier = Modifier
            .size(ICON_BADGE_SIZE)
            .background(color = background, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

// Theme-agnostic success ring for the "Completed" state. We don't have a
// success-tonal token in the design system yet, so this is baked in.
private val SUCCESS_TINT_BG = Color(0xFFDFF3E6)
private val SUCCESS_TINT_FG = Color(0xFF2D9E6B)

private fun iconForKey(key: SetupStepKey): ImageVector = when (key) {
    SetupStepKey.CustomerCreated -> Icons.Default.Check
    SetupStepKey.AddFirstOrder -> Icons.AutoMirrored.Filled.Assignment
    SetupStepKey.SetDueDate -> Icons.Default.CalendarToday
    SetupStepKey.RecordDeposit -> Icons.Default.AccountBalanceWallet
}

@Composable
private fun StepText(step: SetupStep, modifier: Modifier = Modifier) {
    val labelText = labelFor(step.key)
    val descText = descFor(step.key, step.status)
    val descColor = if (step.status == SetupStepStatus.Done) {
        SUCCESS_TINT_FG
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = step.number.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = descText,
            style = MaterialTheme.typography.bodySmall,
            color = descColor,
            fontWeight = if (step.status == SetupStepStatus.Done) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun labelFor(key: SetupStepKey): String = when (key) {
    SetupStepKey.CustomerCreated -> stringResource(Res.string.setup_step_customer_label)
    SetupStepKey.AddFirstOrder -> stringResource(Res.string.setup_step_order_label)
    SetupStepKey.SetDueDate -> stringResource(Res.string.setup_step_due_date_label)
    SetupStepKey.RecordDeposit -> stringResource(Res.string.setup_step_deposit_label)
}

@Composable
private fun descFor(key: SetupStepKey, status: SetupStepStatus): String =
    if (status == SetupStepStatus.Done) {
        stringResource(Res.string.setup_step_customer_desc_done)
    } else {
        when (key) {
            SetupStepKey.CustomerCreated -> stringResource(Res.string.setup_step_customer_desc_done)
            SetupStepKey.AddFirstOrder -> stringResource(Res.string.setup_step_order_desc)
            SetupStepKey.SetDueDate -> stringResource(Res.string.setup_step_due_date_desc)
            SetupStepKey.RecordDeposit -> stringResource(Res.string.setup_step_deposit_desc)
        }
    }

private fun firstCustomerSampleSteps(): List<SetupStep> = listOf(
    SetupStep(SetupStepKey.CustomerCreated, 1, SetupStepStatus.Done),
    SetupStep(SetupStepKey.AddFirstOrder, 2, SetupStepStatus.Active),
    SetupStep(SetupStepKey.SetDueDate, 3, SetupStepStatus.Pending),
    SetupStep(SetupStepKey.RecordDeposit, 4, SetupStepStatus.Pending),
)

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun SetupChecklistCardLightPreview() {
    StitchPadTheme {
        SetupChecklistCard(
            steps = firstCustomerSampleSteps(),
            onActiveStepClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun SetupChecklistCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SetupChecklistCard(
            steps = firstCustomerSampleSteps(),
            onActiveStepClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

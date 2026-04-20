package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.deadline_day_late
import stitchpad.composeapp.generated.resources.deadline_days_late
import stitchpad.composeapp.generated.resources.deadline_due_in_days
import stitchpad.composeapp.generated.resources.deadline_due_today
import stitchpad.composeapp.generated.resources.deadline_due_tomorrow
import stitchpad.composeapp.generated.resources.deadline_no_deadline
import stitchpad.composeapp.generated.resources.deadline_pickup_ready

@Composable
fun DeadlineLine(
    deadline: Long?,
    now: Long,
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    val display = formatDeadline(deadline, now, status)
    val text = when (display) {
        DeadlineDisplay.NoDeadline -> stringResource(Res.string.deadline_no_deadline)
        DeadlineDisplay.PickupReady -> stringResource(Res.string.deadline_pickup_ready)
        DeadlineDisplay.DueToday -> stringResource(Res.string.deadline_due_today)
        DeadlineDisplay.DueTomorrow -> stringResource(Res.string.deadline_due_tomorrow)
        is DeadlineDisplay.DaysLate ->
            if (display.days == 1) stringResource(Res.string.deadline_day_late)
            else stringResource(Res.string.deadline_days_late, display.days)
        is DeadlineDisplay.DueInDays -> stringResource(Res.string.deadline_due_in_days, display.days)
    }
    val color = colorFor(display)

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun colorFor(display: DeadlineDisplay): Color = when (display) {
    is DeadlineDisplay.DaysLate -> DesignTokens.error500
    DeadlineDisplay.DueToday, DeadlineDisplay.DueTomorrow -> DesignTokens.warning500
    is DeadlineDisplay.DueInDays -> if (display.soon) DesignTokens.warning500 else MaterialTheme.colorScheme.onSurfaceVariant
    DeadlineDisplay.PickupReady -> DesignTokens.success500
    DeadlineDisplay.NoDeadline -> MaterialTheme.colorScheme.onSurfaceVariant
}

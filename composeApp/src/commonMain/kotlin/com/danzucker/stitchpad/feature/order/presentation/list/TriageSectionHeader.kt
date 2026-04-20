package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.triage_due_this_week
import stitchpad.composeapp.generated.resources.triage_in_progress
import stitchpad.composeapp.generated.resources.triage_overdue
import stitchpad.composeapp.generated.resources.triage_pending
import stitchpad.composeapp.generated.resources.triage_ready_for_pickup

@Composable
fun TriageSectionHeader(
    group: TriageGroup,
    count: Int,
    modifier: Modifier = Modifier
) {
    val color = colorFor(group)
    val label = stringResource(labelFor(group)).uppercase()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space3,
                bottom = DesignTokens.space1
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = DesignTokens.labelSm,
            letterSpacing = 1.sp
        )
        Text(
            text = count.toString(),
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelSm
        )
    }
}

@Composable
private fun colorFor(group: TriageGroup): Color = when (group) {
    TriageGroup.OVERDUE -> DesignTokens.error500
    TriageGroup.DUE_THIS_WEEK -> DesignTokens.warning500
    TriageGroup.IN_PROGRESS -> DesignTokens.info500
    TriageGroup.READY_FOR_PICKUP -> DesignTokens.success500
    TriageGroup.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun labelFor(group: TriageGroup): StringResource = when (group) {
    TriageGroup.OVERDUE -> Res.string.triage_overdue
    TriageGroup.DUE_THIS_WEEK -> Res.string.triage_due_this_week
    TriageGroup.IN_PROGRESS -> Res.string.triage_in_progress
    TriageGroup.READY_FOR_PICKUP -> Res.string.triage_ready_for_pickup
    TriageGroup.PENDING -> Res.string.triage_pending
}

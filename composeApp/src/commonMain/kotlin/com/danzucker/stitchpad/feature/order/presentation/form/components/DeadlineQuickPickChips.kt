package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_form_deadline_quick_1_week
import stitchpad.composeapp.generated.resources.order_form_deadline_quick_2_weeks
import stitchpad.composeapp.generated.resources.order_form_deadline_quick_3_days

/**
 * One-tap deadline chips ("3 days", "1 week", "2 weeks"). [selectedDays] is the chip whose
 * date equals the current deadline (computed by the ViewModel); [onPick] receives the days.
 */
@Composable
fun DeadlineQuickPickChips(
    options: List<Int>,
    selectedDays: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEach { days ->
            val label = quickPickLabel(days) ?: return@forEach
            val isSelected = selectedDays == days
            FilterChip(
                selected = isSelected,
                onClick = { onPick(days) },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
private fun quickPickLabel(days: Int): String? = when (days) {
    QUICK_PICK_3_DAYS -> stringResource(Res.string.order_form_deadline_quick_3_days)
    QUICK_PICK_1_WEEK -> stringResource(Res.string.order_form_deadline_quick_1_week)
    QUICK_PICK_2_WEEKS -> stringResource(Res.string.order_form_deadline_quick_2_weeks)
    else -> null
}

private const val QUICK_PICK_3_DAYS = 3
private const val QUICK_PICK_1_WEEK = 7
private const val QUICK_PICK_2_WEEKS = 14

@Suppress("UnusedPrivateMember", "MagicNumber")
@Preview
@Composable
private fun DeadlineQuickPickChipsPreview() {
    StitchPadTheme {
        DeadlineQuickPickChips(
            options = listOf(3, 7, 14),
            selectedDays = 7,
            onPick = {},
        )
    }
}

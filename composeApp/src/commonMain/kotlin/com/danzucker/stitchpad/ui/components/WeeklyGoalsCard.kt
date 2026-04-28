package com.danzucker.stitchpad.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val PROGRESS_BAR_HEIGHT = 8.dp
private const val PROGRESS_TRACK_ALPHA = 0.16f
private const val EMPTY_BORDER_ALPHA = 0.5f

/**
 * Two-state visual model for [WeeklyGoalsCard].
 *
 * [Empty] renders a dashed-border invitation to set the first goal.
 * [Filled] renders the active goal with progress bar and meta.
 *
 * Using a sealed interface (instead of nullable params) makes each state's
 * required fields explicit and prevents partially-populated cards.
 */
sealed interface WeeklyGoalsCardState {
    data class Empty(
        val label: String,
        val ctaLabel: String
    ) : WeeklyGoalsCardState

    data class Filled(
        val sectionLabel: String,
        val daysLeftLabel: String,
        val revenueLabel: String,
        val progressText: String,
        val progressPercent: Float,
        /**
         * When non-null, the user has met or exceeded their target. The card adds
         * an inline arrow CTA with this label that opens the goal-setup flow so
         * they can raise the bar for the rest of the week.
         */
        val achievedCtaLabel: String? = null
    ) : WeeklyGoalsCardState
}

/**
 * Sits between [FocusTodayCard] and the rest of the dashboard, showing weekly
 * revenue progress against a target. Whole card is tappable — taps open the
 * goal-setup screen (PR 8) for both empty and filled states.
 *
 * @param state Either [WeeklyGoalsCardState.Empty] or [WeeklyGoalsCardState.Filled].
 * @param onClick Invoked on tap of the whole card.
 */
@Composable
fun WeeklyGoalsCard(
    state: WeeklyGoalsCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is WeeklyGoalsCardState.Empty -> EmptyCard(state, onClick, modifier)
        is WeeklyGoalsCardState.Filled -> FilledCard(state, onClick, modifier)
    }
}

@Composable
private fun EmptyCard(
    state: WeeklyGoalsCardState.Empty,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = EMPTY_BORDER_ALPHA)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(DesignTokens.space4)
        ) {
            Text(
                text = state.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
            ) {
                Text(
                    text = state.ctaLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DesignTokens.iconInline)
                )
            }
        }
    }
}

@Composable
private fun FilledCard(
    state: WeeklyGoalsCardState.Filled,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val isAchieved = state.achievedCtaLabel != null
    val accent = if (isAchieved) DesignTokens.success500 else MaterialTheme.colorScheme.primary

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.padding(DesignTokens.space4)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
                ) {
                    if (isAchieved) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(DesignTokens.iconInline)
                        )
                    }
                    Text(
                        text = state.sectionLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isAchieved) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = state.daysLeftLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.revenueLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.progressText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Spacer(Modifier.height(DesignTokens.space1))
            ProgressBar(percent = state.progressPercent, color = accent)
            if (state.achievedCtaLabel != null) {
                Spacer(Modifier.height(DesignTokens.space1))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
                ) {
                    Text(
                        text = state.achievedCtaLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(DesignTokens.iconInline)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Float, color: Color = MaterialTheme.colorScheme.primary) {
    val trackColor = color.copy(alpha = PROGRESS_TRACK_ALPHA)
    val barShape = RoundedCornerShape(DesignTokens.radiusFull)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT)
            .clip(barShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0f, 1f))
                .height(PROGRESS_BAR_HEIGHT)
                .clip(barShape)
                .background(color)
        )
    }
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WeeklyGoalsCardEmptyPreview() {
    StitchPadTheme {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)
        ) {
            WeeklyGoalsCard(
                state = WeeklyGoalsCardState.Empty(
                    label = "Set your first revenue goal",
                    ctaLabel = "Get started"
                ),
                onClick = {}
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WeeklyGoalsCardFilledPreview() {
    StitchPadTheme {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)
        ) {
            WeeklyGoalsCard(
                state = WeeklyGoalsCardState.Filled(
                    sectionLabel = "This week",
                    daysLeftLabel = "3 days left",
                    revenueLabel = "Revenue goal",
                    progressText = "₦120,000 / ₦300,000",
                    progressPercent = 0.4f
                ),
                onClick = {}
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WeeklyGoalsCardAchievedPreview() {
    StitchPadTheme {
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)
        ) {
            WeeklyGoalsCard(
                state = WeeklyGoalsCardState.Filled(
                    sectionLabel = "Ahead of target",
                    daysLeftLabel = "5 days left",
                    revenueLabel = "Revenue goal",
                    progressText = "₦590,000 / ₦500,000",
                    progressPercent = 1f,
                    achievedCtaLabel = "Raise your goal"
                ),
                onClick = {}
            )
        }
    }
}
// endregion

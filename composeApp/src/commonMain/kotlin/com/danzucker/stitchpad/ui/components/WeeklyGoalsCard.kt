package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_hero_steady

private val PROGRESS_BAR_HEIGHT = 8.dp
private val GOAL_BADGE_SIZE = 52.dp
private val WATERMARK_SIZE = 140.dp
private val WATERMARK_OFFSET_X = 24.dp
private const val PROGRESS_TRACK_ALPHA = 0.16f

/**
 * Two-state visual model for [WeeklyGoalsCard].
 *
 * [Empty] renders an outlined invitation to set the first goal.
 * [Filled] renders the active goal with progress bar and meta.
 *
 * Using a sealed interface (instead of nullable params) makes each state's
 * required fields explicit and prevents partially-populated cards.
 */
sealed interface WeeklyGoalsCardState {
    data class Empty(
        /** Pill chip above the title — e.g. "GET STARTED". */
        val sectionLabel: String,
        /** Big bold title — the empty card's reason for existing. */
        val title: String,
        /** One-line description shown beneath the title. */
        val supporting: String,
        /** Saffron CTA button label on the right. */
        val ctaLabel: String,
        /** Legacy field — kept for source compatibility, no longer rendered. */
        val label: String = title
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
 * Sits between [IllustratedFocusCard] and the rest of the dashboard, showing weekly
 * revenue progress against a target. Whole card is tappable — taps open the
 * goal-setup screen for both empty and filled states.
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
    val scheme = MaterialTheme.colorScheme

    val watermarkAlpha = if (isSystemInDarkTheme()) 0.10f else 0.08f

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Faint mannequin watermark on the right edge of the card.
            // Sits behind the content so the CTA button stays fully legible
            // on top. Slightly bigger than the card height + offset off the
            // right edge so it bleeds visually like the mockup.
            Image(
                painter = painterResource(Res.drawable.dashboard_hero_steady),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(WATERMARK_SIZE)
                    .offset(x = WATERMARK_OFFSET_X)
                    .alpha(watermarkAlpha),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                modifier = Modifier.padding(DesignTokens.space4)
            ) {
                GoalIconBadge()
                EmptyCardCopy(state = state, modifier = Modifier.weight(1f))
                EmptyCardCta(label = state.ctaLabel, onClick = onClick)
            }
        }
    }
}

@Composable
private fun GoalIconBadge() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(GOAL_BADGE_SIZE)
            .background(color = scheme.primary.copy(alpha = 0.12f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.GpsFixed,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun EmptyCardCopy(state: WeeklyGoalsCardState.Empty, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // "GET STARTED" pill chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.radiusSm))
                .background(color = scheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = DesignTokens.space2, vertical = 3.dp),
        ) {
            Text(
                text = state.sectionLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Text(
            text = state.supporting,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyCardCta(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = DesignTokens.space3,
            vertical = DesignTokens.space2,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(DesignTokens.space1))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(DesignTokens.iconInline),
        )
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
                    sectionLabel = "Get started",
                    title = "Set your first revenue goal",
                    supporting = "Start simple and track what you want to earn this month.",
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

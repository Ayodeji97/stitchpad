package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_hero_steady

private val PROGRESS_BAR_HEIGHT = 8.dp
private val GOAL_BADGE_SIZE = 52.dp
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
        val achievedCtaLabel: String? = null,
        /** Hero amount in the achieved layout, e.g. "₦700k". Rendered green. */
        val achievedAmountLabel: String? = null,
        /** Original target in the achieved layout, e.g. "₦300k". Rendered muted with strikethrough. */
        val achievedTargetLabel: String? = null,
        /** Muted line under the title, e.g. "Track your weekly earnings target." */
        val supporting: String? = null,
        /** Pill under the supporting line — green trend icon + "Stay consistent, keep growing". */
        val motivationLabel: String? = null,
        /** Caption under the amount, e.g. "10% of goal". */
        val progressPercentLabel: String? = null,
        /** Bottom-left footer beneath the progress bar, e.g. "₦100,000 earned". */
        val earnedLabel: String? = null,
        /** Bottom-right footer beneath the progress bar, e.g. "₦900,000 to go". */
        val toGoLabel: String? = null,
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

    val watermarkAlpha = if (LocalIsDarkTheme.current) 0.10f else 0.08f

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
            // matchParentSize() lets the Row's natural height dictate the
            // Box height — without this, the Image's fixed size was forcing
            // the card taller than its content and leaving a band of empty
            // padding above/below the actual copy.
            // ContentScale.Fit + alignment CenterEnd keeps the painter on
            // the right side, scaled to the card height, with the rest of
            // the Image bounds left transparent.
            Image(
                painter = painterResource(Res.drawable.dashboard_hero_steady),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .matchParentSize()
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
    val isAchieved = state.achievedCtaLabel != null
    if (isAchieved) {
        AchievedFilledCard(state = state, onClick = onClick, modifier = modifier)
    } else {
        InProgressFilledCard(state = state, onClick = onClick, modifier = modifier)
    }
}

/**
 * Rich in-progress layout: icon column + center copy + right amount stack,
 * progress bar full-width, "earned / to go" footer underneath. Fields like
 * [WeeklyGoalsCardState.Filled.supporting] and [earnedLabel] are optional —
 * each renders only when non-null so the legacy short form (sectionLabel +
 * revenueLabel + progressText + progressBar) still works as a fallback.
 */
@Composable
private fun InProgressFilledCard(
    state: WeeklyGoalsCardState.Filled,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                GoalIconBadge()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = state.sectionLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.revenueLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    if (state.supporting != null) {
                        Text(
                            text = state.supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                ) {
                    DaysLeftPill(label = state.daysLeftLabel, tint = accent)
                    Text(
                        text = state.progressText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    if (state.progressPercentLabel != null) {
                        Text(
                            text = state.progressPercentLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                        )
                    }
                    // Motivation pill belongs to the progress story —
                    // ₦100k/₦300k → 33% of goal → You're on track. Living
                    // in the right column groups the three pace signals
                    // vertically. The right column's natural width is set
                    // by the title-large progress text (~240dp), which is
                    // wider than the pill itself, so it doesn't wrap.
                    if (state.motivationLabel != null) {
                        MotivationPill(label = state.motivationLabel)
                    }
                }
            }
            ProgressBar(percent = state.progressPercent, color = accent)
            if (state.earnedLabel != null || state.toGoLabel != null) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.earnedLabel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.toGoLabel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Calendar-icon pill on the right side of the in-progress card header. */
@Composable
private fun DaysLeftPill(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = tint.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space2,
                vertical = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

/** Trend-up icon + motivational copy, sat on a muted dark surface. */
@Composable
private fun MotivationPill(label: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = scheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space2,
                vertical = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = DesignTokens.success500,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface,
            )
        }
    }
}

/**
 * Achieved-state hero layout. Trophy badge + AHEAD OF TARGET label and a
 * brand-primary days-left pill in the header, then "Revenue goal" beside the
 * green hero amount with the original target struck through, then the bar,
 * then "Raise your goal →" — preserving momentum at the moment the user
 * has earned more.
 */
@Composable
private fun AchievedFilledCard(
    state: WeeklyGoalsCardState.Filled,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val scheme = MaterialTheme.colorScheme
    val accent = DesignTokens.success500

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space4)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AchievedTrophyChip(label = state.sectionLabel, tint = accent)
                DaysLeftPill(label = state.daysLeftLabel, tint = scheme.primary)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.revenueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                AchievedAmountText(
                    amountLabel = state.achievedAmountLabel ?: state.progressText,
                    targetLabel = state.achievedTargetLabel,
                    accent = accent,
                    muted = scheme.onSurfaceVariant
                )
            }
            ProgressBar(percent = state.progressPercent, color = accent)
            if (state.achievedCtaLabel != null) {
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
private fun AchievedTrophyChip(label: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color = tint.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun AchievedAmountText(
    amountLabel: String,
    targetLabel: String?,
    accent: Color,
    muted: Color
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        if (targetLabel != null) {
            Text(
                text = "/ $targetLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = muted,
                textDecoration = TextDecoration.LineThrough
            )
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
                    daysLeftLabel = "1 day left",
                    revenueLabel = "Revenue goal",
                    progressText = "₦100k / ₦1m",
                    progressPercent = 0.10f,
                    supporting = "Track your weekly earnings target.",
                    motivationLabel = "Stay consistent, keep growing",
                    progressPercentLabel = "10% of goal",
                    earnedLabel = "₦100,000 earned",
                    toGoLabel = "₦900,000 to go",
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
                    progressText = "₦700k / ₦300k",
                    progressPercent = 1f,
                    achievedCtaLabel = "Raise your goal",
                    achievedAmountLabel = "₦700k",
                    achievedTargetLabel = "₦300k"
                ),
                onClick = {}
            )
        }
    }
}
// endregion

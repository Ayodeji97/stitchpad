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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val ICON_CONTAINER_SIZE = 44.dp

private const val ICON_BG_ALPHA = 0.12f
private const val BORDER_ALPHA = 0.25f

/**
 * The always-on adaptive header card on the dashboard. Replaces the legacy
 * "All clear for today" banner with a state-aware narrative anchor — different
 * accent color, icon, and copy depending on whether the day is busy, quiet, etc.
 *
 * The whole card is tappable. The inline [ctaLabel] arrow is the only tap-to-act
 * signal; there is no trailing chevron. Accent color carries the variant message
 * (red for urgent days, green for calm, etc.) so the user reads the state
 * without parsing copy first.
 *
 * Pass [containerBrush] for variants that benefit from a gradient background
 * (e.g. urgent / earn) — leave null for the default surface background.
 *
 * @param icon Leading icon shown in a tinted square container.
 * @param accentColor Drives icon container, icon tint, border, and CTA text.
 * @param headline 1-2 line title.
 * @param onClick Invoked on tap of the whole card.
 * @param supporting Optional secondary line under the headline.
 * @param ctaLabel Optional inline call-to-action shown with a forward arrow.
 * @param containerBrush Optional gradient background; null = solid surface.
 */
@Composable
fun FocusTodayCard(
    icon: ImageVector,
    accentColor: Color,
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    ctaLabel: String? = null,
    containerBrush: Brush? = null
) {
    val iconContainerColor = accentColor.copy(alpha = ICON_BG_ALPHA)
    val borderColor = accentColor.copy(alpha = BORDER_ALPHA)
    val shape = RoundedCornerShape(DesignTokens.radiusLg)

    Surface(
        shape = shape,
        color = if (containerBrush == null) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier
                .then(
                    if (containerBrush != null) Modifier.background(containerBrush) else Modifier
                )
                .padding(DesignTokens.space4)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ICON_CONTAINER_SIZE)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(iconContainerColor)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(DesignTokens.iconNav)
                )
            }
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (supporting != null) {
                    Spacer(Modifier.size(width = 0.dp, height = 4.dp))
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ctaLabel != null) {
                    Spacer(Modifier.size(width = 0.dp, height = DesignTokens.space2))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
                    ) {
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(DesignTokens.iconInline)
                        )
                    }
                }
            }
        }
    }
}

// region — Previews
// Each preview shows the card configured for one of the dashboard's five variants
// (FirstOrder / Quiet / Steady / Earn / Focus), demonstrating how the same
// composable adapts to vastly different states purely through param changes.

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FocusTodayCardFirstOrderPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            FocusTodayCard(
                icon = Icons.Filled.RocketLaunch,
                accentColor = DesignTokens.info500,
                headline = "One customer added. Turn them into your first order.",
                ctaLabel = "Create order for Ola Kunle",
                onClick = {}
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FocusTodayCardQuietPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            FocusTodayCard(
                icon = Icons.Filled.Spa,
                accentColor = DesignTokens.success500,
                headline = "No deadlines today.",
                supporting = "Reconnect with a past customer.",
                ctaLabel = "Message Mrs Adebayo",
                onClick = {}
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FocusTodayCardSteadyPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            FocusTodayCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accentColor = DesignTokens.info500,
                headline = "Shop is steady.",
                supporting = "5 orders moving",
                ctaLabel = "Open next order",
                onClick = {}
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FocusTodayCardEarnPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            FocusTodayCard(
                icon = Icons.Filled.Savings,
                accentColor = DesignTokens.primary600,
                headline = "3 ways to earn today.",
                supporting = "Start with Mrs Folake",
                ctaLabel = "Take action",
                containerBrush = Brush.linearGradient(
                    colors = listOf(
                        DesignTokens.primary50,
                        DesignTokens.primary50.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                onClick = {}
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FocusTodayCardFocusPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            FocusTodayCard(
                icon = Icons.Filled.PriorityHigh,
                accentColor = DesignTokens.error500,
                headline = "5 need attention today.",
                supporting = "2 overdue · 3 due today",
                ctaLabel = "Open Mr Kola",
                containerBrush = Brush.linearGradient(
                    colors = listOf(
                        DesignTokens.error50,
                        DesignTokens.error50.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                onClick = {}
            )
        }
    }
}
// endregion

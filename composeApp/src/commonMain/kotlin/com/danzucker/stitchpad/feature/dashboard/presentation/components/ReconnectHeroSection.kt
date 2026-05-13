package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_hero_quiet
import stitchpad.composeapp.generated.resources.dashboard_reconnect_card_last_order
import stitchpad.composeapp.generated.resources.dashboard_reconnect_count
import stitchpad.composeapp.generated.resources.dashboard_reconnect_label
import stitchpad.composeapp.generated.resources.dashboard_reconnect_send_message
import stitchpad.composeapp.generated.resources.dashboard_reconnect_subtitle
import stitchpad.composeapp.generated.resources.dashboard_reconnect_view_all
import stitchpad.composeapp.generated.resources.dashboard_reconnect_view_customer
import stitchpad.composeapp.generated.resources.dashboard_reconnect_worth_following_up

private val HEADER_BADGE_SIZE = 52.dp
private val CARD_AVATAR_SIZE = 56.dp
private const val MAX_CARDS_ON_DASHBOARD = 3
private const val WATERMARK_ALPHA_DARK = 0.10f
private const val WATERMARK_ALPHA_LIGHT = 0.08f

private val WHATSAPP_GREEN = Color(0xFF2D9E6B)
private val WHATSAPP_GREEN_DARK = Color(0xFF5EDBA0)

/**
 * Reconnect section as a horizontally-paged hero carousel of up to
 * [MAX_CARDS_ON_DASHBOARD] candidates. Renders nothing when [candidates] is empty.
 *
 * The header pill always reflects the *full* candidate count, not just what
 * fits in the carousel — tapping it routes via [onViewAllClick] to the full
 * reconnect list/screen for the rest.
 */
@Composable
fun ReconnectHeroSection(
    candidates: List<ReconnectCandidate>,
    onCardClick: (customerId: String) -> Unit,
    onMessageClick: (customerId: String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    val displayed = candidates.take(MAX_CARDS_ON_DASHBOARD)
    val pagerState = rememberPagerState(pageCount = { displayed.size })

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        ReconnectHeader(
            count = candidates.size,
            onViewAllClick = onViewAllClick,
        )
        HorizontalPager(
            state = pagerState,
            pageSpacing = DesignTokens.space3,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val candidate = displayed[page]
            ReconnectHeroCard(
                candidate = candidate,
                onCardClick = { onCardClick(candidate.customerId) },
                onMessageClick = { onMessageClick(candidate.customerId) },
            )
        }
        if (displayed.size > 1) {
            PageDots(
                count = displayed.size,
                activeIndex = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ReconnectHeader(count: Int, onViewAllClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(HEADER_BADGE_SIZE)
                .background(color = scheme.primary.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.dashboard_reconnect_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.dashboard_reconnect_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CountPill(count = count, onClick = onViewAllClick)
    }
}

@Composable
private fun CountPill(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(Res.string.dashboard_reconnect_view_all)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(color = scheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = DesignTokens.space3, vertical = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(Res.string.dashboard_reconnect_count, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ReconnectHeroCard(
    candidate: ReconnectCandidate,
    onCardClick: () -> Unit,
    onMessageClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val isDark = LocalIsDarkTheme.current
    val watermarkAlpha = if (isDark) WATERMARK_ALPHA_DARK else WATERMARK_ALPHA_LIGHT
    val whatsappGreen = if (isDark) WHATSAPP_GREEN_DARK else WHATSAPP_GREEN

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onCardClick, role = Role.Button),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Mannequin watermark on right edge — same pattern as WeeklyGoalsCard empty.
            Image(
                painter = painterResource(Res.drawable.dashboard_hero_quiet),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(watermarkAlpha),
            )
            Column(
                modifier = Modifier.padding(DesignTokens.space4),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                ) {
                    DashedAvatar(initials = initialsOf(candidate.customerName))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = candidate.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                Res.string.dashboard_reconnect_card_last_order,
                                candidate.daysSinceLastInteraction,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(DesignTokens.space1))
                        WorthFollowingUpPill()
                    }
                }
                CardActions(
                    whatsappGreen = whatsappGreen,
                    onMessageClick = onMessageClick,
                    onViewClick = onCardClick,
                )
            }
        }
    }
}

@Composable
private fun DashedAvatar(initials: String) {
    val scheme = MaterialTheme.colorScheme
    val ringColor = scheme.primary
    val bgColor = scheme.primary.copy(alpha = 0.14f)
    val strokeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 1.5.dp.toPx() }
    val dashOnPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    val dashOffPx = with(androidx.compose.ui.platform.LocalDensity.current) { 3.dp.toPx() }

    Box(
        modifier = Modifier
            .size(CARD_AVATAR_SIZE)
            .background(color = bgColor, shape = CircleShape)
            .drawBehind {
                drawCircle(
                    color = ringColor,
                    radius = (size.minDimension / 2f) - (strokeWidthPx / 2f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashOnPx, dashOffPx),
                            0f,
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ringColor,
        )
    }
}

@Composable
private fun WorthFollowingUpPill() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(color = scheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(Res.string.dashboard_reconnect_worth_following_up),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
    }
}

@Composable
private fun CardActions(
    whatsappGreen: Color,
    onMessageClick: () -> Unit,
    onViewClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = onMessageClick,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            border = BorderStroke(1.dp, whatsappGreen),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = whatsappGreen),
            contentPadding = PaddingValues(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space2,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
            Spacer(Modifier.size(DesignTokens.space1))
            Text(
                text = stringResource(Res.string.dashboard_reconnect_send_message),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(
            onClick = onViewClick,
            contentPadding = PaddingValues(
                horizontal = DesignTokens.space2,
                vertical = DesignTokens.space2,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
            Spacer(Modifier.size(DesignTokens.space1))
            Text(
                text = stringResource(Res.string.dashboard_reconnect_view_customer),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, activeIndex: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == activeIndex
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .background(
                        color = if (active) scheme.primary else scheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// region — Previews

private fun samplePreviewCandidates(): List<ReconnectCandidate> = listOf(
    ReconnectCandidate(
        customerId = "c1",
        customerName = "Adaeze Okoro",
        customerPhone = "+2348012345678",
        daysSinceLastInteraction = 45,
        hasOrderHistory = true,
    ),
    ReconnectCandidate(
        customerId = "c2",
        customerName = "Blessing Tosin",
        customerPhone = "+2348023456789",
        daysSinceLastInteraction = 62,
        hasOrderHistory = true,
    ),
    ReconnectCandidate(
        customerId = "c3",
        customerName = "Mrs Adebayo",
        customerPhone = "+2348034567890",
        daysSinceLastInteraction = 90,
        hasOrderHistory = false,
    ),
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReconnectHeroSectionLightPreview() {
    StitchPadTheme {
        Surface(color = Color.Transparent) {
            ReconnectHeroSection(
                candidates = samplePreviewCandidates() + samplePreviewCandidates().take(5),
                onCardClick = {},
                onMessageClick = {},
                onViewAllClick = {},
                modifier = Modifier.padding(DesignTokens.space4),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReconnectHeroSectionDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(color = Color.Transparent) {
            ReconnectHeroSection(
                candidates = samplePreviewCandidates(),
                onCardClick = {},
                onMessageClick = {},
                onViewAllClick = {},
                modifier = Modifier.padding(DesignTokens.space4),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReconnectHeroSectionSinglePreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(color = Color.Transparent) {
            ReconnectHeroSection(
                candidates = samplePreviewCandidates().take(1),
                onCardClick = {},
                onMessageClick = {},
                onViewAllClick = {},
                modifier = Modifier.padding(DesignTokens.space4),
            )
        }
    }
}
// endregion

package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reconnect_last_order_days
import stitchpad.composeapp.generated.resources.reconnect_no_orders_yet
import stitchpad.composeapp.generated.resources.reconnect_say_hi
import stitchpad.composeapp.generated.resources.reconnect_section
import stitchpad.composeapp.generated.resources.reconnect_send_hello

private val CARD_WIDTH = 220.dp
private val AVATAR_SIZE = 36.dp
private const val AVATAR_BG_ALPHA = 0.14f

/** Visual style for [ReconnectStrip] — picks card-row vs pill-row layout. */
enum class ReconnectStripStyle {
    /** Full card row with avatar, name, last-order meta, and "Send a hello" hint. Used on S2/S3. */
    Cards,

    /** Compact pill chips (avatar + name only). Used on S4 to keep pipeline focus. */
    Pills
}

/**
 * Horizontal row of past customers prompting the user to reconnect via WhatsApp.
 * Renders nothing when [candidates] is empty.
 *
 * The CTA is a single tap on a card/pill — the consumer is expected to launch the
 * WhatsApp template; on iOS the launch must respect the modal-bottom-sheet timing
 * delay (handled at the call site, not here).
 */
@Composable
fun ReconnectStrip(
    candidates: List<ReconnectCandidate>,
    style: ReconnectStripStyle,
    onCandidateClick: (ReconnectCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
    ) {
        Text(
            text = stringResource(Res.string.reconnect_section).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            contentPadding = PaddingValues(horizontal = DesignTokens.space4)
        ) {
            items(candidates, key = { it.customerId }) { candidate ->
                when (style) {
                    ReconnectStripStyle.Cards -> ReconnectCard(
                        candidate = candidate,
                        onClick = { onCandidateClick(candidate) }
                    )
                    ReconnectStripStyle.Pills -> ReconnectPill(
                        candidate = candidate,
                        onClick = { onCandidateClick(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReconnectCard(candidate: ReconnectCandidate, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val accent = MaterialTheme.colorScheme.primary
    val meta = if (candidate.hasOrderHistory) {
        stringResource(Res.string.reconnect_last_order_days, candidate.daysSinceLastInteraction)
    } else {
        stringResource(Res.string.reconnect_no_orders_yet)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                Avatar(name = candidate.customerName)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.customerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.space1))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
            ) {
                Icon(
                    imageVector = Icons.Filled.WavingHand,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(DesignTokens.iconInline)
                )
                Text(
                    text = stringResource(Res.string.reconnect_send_hello),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun ReconnectPill(candidate: ReconnectCandidate, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.radiusFull)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.padding(
                start = DesignTokens.space2,
                end = DesignTokens.space3,
                top = DesignTokens.space1,
                bottom = DesignTokens.space1
            )
        ) {
            Avatar(name = candidate.customerName, size = 28.dp)
            Text(
                text = candidate.customerName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(Res.string.reconnect_say_hi),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = AVATAR_SIZE) {
    val accent = MaterialTheme.colorScheme.primary
    val initials = name.split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(accent.copy(alpha = AVATAR_BG_ALPHA))
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

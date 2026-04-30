package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_reconnect_label

private val LEADING_ICON_SIZE = 14.dp

/**
 * Horizontally-scrollable row of pill-shaped reconnect candidate chips.
 * Renders nothing when [candidates] is empty.
 *
 * Intended for the bottom of the V2 dashboard above the bottom nav.
 * Tapping a chip fires [onCandidateClick] with the [ReconnectCandidate.customerId];
 * the trailing chevron fires [onMoreClick] to open the full reconnect list.
 */
@Composable
fun ReconnectChipStrip(
    candidates: List<ReconnectCandidate>,
    onCandidateClick: (customerId: String) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DesignTokens.space3, end = DesignTokens.space2, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            // Leading "Reconnect" label with person icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(LEADING_ICON_SIZE),
                )
                Text(
                    text = stringResource(Res.string.dashboard_reconnect_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Scrollable chip row
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                items(items = candidates, key = { it.customerId }) { candidate ->
                    ReconnectChip(
                        candidate = candidate,
                        onClick = { onCandidateClick(candidate.customerId) },
                    )
                }
            }
            // Trailing chevron → opens full reconnect list
            IconButton(
                onClick = onMoreClick,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReconnectChip(candidate: ReconnectCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick, role = Role.Button)
            .minimumInteractiveComponentSize()
            .padding(horizontal = DesignTokens.space2, vertical = DesignTokens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = candidate.customerName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "· ${formatDaysAgo(candidate.daysSinceLastInteraction)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Formats [days] into a compact relative-time label, e.g. "45d" or "2mo". */
private fun formatDaysAgo(days: Int): String = when {
    days < 30 -> "${days}d"
    days < 365 -> "${days / 30}mo"
    else -> "${days / 365}y"
}

private fun sampleCandidates(): List<ReconnectCandidate> = listOf(
    ReconnectCandidate(
        customerId = "c1",
        customerName = "Adeyinka Paul",
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
private fun ReconnectChipStripPreview() {
    StitchPadTheme {
        ReconnectChipStrip(
            candidates = sampleCandidates(),
            onCandidateClick = {},
            onMoreClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReconnectChipStripDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        ReconnectChipStrip(
            candidates = sampleCandidates(),
            onCandidateClick = {},
            onMoreClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReconnectChipStripEmptyPreview() {
    StitchPadTheme {
        ReconnectChipStrip(
            candidates = emptyList(),
            onCandidateClick = {},
            onMoreClick = {},
        )
    }
}

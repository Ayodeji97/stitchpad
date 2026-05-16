package com.danzucker.stitchpad.feature.smart.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.feature.smart.presentation.components.FreeTierCounterChip
import com.danzucker.stitchpad.feature.smart.presentation.components.IntentTile
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_intent_coming_soon_label
import stitchpad.composeapp.generated.resources.smart_intent_draft_message_subtitle
import stitchpad.composeapp.generated.resources.smart_intent_draft_message_title
import stitchpad.composeapp.generated.resources.smart_intent_price_this_title
import stitchpad.composeapp.generated.resources.smart_intent_reply_helper_title
import stitchpad.composeapp.generated.resources.smart_section_subtitle
import stitchpad.composeapp.generated.resources.smart_section_title

/**
 * Always-on Dashboard section card. Hidden by the caller when the customer
 * list is empty (no dead-end taps).
 *
 * V1: 1 enabled tile (Draft Message), 2 grayed "Coming soon" placeholders.
 */
@Composable
fun SmartSectionCard(
    remainingFreeQuota: Int?,
    onDraftMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation1,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.smart_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.smart_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (remainingFreeQuota != null) {
                    FreeTierCounterChip(remaining = remainingFreeQuota)
                }
            }
            Spacer(Modifier.height(DesignTokens.space3))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                // Right padding so the third tile peeks ~24dp at the edge
                // and the row reads as horizontally scrollable instead of
                // looking like a 2-tile layout with one tile hidden.
                contentPadding = PaddingValues(end = DesignTokens.space6),
            ) {
                item {
                    IntentTile(
                        title = stringResource(Res.string.smart_intent_draft_message_title),
                        subtitle = stringResource(Res.string.smart_intent_draft_message_subtitle),
                        icon = Icons.Outlined.AutoAwesome,
                        enabled = true,
                        onClick = onDraftMessageClick,
                    )
                }
                item {
                    IntentTile(
                        title = stringResource(Res.string.smart_intent_price_this_title),
                        subtitle = stringResource(Res.string.smart_intent_coming_soon_label),
                        icon = Icons.Outlined.LocalOffer,
                        enabled = false,
                        onClick = {},
                    )
                }
                item {
                    IntentTile(
                        title = stringResource(Res.string.smart_intent_reply_helper_title),
                        subtitle = stringResource(Res.string.smart_intent_coming_soon_label),
                        icon = Icons.Outlined.Reply,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun SmartSectionCardWithQuotaPreview() {
    StitchPadTheme {
        SmartSectionCard(remainingFreeQuota = 5, onDraftMessageClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun SmartSectionCardPremiumPreview() {
    StitchPadTheme {
        SmartSectionCard(remainingFreeQuota = null, onDraftMessageClick = {})
    }
}

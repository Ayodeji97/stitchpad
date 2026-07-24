package com.danzucker.stitchpad.feature.smart.presentation

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.smart.presentation.components.FreeTierCounterChip
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_intent_draft_message_subtitle
import stitchpad.composeapp.generated.resources.smart_intent_draft_message_title
import stitchpad.composeapp.generated.resources.smart_section_subtitle
import stitchpad.composeapp.generated.resources.smart_section_title

/**
 * Always-on Dashboard section card. Hidden by the caller when the customer
 * list is empty (no dead-end taps).
 *
 * V2: a single Draft Message action row. The two "Coming soon" placeholder
 * tiles were removed so the card stops reading as a multi-feature hub when
 * it is really one action. Draft Message is the ONLY UI entry point to the
 * Smart draft feature, so this card must not be removed outright.
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
            DraftMessageRow(onClick = onDraftMessageClick)
        }
    }
}

@Composable
private fun DraftMessageRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.smart_intent_draft_message_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(Res.string.smart_intent_draft_message_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
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

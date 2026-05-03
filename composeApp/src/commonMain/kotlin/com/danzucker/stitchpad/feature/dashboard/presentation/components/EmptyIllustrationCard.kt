package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val ILLUSTRATION_SIZE = 72.dp
private val ILLUSTRATION_BACKDROP_SIZE = 96.dp
private val ILLUSTRATION_BACKDROP_WIDTH_LARGE = 128.dp
private val ILLUSTRATION_SIZE_LARGE = 96.dp

enum class EmptyCardCtaStyle { Text, OutlinedPill }

@Composable
fun EmptyIllustrationCard(
    slot: EmptyIllustrationSlot,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCtaClick: () -> Unit = {},
    illustrationBackground: Color? = null,
    largeIllustration: Boolean = false,
    ctaStyle: EmptyCardCtaStyle = EmptyCardCtaStyle.Text,
) {
    val drawable = remember(slot) { emptyIllustrationFor(slot) }
    val illustrationSize = if (largeIllustration) ILLUSTRATION_SIZE_LARGE else ILLUSTRATION_SIZE
    val backdropWidth = if (largeIllustration) ILLUSTRATION_BACKDROP_WIDTH_LARGE else ILLUSTRATION_BACKDROP_SIZE
    val backdropHeight = ILLUSTRATION_BACKDROP_SIZE
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            if (illustrationBackground != null) {
                Box(
                    modifier = Modifier
                        .size(width = backdropWidth, height = backdropHeight)
                        .background(
                            color = illustrationBackground,
                            shape = RoundedCornerShape(DesignTokens.radiusLg),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DashboardIllustration(drawable = drawable, size = illustrationSize)
                }
            } else {
                DashboardIllustration(drawable = drawable, size = illustrationSize)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(DesignTokens.space1))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ctaLabel != null) {
                    Spacer(Modifier.height(DesignTokens.space2))
                    when (ctaStyle) {
                        EmptyCardCtaStyle.Text -> TextButton(
                            onClick = onCtaClick,
                            contentPadding = PaddingValues(
                                horizontal = DesignTokens.space2,
                                vertical = 4.dp,
                            ),
                        ) {
                            Text(
                                text = ctaLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        EmptyCardCtaStyle.OutlinedPill -> OutlinedButton(
                            onClick = onCtaClick,
                            shape = RoundedCornerShape(DesignTokens.radiusFull),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = DesignTokens.space3,
                                vertical = DesignTokens.space1,
                            ),
                        ) {
                            Text(
                                text = ctaLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun EmptyIllustrationCardPipelinePreview() {
    StitchPadTheme {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Pipeline,
            title = "No work in flight yet",
            supporting = "When you create orders, they'll appear here.",
            ctaLabel = "Create order",
            onCtaClick = {},
        )
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun EmptyIllustrationCardNbaPreview() {
    StitchPadTheme {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Nba,
            title = "Nothing urgent right now",
            supporting = "Use this time to reconnect with customers or review orders.",
            ctaLabel = "Reconnect customers",
            onCtaClick = {},
            ctaStyle = EmptyCardCtaStyle.OutlinedPill,
            largeIllustration = true,
        )
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun EmptyIllustrationCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Pipeline,
            title = "No work in flight yet",
            supporting = "When you create orders, they'll appear here.",
            ctaLabel = "Create order",
            onCtaClick = {},
        )
    }
}

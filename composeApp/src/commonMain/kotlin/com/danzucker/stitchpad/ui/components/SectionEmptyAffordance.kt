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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val ICON_CONTAINER_SIZE = 40.dp
private const val ICON_BG_ALPHA = 0.10f

/**
 * Mini-card empty state for dashboard sections (NBA, Pipeline) so they never
 * render as bare whitespace. Whole card is tappable; the inline arrow on
 * [ctaLabel] is the only tap-to-act signal.
 *
 * Used by `NextBestActionsSection` when its underlying
 * data is empty — keeping the dashboard consistently scannable across all states.
 */
@Composable
fun SectionEmptyAffordance(
    icon: ImageVector,
    title: String,
    supporting: String,
    ctaLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val accent = MaterialTheme.colorScheme.primary
    val iconContainer = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ICON_BG_ALPHA)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space4)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ICON_CONTAINER_SIZE)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(iconContainer)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(DesignTokens.iconList)
                )
            }
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(width = 0.dp, height = 4.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(width = 0.dp, height = DesignTokens.space2))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
                ) {
                    Text(
                        text = ctaLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
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

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SectionEmptyAffordancePreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            SectionEmptyAffordance(
                icon = Icons.Filled.Inbox,
                title = "Pipeline empty",
                supporting = "Start the next order to see it move from pending → in progress → ready.",
                ctaLabel = "Start an order",
                onClick = {}
            )
        }
    }
}

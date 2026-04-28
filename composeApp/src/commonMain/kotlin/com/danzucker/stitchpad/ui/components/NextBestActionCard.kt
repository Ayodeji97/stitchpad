package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

private val CARD_WIDTH = 280.dp
private val ACCENT_STRIP_WIDTH = 4.dp
private const val ACCENT_ICON_ALPHA = 0.85f

@Composable
fun NextBestActionCard(
    accent: Color,
    accentBackground: Color,
    icon: ImageVector,
    typeLabel: String,
    customerName: String,
    primaryLine: String,
    secondaryLine: String,
    ctaLabel: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(ACCENT_STRIP_WIDTH)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space4),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = typeLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(DesignTokens.space2))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent.copy(alpha = ACCENT_ICON_ALPHA),
                        modifier = Modifier.size(DesignTokens.iconList)
                    )
                }
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = primaryLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = secondaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(DesignTokens.space2))
                CtaPill(label = ctaLabel, accent = accent, accentBackground = accentBackground)
            }
        }
    }
}

@Composable
private fun CtaPill(label: String, accent: Color, accentBackground: Color) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = accentBackground
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space2
            )
        )
    }
}

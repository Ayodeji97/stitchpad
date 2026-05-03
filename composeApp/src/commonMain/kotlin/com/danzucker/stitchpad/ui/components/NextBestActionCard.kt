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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

private const val ACCENT_BORDER_ALPHA = 0.4f
private val ACCENT_BAR_WIDTH = 4.dp

@Composable
fun NextBestActionCard(
    accent: Color,
    accentBackground: Color,
    typeIcon: ImageVector,
    ctaIcon: ImageVector,
    typeLabel: String,
    customerName: String,
    primaryLine: String,
    secondaryLine: String,
    ctaLabel: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Optional secondary text-link action ("Open order →") rendered on the
     * right of the CTA pill row. When non-null, [onSecondaryClick] is wired
     * to its own tap target so the user can route to the order detail
     * without firing the primary action. Used by the money-collecting NBA
     * types where Send-reminder is primary and viewing the order is the
     * meaningful escape hatch.
     */
    secondaryActionLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = ACCENT_BORDER_ALPHA)),
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            // Left vertical accent bar — full card height, tinted to the
            // type's urgency colour. Mirrors AccentedOrderRow's pattern so
            // the dashboard reads with one consistent "urgency stripe"
            // language across triage rows and NBA cards.
            Box(
                modifier = Modifier
                    .width(ACCENT_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space4),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                TypePill(
                    icon = typeIcon,
                    label = typeLabel,
                    accent = accent,
                    accentBackground = accentBackground,
                )
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = primaryLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secondaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(DesignTokens.space2))
                FooterDivider()
                Spacer(Modifier.height(DesignTokens.space2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CtaPill(
                        icon = ctaIcon,
                        label = ctaLabel,
                        accent = accent,
                        accentBackground = accentBackground,
                    )
                    if (secondaryActionLabel != null && onSecondaryClick != null) {
                        SecondaryAction(
                            label = secondaryActionLabel,
                            accent = accent,
                            onClick = onSecondaryClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePill(
    icon: ImageVector,
    label: String,
    accent: Color,
    accentBackground: Color,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = accentBackground,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FooterDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun CtaPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    accentBackground: Color,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = accentBackground,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space2,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(
                horizontal = DesignTokens.space2,
                vertical = DesignTokens.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(DesignTokens.iconInline),
        )
    }
}

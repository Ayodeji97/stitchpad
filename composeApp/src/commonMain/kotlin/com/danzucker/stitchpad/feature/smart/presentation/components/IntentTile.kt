package com.danzucker.stitchpad.feature.smart.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Single tile inside the SmartSectionCard.
 * - enabled: filled primaryContainer, tap → onClick
 * - disabled: surfaceVariant, "Coming soon" subtitle
 */
@Composable
fun IntentTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Disabled tiles use onSurfaceVariant for the subtitle (already a
    // contrast-tuned secondary). Enabled tiles drop alpha on the primary
    // pair to read as a quieter supporting line.
    val subtitleColor = if (enabled) onContainer.copy(alpha = 0.7f) else onContainer

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = container,
        modifier = modifier
            .width(160.dp)
            .heightIn(min = 120.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Icon(imageVector = icon, contentDescription = null, tint = onContainer)
            Spacer(Modifier.height(DesignTokens.space2))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = onContainer)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }
    }
}

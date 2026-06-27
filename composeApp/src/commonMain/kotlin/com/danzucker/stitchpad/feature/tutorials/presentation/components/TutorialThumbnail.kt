package com.danzucker.stitchpad.feature.tutorials.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.tutorials_duration_fmt

/**
 * Branded placeholder thumbnail for a tutorial clip: an indigo→sienna gradient tile with a
 * centered play glyph and an optional duration badge. v1 uses this in place of remote poster
 * stills (which would each cost a Storage download-URL round-trip) so the list is offline-safe.
 */
@Composable
fun TutorialThumbnail(
    durationSec: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
        if (durationSec > 0) {
            Text(
                text = stringResource(
                    Res.string.tutorials_duration_fmt,
                    durationSec / 60,
                    (durationSec % 60).toString().padStart(2, '0'),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(DesignTokens.space1)
                    .clip(RoundedCornerShape(DesignTokens.radiusSm))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
            )
        }
    }
}

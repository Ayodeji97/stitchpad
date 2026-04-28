package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens

val TileValueDefault: TextUnit = 26.sp
val TileValueCurrency: TextUnit = 20.sp

private const val TILE_ACCENT_ICON_ALPHA = 0.5f
private val TILE_ICON_SIZE = 18.dp
private val TILE_ICON_INSET = 10.dp

@Composable
fun Tile(
    icon: ImageVector,
    valueText: String,
    labelText: String,
    accent: Color,
    background: Color,
    border: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = TileValueDefault
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent.copy(alpha = TILE_ACCENT_ICON_ALPHA),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(TILE_ICON_INSET)
                    .size(TILE_ICON_SIZE)
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space4,
                    vertical = DesignTokens.space5
                )
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = valueFontSize),
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = labelText.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

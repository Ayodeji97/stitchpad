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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val ICON_CONTAINER_SIZE = 36.dp
private const val ICON_BG_ALPHA = 0.12f

/**
 * One tile in the QuickStart row. Compact: icon + single label, no description.
 *
 * Bundled into [QuickStartTiles] for the FirstCustomer dashboard state — the
 * single-label form keeps the row scannable on small screens while the parent
 * surface takes the full call-to-action visual weight.
 */
data class QuickStartTile(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * Three compact action tiles shown on the FirstCustomer dashboard state. Each
 * tile is a square-ish card with icon + label, equal weight in the row.
 *
 * Use for the "1+ customer, 0 orders" state where the user needs a clear nudge
 * toward their first revenue-driving action.
 */
@Composable
fun QuickStartTiles(
    tiles: List<QuickStartTile>,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier.fillMaxWidth()
    ) {
        tiles.forEach { tile ->
            QuickStartTileCard(
                tile = tile,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickStartTileCard(
    tile: QuickStartTile,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(shape)
            .clickable(onClick = tile.onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space4
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ICON_CONTAINER_SIZE)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(accent.copy(alpha = ICON_BG_ALPHA))
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(DesignTokens.iconList)
                )
            }
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun QuickStartTilesPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
            QuickStartTiles(
                tiles = listOf(
                    QuickStartTile(Icons.AutoMirrored.Filled.ReceiptLong, "Create first order") {},
                    QuickStartTile(Icons.Filled.PersonAdd, "Add customer") {},
                    QuickStartTile(Icons.Filled.Straighten, "Add measurement") {}
                )
            )
        }
    }
}

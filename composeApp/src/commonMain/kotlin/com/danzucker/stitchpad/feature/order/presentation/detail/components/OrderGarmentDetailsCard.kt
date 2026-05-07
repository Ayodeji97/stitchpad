package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_add_fabric
import stitchpad.composeapp.generated.resources.order_detail_add_fabric_name
import stitchpad.composeapp.generated.resources.order_detail_fabric_caption
import stitchpad.composeapp.generated.resources.order_detail_garment_section

private val FABRIC_THUMBNAIL_SIZE = 128.dp
private val FABRIC_PLACEHOLDER_SIZE = 96.dp

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>,
    priority: OrderPriority,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                SectionIconTile(
                    imageVector = Icons.Default.Checkroom,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.order_detail_garment_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            // Per-item rows. Multi-item rows are stacked with space3 between them.
            // The priority pill anchors to the bottom of the FIRST row's left column,
            // filling the dead space next to the 128dp fabric thumbnail.
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(DesignTokens.space3))
                }
                val isFirstItemNeedingFabric = items.indexOfFirst { needsFabricInfo(it) } == index
                val showCta = needsFabricInfo(item) && isFirstItemNeedingFabric
                GarmentItemRow(
                    item = item,
                    onAddFabricPhotoClick = if (showCta) onAddFabricPhotoClick else null,
                    onAddFabricNameClick = if (showCta) onAddFabricNameClick else null,
                    priority = if (index == 0) priority else OrderPriority.NORMAL,
                )
            }
        }
    }
}

@Composable
private fun GarmentItemRow(
    item: OrderItem,
    onAddFabricPhotoClick: (() -> Unit)?,
    onAddFabricNameClick: (() -> Unit)?,
    priority: OrderPriority,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = garmentDisplayName(item.garmentType),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!item.fabricName.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.fabricName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Currently OrderItem has no quantity field; OrderForm always emits qty=1.
            // The "Qty 1" text in the previous design was a placeholder for a future
            // multi-quantity feature. Drop it for now — re-add when the model gains qty.
            val needsPhoto = item.fabricPhotoUrl.isNullOrBlank()
            val needsName = !needsPhoto && item.fabricName.isNullOrBlank()
            val ctaLabel: StringResource?
            val ctaCallback: (() -> Unit)?
            when {
                needsPhoto -> {
                    ctaLabel = Res.string.order_detail_add_fabric
                    ctaCallback = onAddFabricPhotoClick
                }
                needsName -> {
                    ctaLabel = Res.string.order_detail_add_fabric_name
                    ctaCallback = onAddFabricNameClick
                }
                else -> {
                    ctaLabel = null
                    ctaCallback = null
                }
            }
            if (ctaLabel != null && ctaCallback != null) {
                Spacer(Modifier.height(DesignTokens.space1))
                TextButton(
                    onClick = ctaCallback,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(ctaLabel),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (priority != OrderPriority.NORMAL) {
                Spacer(Modifier.weight(1f))
                PriorityPill(priority = priority)
            }
        }

        // Right column: fabric photo with caption pill, or placeholder.
        if (!item.fabricPhotoUrl.isNullOrBlank()) {
            FabricThumbnail(photoUrl = item.fabricPhotoUrl)
        } else {
            FabricPlaceholder()
        }
    }
}

@Composable
private fun FabricThumbnail(photoUrl: String) {
    val caption = stringResource(Res.string.order_detail_fabric_caption)
    Box(
        modifier = Modifier
            .size(FABRIC_THUMBNAIL_SIZE)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
    ) {
        SubcomposeAsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(FABRIC_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(DesignTokens.radiusMd)),
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(DesignTokens.radiusFull),
                )
                .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
        )
    }
}

@Composable
private fun FabricPlaceholder() {
    Box(
        modifier = Modifier
            .size(FABRIC_PLACEHOLDER_SIZE)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Texture,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(DesignTokens.iconFeature),
        )
    }
}

@Composable
private fun PriorityPill(priority: OrderPriority) {
    val (pillColor, label) = when (priority) {
        OrderPriority.URGENT -> DesignTokens.warning500 to "High"
        OrderPriority.RUSH -> DesignTokens.error500 to "Rush"
        OrderPriority.NORMAL -> return
    }
    Surface(
        shape = CircleShape,
        color = pillColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = pillColor,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space1,
            ),
        )
    }
}

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Returns true when an item is still missing fabric photo or fabric name. */
private fun needsFabricInfo(item: OrderItem): Boolean =
    item.fabricPhotoUrl.isNullOrBlank() || item.fabricName.isNullOrBlank()

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardSingleNormalFabricPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SHIRT,
                    description = "Ankara print",
                    price = 60_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = "Royal Lace",
                ),
            ),
            priority = OrderPriority.NORMAL,
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardPhotoSetNameMissingPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask",
                    price = 100_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardUrgentNoFabricPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SENATOR,
                    description = "White lace",
                    price = 85_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.URGENT,
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardMultiItemPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask with embroidery",
                    price = 100_000.0,
                    fabricPhotoUrl = "https://example.com/fabric1.jpg",
                ),
                OrderItem(
                    id = "i2",
                    garmentType = GarmentType.SHIRT,
                    description = "Matching cotton inner",
                    price = 25_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardRushDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold Damask",
                    price = 120_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.RUSH,
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

// endregion

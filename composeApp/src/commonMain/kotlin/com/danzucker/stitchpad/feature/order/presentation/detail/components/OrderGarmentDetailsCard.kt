package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_add_fabric
import stitchpad.composeapp.generated.resources.order_detail_add_fabric_name
import stitchpad.composeapp.generated.resources.order_detail_fabric_caption
import stitchpad.composeapp.generated.resources.order_detail_garment_section
import stitchpad.composeapp.generated.resources.order_detail_quantity
import stitchpad.composeapp.generated.resources.order_priority_high_pill
import stitchpad.composeapp.generated.resources.order_priority_rush_pill

private val FABRIC_THUMBNAIL_SIZE = 64.dp
private val FABRIC_PLACEHOLDER_SIZE = 96.dp

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>,
    priority: OrderPriority,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewerImages: List<String> by remember { mutableStateOf(emptyList()) }
    var viewerStartIndex: Int by remember { mutableIntStateOf(0) }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space4,
                bottom = DesignTokens.space3,
            ),
        ) {
            // Per-item rows. The first row hosts the section header inside its
            // left column so the fabric strip spans from the top of the card
            // body and total card height collapses by ~40dp.
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
                    showHeader = index == 0,
                    onFabricStripClick = { urls, startIdx ->
                        viewerImages = urls
                        viewerStartIndex = startIdx
                    },
                )
            }
        }
    }
    if (viewerImages.isNotEmpty()) {
        FullScreenImageViewer(
            images = viewerImages,
            startIndex = viewerStartIndex,
            contentDescription = null,
            onDismiss = { viewerImages = emptyList() },
        )
    }
}

@Composable
private fun GarmentItemRow(
    item: OrderItem,
    onAddFabricPhotoClick: (() -> Unit)?,
    onAddFabricNameClick: (() -> Unit)?,
    priority: OrderPriority,
    showHeader: Boolean,
    onFabricStripClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (showHeader) {
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
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                val garmentLabel = garmentDisplayName(item)
                Text(
                    text = garmentLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (priority != OrderPriority.NORMAL) {
                    PriorityPill(priority = priority)
                }
            }
            if (!item.fabricName.isNullOrBlank()) {
                Spacer(Modifier.height(DesignTokens.space2))
                Text(
                    text = item.fabricName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.order_detail_quantity, item.quantity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(DesignTokens.space2))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hasFabricImages = item.fabricImages.isNotEmpty()
            val needsPhoto = !hasFabricImages && item.fabricPhotoUrl.isNullOrBlank()
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
        }

        // Right column: fabric strip (multi-image) or placeholder.
        if (item.fabricImages.isNotEmpty()) {
            FabricStrip(
                fabricImages = item.fabricImages,
                onImageClick = onFabricStripClick,
            )
        } else if (!item.fabricPhotoUrl.isNullOrBlank()) {
            // Legacy single-field fallback during double-write window.
            FabricStrip(
                fabricImages = listOf(
                    FabricImageRef(
                        photoUrl = item.fabricPhotoUrl,
                        photoStoragePath = item.fabricPhotoStoragePath.orEmpty(),
                    )
                ),
                onImageClick = onFabricStripClick,
            )
        } else {
            FabricPlaceholder()
        }
    }
}

/**
 * Horizontal scrollable strip of fabric thumbnail tiles.
 * Tapping any tile opens the full-screen viewer at that index.
 */
@Composable
private fun FabricStrip(
    fabricImages: List<FabricImageRef>,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val urls = fabricImages.map { it.photoUrl }
    val caption = stringResource(Res.string.order_detail_fabric_caption)
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        urls.forEachIndexed { index, url ->
            Box(
                modifier = Modifier
                    .size(FABRIC_THUMBNAIL_SIZE)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    )
                    .clickable { onImageClick(urls, index) },
            ) {
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LoadingDots(dotSize = 6.dp)
                        }
                    },
                    modifier = Modifier
                        .size(FABRIC_THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd)),
                )
                if (index == 0) {
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
        }
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
    val (pillColor, labelRes) = when (priority) {
        OrderPriority.URGENT -> DesignTokens.warning500 to Res.string.order_priority_high_pill
        OrderPriority.RUSH -> DesignTokens.error500 to Res.string.order_priority_rush_pill
        OrderPriority.NORMAL -> return
    }
    Surface(
        shape = CircleShape,
        color = pillColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = stringResource(labelRes),
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
private fun needsFabricInfo(item: OrderItem): Boolean {
    val hasPhoto = item.fabricImages.isNotEmpty() || !item.fabricPhotoUrl.isNullOrBlank()
    return !hasPhoto || item.fabricName.isNullOrBlank()
}

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

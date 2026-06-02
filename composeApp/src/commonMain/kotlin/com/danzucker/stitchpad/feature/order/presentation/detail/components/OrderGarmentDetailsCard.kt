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
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_add_fabric
import stitchpad.composeapp.generated.resources.order_detail_add_fabric_name
import stitchpad.composeapp.generated.resources.order_detail_add_style
import stitchpad.composeapp.generated.resources.order_detail_fabric_caption
import stitchpad.composeapp.generated.resources.order_detail_garment_section
import stitchpad.composeapp.generated.resources.order_detail_style_caption
import stitchpad.composeapp.generated.resources.order_priority_high_pill
import stitchpad.composeapp.generated.resources.order_priority_rush_pill

private val REFERENCE_THUMBNAIL_SIZE = 64.dp

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>,
    priority: OrderPriority,
    styleImageUrls: List<String>,
    onAddStyleClick: () -> Unit,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewerImages: List<String> by remember { mutableStateOf(emptyList()) }
    var viewerStartIndex: Int by remember { mutableIntStateOf(0) }
    val openViewer: (List<String>, Int) -> Unit = { urls, startIdx ->
        viewerImages = urls
        viewerStartIndex = startIdx
    }

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
                bottom = DesignTokens.space4,
            ),
        ) {
            SectionHeader()
            Spacer(Modifier.height(DesignTokens.space3))

            val firstNeedsFabricIndex = items.indexOfFirst { needsFabricInfo(it) }
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(DesignTokens.space4))
                }

                GarmentTextBlock(
                    item = item,
                    priority = if (index == 0) priority else OrderPriority.NORMAL,
                )

                // Style is order-level — render it once, under the first garment.
                if (index == 0) {
                    Spacer(Modifier.height(DesignTokens.space3))
                    ReferenceSection(
                        label = stringResource(Res.string.order_detail_style_caption),
                        icon = Icons.Default.Checkroom,
                        urls = styleImageUrls,
                        ctaLabel = if (styleImageUrls.isEmpty()) Res.string.order_detail_add_style else null,
                        onCtaClick = onAddStyleClick,
                        onImageClick = openViewer,
                    )
                }

                Spacer(Modifier.height(DesignTokens.space3))
                FabricSection(
                    item = item,
                    showCta = firstNeedsFabricIndex == index,
                    onAddFabricPhotoClick = onAddFabricPhotoClick,
                    onAddFabricNameClick = onAddFabricNameClick,
                    onImageClick = openViewer,
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
private fun SectionHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        SectionIconTile(imageVector = Icons.Default.Checkroom, contentDescription = null)
        Text(
            text = stringResource(Res.string.order_detail_garment_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GarmentTextBlock(
    item: OrderItem,
    priority: OrderPriority,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = garmentDisplayName(item),
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
}

@Composable
private fun FabricSection(
    item: OrderItem,
    showCta: Boolean,
    onAddFabricPhotoClick: () -> Unit,
    onAddFabricNameClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val legacyUrl = item.fabricPhotoUrl
    val urls = when {
        item.fabricImages.isNotEmpty() -> item.fabricImages.map { it.photoUrl }
        !legacyUrl.isNullOrBlank() -> listOf(legacyUrl)
        else -> emptyList()
    }
    val needsPhoto = urls.isEmpty()
    val needsName = !needsPhoto && item.fabricName.isNullOrBlank()
    val ctaLabel: StringResource? = when {
        !showCta -> null
        needsPhoto -> Res.string.order_detail_add_fabric
        needsName -> Res.string.order_detail_add_fabric_name
        else -> null
    }
    val onCtaClick: () -> Unit = if (needsPhoto) onAddFabricPhotoClick else onAddFabricNameClick

    ReferenceSection(
        label = stringResource(Res.string.order_detail_fabric_caption),
        icon = Icons.Default.Texture,
        urls = urls,
        ctaLabel = ctaLabel,
        onCtaClick = onCtaClick,
        onImageClick = onImageClick,
    )
}

/**
 * A labeled reference block: a mini header (icon + label), a horizontal strip of
 * 64dp thumbnails (or a single placeholder tile when empty), and an optional
 * "Add ..." text CTA below. Used for both style and fabric so the two read as peers.
 */
@Composable
private fun ReferenceSection(
    label: String,
    icon: ImageVector,
    urls: List<String>,
    ctaLabel: StringResource?,
    onCtaClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(DesignTokens.space2))

    if (urls.isEmpty()) {
        ReferencePlaceholder(icon = icon, onClick = if (ctaLabel != null) onCtaClick else null)
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            urls.forEachIndexed { index, url ->
                ReferenceThumbnail(url = url, onClick = { onImageClick(urls, index) })
            }
        }
    }

    if (ctaLabel != null) {
        Spacer(Modifier.height(DesignTokens.space1))
        TextButton(onClick = onCtaClick, contentPadding = PaddingValues(0.dp)) {
            Text(
                text = stringResource(ctaLabel),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReferenceThumbnail(
    url: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(REFERENCE_THUMBNAIL_SIZE)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            .clickable { onClick() },
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
                .size(REFERENCE_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(DesignTokens.radiusMd)),
        )
    }
}

@Composable
private fun ReferencePlaceholder(icon: ImageVector, onClick: (() -> Unit)?) {
    val base = Modifier
        .size(REFERENCE_THUMBNAIL_SIZE)
        .background(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
        )
    Box(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
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
private fun OrderGarmentDetailsCardEmptyPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask",
                    price = 60_000.0,
                    fabricPhotoUrl = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = emptyList(),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardPopulatedPreview() {
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
            priority = OrderPriority.URGENT,
            styleImageUrls = listOf("https://example.com/style1.jpg", "https://example.com/style2.jpg"),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SENATOR,
                    description = "White lace",
                    price = 85_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = null,
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImageUrls = emptyList(),
            onAddStyleClick = {},
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
            styleImageUrls = listOf("https://example.com/style1.jpg"),
            onAddStyleClick = {},
            onAddFabricPhotoClick = {},
            onAddFabricNameClick = {},
        )
    }
}

// endregion

@file:Suppress("TooManyFunctions")

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import stitchpad.composeapp.generated.resources.order_detail_quantity
import stitchpad.composeapp.generated.resources.order_detail_style_caption
import stitchpad.composeapp.generated.resources.order_form_image_add_tile
import stitchpad.composeapp.generated.resources.order_priority_high_pill
import stitchpad.composeapp.generated.resources.order_priority_rush_pill
import kotlin.math.roundToInt

private val REFERENCE_TILE_HEIGHT = 128.dp
private val REFERENCE_MULTI_TILE_WIDTH = 100.dp
private const val MAX_IMAGES_PER_CATEGORY = 3

data class ReferenceImage(
    val url: String,
    val sourceIndex: Int,
)

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>,
    priority: OrderPriority,
    styleImagesByItemId: Map<String, List<ReferenceImage>>,
    onAddStyleClick: (String) -> Unit,
    onRemoveStyleImage: (String, Int) -> Unit,
    onAddFabricPhotoClick: (String) -> Unit,
    onRemoveFabricImage: (String, Int) -> Unit,
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
                Spacer(Modifier.height(DesignTokens.space3))

                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
                    StyleColumn(
                        item = item,
                        styleImages = styleImagesByItemId[item.id].orEmpty(),
                        onAddStyleClick = onAddStyleClick,
                        onRemoveStyleImage = onRemoveStyleImage,
                        onImageClick = openViewer,
                        modifier = Modifier.weight(1f),
                    )
                    FabricColumn(
                        item = item,
                        showCta = firstNeedsFabricIndex == index,
                        onAddFabricPhotoClick = onAddFabricPhotoClick,
                        onRemoveFabricImage = onRemoveFabricImage,
                        onAddFabricNameClick = onAddFabricNameClick,
                        onImageClick = openViewer,
                        modifier = Modifier.weight(1f),
                    )
                }
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
}

@Composable
private fun StyleColumn(
    item: OrderItem,
    styleImages: List<ReferenceImage>,
    onAddStyleClick: (String) -> Unit,
    onRemoveStyleImage: (String, Int) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val urls = styleImages.map { it.url }
    ReferenceColumn(
        label = stringResource(Res.string.order_detail_style_caption),
        icon = Icons.Default.Checkroom,
        urls = urls,
        ctaLabel = if (urls.isEmpty()) Res.string.order_detail_add_style else null,
        canAdd = styleImages.size < MAX_IMAGES_PER_CATEGORY,
        onCtaClick = { onAddStyleClick(item.id) },
        onAddClick = { onAddStyleClick(item.id) },
        onRemove = if (styleImages.isNotEmpty()) {
            { displayIndex ->
                styleImages.getOrNull(displayIndex)?.sourceIndex?.let { onRemoveStyleImage(item.id, it) }
            }
        } else {
            null
        },
        onImageClick = onImageClick,
        modifier = modifier,
    )
}

@Composable
private fun FabricColumn(
    item: OrderItem,
    showCta: Boolean,
    onAddFabricPhotoClick: (String) -> Unit,
    onRemoveFabricImage: (String, Int) -> Unit,
    onAddFabricNameClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val legacyUrl = item.fabricPhotoUrl
    val urls = when {
        item.fabricImages.isNotEmpty() -> item.fabricImages.map { it.localPhotoPath ?: it.photoUrl }
        !legacyUrl.isNullOrBlank() -> listOf(legacyUrl)
        else -> emptyList()
    }
    val needsPhoto = urls.isEmpty()
    val canAddPhoto = urls.size < MAX_IMAGES_PER_CATEGORY
    val needsName = !needsPhoto && item.fabricName.isNullOrBlank()
    val ctaLabel: StringResource? = when {
        !showCta -> null
        needsPhoto -> Res.string.order_detail_add_fabric
        needsName -> Res.string.order_detail_add_fabric_name
        else -> null
    }
    val onAddClick: () -> Unit = { onAddFabricPhotoClick(item.id) }
    val onCtaClick: () -> Unit = if (needsPhoto) {
        { onAddFabricPhotoClick(item.id) }
    } else {
        onAddFabricNameClick
    }

    ReferenceColumn(
        label = stringResource(Res.string.order_detail_fabric_caption),
        icon = Icons.Default.Texture,
        urls = urls,
        ctaLabel = ctaLabel,
        canAdd = canAddPhoto,
        onCtaClick = onCtaClick,
        onAddClick = onAddClick,
        onRemove = if (item.fabricImages.isNotEmpty()) {
            { index -> onRemoveFabricImage(item.id, index) }
        } else {
            null
        },
        onImageClick = onImageClick,
        modifier = modifier,
    )
}

/**
 * A labeled reference block that fills the width it is given: a mini header (icon + label),
 * a fixed-height media area, and an optional "Add ..." CTA below. Used for both style and
 * fabric so the two read as side-by-side peers. Media: a single photo fills the column; 2+
 * photos scroll horizontally with the next one peeking and a "i/n" count pill; empty shows a
 * tappable placeholder.
 */
@Composable
private fun ReferenceColumn(
    label: String,
    icon: ImageVector,
    urls: List<String>,
    ctaLabel: StringResource?,
    canAdd: Boolean,
    onCtaClick: () -> Unit,
    onAddClick: () -> Unit,
    onRemove: ((Int) -> Unit)?,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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

        when {
            urls.isEmpty() -> ReferencePlaceholder(
                icon = icon,
                onClick = if (canAdd) onAddClick else null,
            )
            else -> MultiReferenceStrip(
                urls = urls,
                canAdd = canAdd,
                contentDescription = label,
                onAddClick = onAddClick,
                onRemove = onRemove,
                onImageClick = onImageClick,
            )
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
}

@Composable
private fun ReferenceTileImage(url: String, contentDescription: String?) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadingDots(dotSize = 6.dp)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MultiReferenceStrip(
    urls: List<String>,
    canAdd: Boolean,
    contentDescription: String?,
    onAddClick: () -> Unit,
    onRemove: ((Int) -> Unit)?,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val currentIndex by remember(urls.size) {
        derivedStateOf { referenceScrollIndex(scrollState.value, scrollState.maxValue, urls.size) }
    }
    Box(modifier = Modifier.fillMaxWidth().height(REFERENCE_TILE_HEIGHT)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.horizontalScroll(scrollState),
        ) {
            urls.forEachIndexed { index, url ->
                Box(
                    modifier = Modifier
                        .width(REFERENCE_MULTI_TILE_WIDTH)
                        .height(REFERENCE_TILE_HEIGHT)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick(urls, index) },
                ) {
                    ReferenceTileImage(url = url, contentDescription = contentDescription)
                    if (onRemove != null) {
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
            if (canAdd) {
                AddReferenceTile(onClick = onAddClick)
            }
        }
        if (urls.size > 1) {
            Text(
                text = "${currentIndex + 1}/${urls.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DesignTokens.space2)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(DesignTokens.radiusFull),
                    )
                    .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AddReferenceTile(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(REFERENCE_MULTI_TILE_WIDTH)
            .height(REFERENCE_TILE_HEIGHT)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(Res.string.order_form_image_add_tile),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ReferencePlaceholder(icon: ImageVector, onClick: (() -> Unit)?) {
    val base = Modifier
        .fillMaxWidth()
        .height(REFERENCE_TILE_HEIGHT)
        .clip(RoundedCornerShape(DesignTokens.radiusMd))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
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

/**
 * Maps the current horizontal scroll offset to the index of the photo the count pill should
 * highlight, as a progress fraction over the scroll state's REACHABLE range (`maxScrollPx`).
 * Stride-based math can't reach the final image in a narrow peek-column (the last tiles never
 * reach the left edge), so we map [0, maxScrollPx] across [0, count-1]. Pure so it's unit-tested.
 */
internal fun referenceScrollIndex(scrollPx: Int, maxScrollPx: Int, count: Int): Int {
    if (count <= 1 || maxScrollPx <= 0) return 0
    val progress = (scrollPx.toFloat() / maxScrollPx).coerceIn(0f, 1f)
    return (progress * (count - 1)).roundToInt()
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardOneEachPreview() {
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
            styleImagesByItemId = mapOf("i1" to listOf(ReferenceImage("https://example.com/style1.jpg", 0))),
            onAddStyleClick = { _ -> },
            onRemoveStyleImage = { _, _ -> },
            onAddFabricPhotoClick = { _ -> },
            onRemoveFabricImage = { _, _ -> },
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardMultiStylePreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.AGBADA,
                    description = "Gold damask",
                    price = 100_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                    fabricName = "Damask",
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImagesByItemId = mapOf(
                "i1" to listOf(
                    ReferenceImage("https://example.com/style1.jpg", 0),
                    ReferenceImage("https://example.com/style2.jpg", 1),
                    ReferenceImage("https://example.com/style3.jpg", 2),
                ),
            ),
            onAddStyleClick = { _ -> },
            onRemoveStyleImage = { _, _ -> },
            onAddFabricPhotoClick = { _ -> },
            onRemoveFabricImage = { _, _ -> },
            onAddFabricNameClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardAsymmetricPreview() {
    // Style added, fabric empty — columns must stay aligned at equal height.
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
            priority = OrderPriority.NORMAL,
            styleImagesByItemId = mapOf("i1" to listOf(ReferenceImage("https://example.com/style1.jpg", 0))),
            onAddStyleClick = { _ -> },
            onRemoveStyleImage = { _, _ -> },
            onAddFabricPhotoClick = { _ -> },
            onRemoveFabricImage = { _, _ -> },
            onAddFabricNameClick = {},
        )
    }
}

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
            styleImagesByItemId = emptyMap(),
            onAddStyleClick = { _ -> },
            onRemoveStyleImage = { _, _ -> },
            onAddFabricPhotoClick = { _ -> },
            onRemoveFabricImage = { _, _ -> },
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
                    fabricName = "Lace",
                ),
            ),
            priority = OrderPriority.NORMAL,
            styleImagesByItemId = mapOf("i1" to listOf(ReferenceImage("https://example.com/style1.jpg", 0))),
            onAddStyleClick = { _ -> },
            onRemoveStyleImage = { _, _ -> },
            onAddFabricPhotoClick = { _ -> },
            onRemoveFabricImage = { _, _ -> },
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
                    fabricName = "Damask",
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
            styleImagesByItemId = mapOf(
                "i1" to listOf(ReferenceImage("https://example.com/style1.jpg", 0)),
                "i2" to emptyList(),
            ),
            onAddStyleClick = { _ -> },
            onRemoveStyleImage = { _, _ -> },
            onAddFabricPhotoClick = { _ -> },
            onRemoveFabricImage = { _, _ -> },
            onAddFabricNameClick = {},
        )
    }
}

// endregion

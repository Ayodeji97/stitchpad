package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_garment_section

private val FABRIC_THUMBNAIL_SIZE = 72.dp

@Composable
fun OrderGarmentDetailsCard(
    items: List<OrderItem>,
    priority: OrderPriority,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // Header row
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

            // Item rows
            items.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(DesignTokens.space2))
                GarmentItemRow(item = item)
            }

            // Priority pill — only for non-NORMAL priority
            if (priority != OrderPriority.NORMAL) {
                Spacer(Modifier.height(DesignTokens.space3))
                PriorityPill(priority = priority)
            }
        }
    }
}

@Composable
private fun GarmentItemRow(item: OrderItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = garmentDisplayName(item.garmentType),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space1))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
            ) {
                Icon(
                    imageVector = Icons.Default.Texture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(DesignTokens.iconInline),
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Qty 1",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Fabric thumbnail
        if (!item.fabricPhotoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = item.fabricPhotoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(FABRIC_THUMBNAIL_SIZE)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(FABRIC_THUMBNAIL_SIZE)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Checkroom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DesignTokens.iconFeature),
                )
            }
        }
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

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardSingleNormalPreview() {
    StitchPadTheme {
        OrderGarmentDetailsCard(
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = GarmentType.SHIRT,
                    description = "Ankara",
                    price = 60_000.0,
                    fabricPhotoUrl = "https://example.com/fabric.jpg",
                ),
            ),
            priority = OrderPriority.NORMAL,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderGarmentDetailsCardUrgentNoPhotoPreview() {
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
        )
    }
}

// endregion

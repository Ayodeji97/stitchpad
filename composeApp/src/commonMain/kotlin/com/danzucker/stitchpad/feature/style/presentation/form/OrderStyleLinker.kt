package com.danzucker.stitchpad.feature.style.presentation.form

import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource

private const val MAX_STYLE_IMAGES_PER_ITEM = 3

/**
 * Appends a LIBRARY style ref ([newStyleId]) to the target order item and returns the updated
 * item list, or null when nothing changes (target missing, style already linked, or the item is
 * at the image cap). [targetItemId] selects the garment; when null (legacy/unspecified link) it
 * falls back to the first item — matching the pre-per-item behaviour.
 *
 * Pure so it can be unit-tested; the StyleForm save() flow only persists when this returns non-null.
 */
internal fun linkStyleToOrderItems(
    items: List<OrderItem>,
    targetItemId: String?,
    newStyleId: String,
    maxImagesPerItem: Int = MAX_STYLE_IMAGES_PER_ITEM,
): List<OrderItem>? {
    val target = if (targetItemId != null) {
        items.firstOrNull { it.id == targetItemId }
    } else {
        items.firstOrNull()
    } ?: return null

    val alreadyLinked = target.styleImages.any {
        it.source == StyleImageSource.LIBRARY && it.styleId == newStyleId
    }
    if (alreadyLinked || target.styleImages.size >= maxImagesPerItem) return null

    val newRef = StyleImageRef(source = StyleImageSource.LIBRARY, styleId = newStyleId)
    return items.map { item ->
        if (item.id == target.id) item.copy(styleImages = item.styleImages + newRef) else item
    }
}

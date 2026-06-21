package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource

/**
 * Pure toggle/commit math for the order-DETAIL saved-style picker, extracted so it
 * can be unit-tested without instantiating [OrderDetailViewModel] (which requires a
 * platform Coil ImageLoader + PlatformContext that are unconstructible in commonTest).
 *
 * Mirrors the order-FORM picker semantics: batch toggle in tap order, hard cap at
 * [maxRefs] across the item's already-committed [StyleImageRef]s plus the pending list.
 */

/**
 * Toggles [styleId] in [pending]: removes it if already pending, otherwise appends it
 * unless committing it would push the item past [maxRefs] ([committedSlots] already used).
 */
internal fun togglePendingStyle(
    pending: List<String>,
    styleId: String,
    committedSlots: Int,
    maxRefs: Int,
): List<String> = when {
    styleId in pending -> pending - styleId
    committedSlots + pending.size >= maxRefs -> pending
    else -> pending + styleId
}

/**
 * The LIBRARY [StyleImageRef]s to APPEND for the [pending] picks, skipping ids already
 * present in [existingStyleIds] and defensively capping the total at [maxRefs]
 * ([usedSlots] already used). Empty when nothing new fits.
 */
internal fun pendingStyleRefsToAdd(
    pending: List<String>,
    existingStyleIds: Set<String>,
    usedSlots: Int,
    maxRefs: Int,
): List<StyleImageRef> =
    pending
        .filter { it !in existingStyleIds }
        .take((maxRefs - usedSlots).coerceAtLeast(0))
        .map { StyleImageRef(source = StyleImageSource.LIBRARY, styleId = it) }

package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.Style

/**
 * Decides which styles are "locked" (read-only) for the current tier. The newest
 * [activeCap] styles (by createdAt) stay active/editable; everything beyond the cap is
 * locked. Used for Free flattened closets and Pro/Atelier per-folder over-cap. Purely a
 * presentation decision — never deletes or moves data.
 */
object StyleLockPolicy {
    fun lockedStyleIds(styles: List<Style>, activeCap: Int): Set<String> {
        if (activeCap <= 0) return styles.mapTo(mutableSetOf()) { it.id }
        return styles
            .sortedByDescending { it.createdAt }
            .drop(activeCap)
            .mapTo(mutableSetOf()) { it.id }
    }
}

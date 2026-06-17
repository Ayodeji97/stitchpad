package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.Style
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleLockPolicyTest {

    private fun style(id: String, createdAt: Long) = Style(
        id = id,
        customerId = "c1",
        description = id,
        photoUrl = "",
        photoStoragePath = "",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun underCap_nothingLocked() {
        val styles = listOf(style("a", 1), style("b", 2))
        assertEquals(emptySet(), StyleLockPolicy.lockedStyleIds(styles, activeCap = 5))
    }

    @Test
    fun overCap_newestStayActive_oldestLocked() {
        // newest = highest createdAt. cap 2 keeps c(3) and b(2) active; a(1) locked.
        val styles = listOf(style("a", 1), style("b", 2), style("c", 3))
        assertEquals(setOf("a"), StyleLockPolicy.lockedStyleIds(styles, activeCap = 2))
    }

    @Test
    fun capZero_everythingLocked() {
        val styles = listOf(style("a", 1), style("b", 2))
        assertEquals(setOf("a", "b"), StyleLockPolicy.lockedStyleIds(styles, activeCap = 0))
    }
}

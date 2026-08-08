package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [fallbackMemberColorSeed] dedup (final-fix-report Finding 2, staff-phase2-assignment): a
 * single pure helper shared by [com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderAssigneeCard]
 * (only as a last resort — it prefers the roster's own `colorSeed` when it can resolve one)
 * and [com.danzucker.stitchpad.feature.order.presentation.list.OrderListScreen] (always —
 * list rows never have a roster), so the two call sites can no longer drift.
 */
class MemberAvatarTest {

    @Test
    fun prefersTheIdOverTheName() {
        // Same id, different name -> same seed; the id must win so a rename doesn't
        // reshuffle the hue.
        val seedBeforeRename = fallbackMemberColorSeed(memberId = "m1", memberName = "Old Name")
        val seedAfterRename = fallbackMemberColorSeed(memberId = "m1", memberName = "New Name")
        assertEquals(seedBeforeRename, seedAfterRename)
    }

    @Test
    fun fallsBackToTheNameWhenIdIsNull() {
        assertEquals("Paul Adeyemi".hashCode(), fallbackMemberColorSeed(memberId = null, memberName = "Paul Adeyemi"))
    }

    @Test
    fun differentIdsProduceDifferentSeeds() {
        assertNotEquals(
            fallbackMemberColorSeed(memberId = "m1", memberName = "Same Name"),
            fallbackMemberColorSeed(memberId = "m2", memberName = "Same Name"),
        )
    }

    @Test
    fun isStableAcrossCalls() {
        // Deterministic — no randomness, no time dependency.
        assertEquals(
            fallbackMemberColorSeed(memberId = "m1", memberName = "Paul"),
            fallbackMemberColorSeed(memberId = "m1", memberName = "Paul"),
        )
    }
}

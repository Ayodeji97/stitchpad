package com.danzucker.stitchpad.feature.dashboard.presentation.components

import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import kotlin.test.Test
import kotlin.test.assertNotNull

class DashboardIllustrationTest {

    @Test
    fun heroIllustrationDefinedForEveryFocusVariant() {
        FocusVariant.entries.forEach { variant ->
            assertNotNull(
                heroIllustrationFor(variant),
                "FocusVariant.$variant must have a hero illustration",
            )
        }
    }

    @Test
    fun emptyIllustrationDefinedForEverySlot() {
        EmptyIllustrationSlot.entries.forEach { slot ->
            assertNotNull(
                emptyIllustrationFor(slot),
                "EmptyIllustrationSlot.$slot must have an empty illustration",
            )
        }
    }
}

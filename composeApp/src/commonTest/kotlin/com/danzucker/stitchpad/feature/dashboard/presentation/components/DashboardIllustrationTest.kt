package com.danzucker.stitchpad.feature.dashboard.presentation.components

import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import kotlin.test.Test

/**
 * These tests document the contract that every FocusVariant and every
 * EmptyIllustrationSlot has an illustration mapping. The when-exhaustiveness
 * check in `heroIllustrationFor` / `emptyIllustrationFor` enforces this at
 * compile time today; the tests exist so the contract survives a future
 * refactor (e.g. someone changing the return type to nullable, or replacing
 * the when with a Map lookup).
 *
 * TODO(v2 art): once real illustrations ship, strengthen these to assert
 * each variant maps to a *distinct* drawable so a copy-paste bug between
 * branches is caught.
 */
class DashboardIllustrationTest {

    @Test
    fun everyFocusVariantHasHeroIllustration() {
        FocusVariant.entries.forEach { variant ->
            heroIllustrationFor(variant)
        }
    }

    @Test
    fun everyEmptyIllustrationSlotHasIllustration() {
        EmptyIllustrationSlot.entries.forEach { slot ->
            emptyIllustrationFor(slot)
        }
    }
}

package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrikethroughPriceTest {

    @Test
    fun zeroDiscount_hidesStruckGross() {
        // The dominant case: no discount -> only the net total shows, no strikethrough.
        assertFalse(shouldShowStruckGross(0.0))
    }

    @Test
    fun negativeDiscount_hidesStruckGross() {
        // Defensive: a bad/negative value must never trigger a strikethrough.
        assertFalse(shouldShowStruckGross(-5_000.0))
    }

    @Test
    fun positiveDiscount_showsStruckGross() {
        assertTrue(shouldShowStruckGross(30_000.0))
    }
}

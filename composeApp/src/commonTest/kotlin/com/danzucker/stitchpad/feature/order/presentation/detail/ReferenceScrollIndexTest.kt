package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.feature.order.presentation.detail.components.referenceScrollIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceScrollIndexTest {

    @Test
    fun atRest_isFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 0, maxScrollPx = 200, count = 3))
    }

    @Test
    fun fullyScrolled_reachesLast() {
        // The whole point of the fix: max scroll must map to the final index.
        assertEquals(2, referenceScrollIndex(scrollPx = 200, maxScrollPx = 200, count = 3))
    }

    @Test
    fun halfScrolled_isMiddle() {
        assertEquals(1, referenceScrollIndex(scrollPx = 100, maxScrollPx = 200, count = 3))
    }

    @Test
    fun justUnderFirstThreshold_staysFirst() {
        // For 3 items the flip to index 1 happens at 25% progress (round(0.5)).
        // 49/200 = 0.245 -> round(0.49) = 0.
        assertEquals(0, referenceScrollIndex(scrollPx = 49, maxScrollPx = 200, count = 3))
    }

    @Test
    fun beyondMax_clampsToLast() {
        assertEquals(2, referenceScrollIndex(scrollPx = 10_000, maxScrollPx = 200, count = 3))
    }

    @Test
    fun singleImage_isAlwaysFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 500, maxScrollPx = 200, count = 1))
    }

    @Test
    fun zeroMaxScroll_isFirst() {
        // Not yet laid out / nothing scrollable.
        assertEquals(0, referenceScrollIndex(scrollPx = 500, maxScrollPx = 0, count = 3))
    }
}

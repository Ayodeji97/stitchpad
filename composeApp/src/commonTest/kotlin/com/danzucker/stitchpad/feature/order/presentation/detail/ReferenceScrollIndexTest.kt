package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.feature.order.presentation.detail.components.referenceScrollIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceScrollIndexTest {

    private val stride = 108f // tile width (100) + gap (8), in px for the test

    @Test
    fun atRest_isFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 0, stridePx = stride, count = 3))
    }

    @Test
    fun justPastHalf_roundsToSecond() {
        // 60px > half of 108 → rounds up to index 1
        assertEquals(1, referenceScrollIndex(scrollPx = 60, stridePx = stride, count = 3))
    }

    @Test
    fun justUnderHalf_staysFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 40, stridePx = stride, count = 3))
    }

    @Test
    fun scrolledToEnd_clampsToLast() {
        assertEquals(2, referenceScrollIndex(scrollPx = 10_000, stridePx = stride, count = 3))
    }

    @Test
    fun singleImage_isAlwaysFirst() {
        assertEquals(0, referenceScrollIndex(scrollPx = 500, stridePx = stride, count = 1))
    }

    @Test
    fun zeroStride_doesNotCrash() {
        assertEquals(0, referenceScrollIndex(scrollPx = 500, stridePx = 0f, count = 3))
    }
}

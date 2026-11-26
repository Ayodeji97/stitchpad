package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import com.danzucker.stitchpad.feature.notebookimport.domain.PageBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropRegionTest {

    private fun assertInBounds(region: CropRegion) {
        assertTrue(region.left >= 0f && region.top >= 0f, "starts inside: $region")
        assertTrue(region.left + region.width <= 1.0001f, "ends inside horizontally: $region")
        assertTrue(region.top + region.height <= 1.0001f, "ends inside vertically: $region")
    }

    @Test
    fun nullBox_showsWholePage() {
        assertNull(cropRegion(null))
    }

    @Test
    fun validBox_isPaddedAndInBounds() {
        val region = assertNotNull(cropRegion(PageBox(0.2f, 0.3f, 0.5f, 0.2f)))
        assertInBounds(region)
        assertEquals(0.18f, region.left, 0.001f)
        assertEquals(0.54f, region.width, 0.001f)
    }

    @Test
    fun boxSpillingOffThePage_isClamped() {
        val region = assertNotNull(cropRegion(PageBox(0.8f, -0.2f, 0.6f, 0.5f)))
        assertInBounds(region)
        assertEquals(0f, region.top, 0.001f)
    }

    @Test
    fun tinyBox_growsToMinimumSize() {
        val region = assertNotNull(cropRegion(PageBox(0.99f, 0.5f, 0.001f, 0.001f)))
        assertInBounds(region)
        assertTrue(region.width >= MIN_CROP_FRACTION - 0.0001f)
        assertTrue(region.height >= MIN_CROP_FRACTION - 0.0001f)
    }

    @Test
    fun invalidBoxes_showWholePage() {
        assertNull(cropRegion(PageBox(Float.NaN, 0f, 0.5f, 0.5f)))
        assertNull(cropRegion(PageBox(0f, 0f, Float.POSITIVE_INFINITY, 0.5f)))
        assertNull(cropRegion(PageBox(0.2f, 0.2f, 0f, 0.5f)))
        assertNull(cropRegion(PageBox(0.2f, 0.2f, -0.3f, 0.5f)))
        assertNull(cropRegion(PageBox(1.5f, 0.2f, 0.3f, 0.5f)))
        assertNull(cropRegion(PageBox(0.2f, -2f, 0.3f, 0.5f)))
    }
}

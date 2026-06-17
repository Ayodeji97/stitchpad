package com.danzucker.stitchpad.feature.style.presentation.form

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderStyleLinkerTest {

    private fun item(id: String, styles: List<StyleImageRef> = emptyList()) =
        OrderItem(id = id, garmentType = GarmentType.SHIRT, description = "", price = 0.0, styleImages = styles)

    private fun lib(styleId: String) = StyleImageRef(source = StyleImageSource.LIBRARY, styleId = styleId)

    @Test
    fun linksToTargetItem_notFirst() {
        // The whole point: a new style created from the 2nd garment must attach to THAT garment.
        val items = listOf(item("a"), item("b"))
        val out = linkStyleToOrderItems(items, targetItemId = "b", newStyleId = "s1")!!
        assertEquals(0, out[0].styleImages.size)
        assertEquals(1, out[1].styleImages.size)
        assertEquals("s1", out[1].styleImages.first().styleId)
        assertEquals(StyleImageSource.LIBRARY, out[1].styleImages.first().source)
    }

    @Test
    fun nullTarget_fallsBackToFirstItem() {
        // Backward-compat: an unspecified link (legacy entry points) still hits items[0].
        val items = listOf(item("a"), item("b"))
        val out = linkStyleToOrderItems(items, targetItemId = null, newStyleId = "s1")!!
        assertEquals(1, out[0].styleImages.size)
        assertEquals(0, out[1].styleImages.size)
    }

    @Test
    fun appendsWhenItemAlreadyHasStylesUnderCap() {
        val items = listOf(item("a", listOf(lib("x"))))
        val out = linkStyleToOrderItems(items, targetItemId = "a", newStyleId = "s1")!!
        assertEquals(2, out[0].styleImages.size)
    }

    @Test
    fun duplicateLibraryRef_returnsNull() {
        val items = listOf(item("a", listOf(lib("s1"))))
        assertNull(linkStyleToOrderItems(items, targetItemId = "a", newStyleId = "s1"))
    }

    @Test
    fun atCap_returnsNull() {
        val items = listOf(item("a", listOf(lib("x"), lib("y"), lib("z"))))
        assertNull(linkStyleToOrderItems(items, targetItemId = "a", newStyleId = "s1"))
    }

    @Test
    fun targetItemNotFound_returnsNull() {
        val items = listOf(item("a"))
        assertNull(linkStyleToOrderItems(items, targetItemId = "missing", newStyleId = "s1"))
    }

    @Test
    fun emptyItems_returnsNull() {
        assertNull(linkStyleToOrderItems(emptyList(), targetItemId = null, newStyleId = "s1"))
    }
}

package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptPaletteTest {

    @Test
    fun light_style_maps_to_light_palette_with_white_background() {
        val palette = ReceiptImageStyle.LIGHT.palette()
        assertEquals(ReceiptPalette.Light, palette)
        assertEquals("#FFFFFF", palette.backgroundHex)
        assertEquals("#1E1C1A", palette.bodyTextHex)
        assertEquals("#C48E00", palette.accentHex)
    }

    @Test
    fun dark_style_maps_to_dark_palette_with_ink_background() {
        val palette = ReceiptImageStyle.DARK.palette()
        assertEquals(ReceiptPalette.Dark, palette)
        assertEquals("#121110", palette.backgroundHex)
        assertEquals("#E5E3DF", palette.bodyTextHex)
        assertEquals("#E8A800", palette.accentHex)
    }

    @Test
    fun light_watermark_ink_is_darker_than_dark_for_white_background_visibility() {
        assertEquals("#7D7970", ReceiptPalette.Light.watermarkInkHex)
        assertEquals("#A8A49D", ReceiptPalette.Dark.watermarkInkHex)
    }
}

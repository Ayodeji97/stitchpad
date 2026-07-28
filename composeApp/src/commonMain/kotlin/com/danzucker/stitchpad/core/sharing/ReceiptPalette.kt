package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle

/**
 * Colors that differ between the Light and Dark shared receipt image. Values
 * that already read correctly on both backgrounds (indigo header #2C3E7C,
 * white header text, green #2D9E6B, rush red #D93B3B, label #7D7970, and the
 * data-driven statusColorHex) stay hardcoded in the renderers.
 *
 * Dark values come from the original renderDarkBitmap/renderDarkImage; Light
 * values from renderLightPdf, with a darker saffron accent (#C48E00) and a
 * darker watermark ink (#7D7970) chosen for contrast on white.
 */
data class ReceiptPalette(
    val backgroundHex: String,
    val bodyTextHex: String,
    val dividerHex: String,
    val accentHex: String,
    val footerHex: String,
    val watermarkInkHex: String,
) {
    companion object {
        val Dark = ReceiptPalette(
            backgroundHex = "#121110",
            bodyTextHex = "#E5E3DF",
            dividerHex = "#3A3731",
            accentHex = "#E8A800",
            footerHex = "#3A3731",
            watermarkInkHex = "#A8A49D",
        )
        val Light = ReceiptPalette(
            backgroundHex = "#FFFFFF",
            bodyTextHex = "#1E1C1A",
            dividerHex = "#E8E6E3",
            accentHex = "#C48E00",
            footerHex = "#A8A49D",
            watermarkInkHex = "#7D7970",
        )
    }
}

fun ReceiptImageStyle.palette(): ReceiptPalette = when (this) {
    ReceiptImageStyle.LIGHT -> ReceiptPalette.Light
    ReceiptImageStyle.DARK -> ReceiptPalette.Dark
}

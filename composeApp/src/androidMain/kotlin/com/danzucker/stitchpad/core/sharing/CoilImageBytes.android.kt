package com.danzucker.stitchpad.core.sharing

import android.graphics.Bitmap
import android.graphics.Canvas
import coil3.BitmapImage
import coil3.Image
import java.io.ByteArrayOutputStream

actual fun Image.toPngBytes(): ByteArray? {
    val bitmap = toBitmapOrNull() ?: return null
    val stream = ByteArrayOutputStream()
    return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) stream.toByteArray() else null
}

private fun Image.toBitmapOrNull(): Bitmap? = when (this) {
    is BitmapImage -> bitmap
    else -> {
        val w = width.takeIf { it > 0 } ?: return null
        val h = height.takeIf { it > 0 } ?: return null
        // Coil may return a non-bitmap image (e.g. AnimatedImage). Rasterize via canvas.
        val rasterized = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(rasterized))
        rasterized
    }
}

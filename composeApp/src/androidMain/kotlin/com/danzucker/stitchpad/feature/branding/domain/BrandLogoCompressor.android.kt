package com.danzucker.stitchpad.feature.branding.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "BrandLogoCompressor"

actual class BrandLogoCompressor actual constructor() {

    @Suppress("ReturnCount") // explicit early returns for decode-failure + null-bitmap make the failure paths visible
    actual suspend fun compress(
        bytes: ByteArray,
        maxEdgePx: Int,
        jpegQuality: Int,
    ): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            val scaled = scaleToMaxEdge(source, maxEdgePx)
            val stream = ByteArrayOutputStream()
            val ok = scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
            // Avoid leaking the intermediate scaled bitmap when scaleToMaxEdge
            // allocated a new one. createScaledBitmap returns the same instance
            // when no scaling is needed, so guard against recycling our own input.
            if (scaled !== source) scaled.recycle()
            source.recycle()
            if (ok) stream.toByteArray() else null
        } catch (e: CancellationException) {
            // Honour structured concurrency — never swallow cancellation.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.w(tag = TAG, throwable = e) { "compress failed bytes=${bytes.size}" }
            null
        }
    }

    private fun scaleToMaxEdge(source: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxEdgePx) return source
        val ratio = maxEdgePx.toFloat() / longest
        val targetWidth = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}

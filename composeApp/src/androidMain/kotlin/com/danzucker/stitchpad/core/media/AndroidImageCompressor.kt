package com.danzucker.stitchpad.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Android [ImageCompressor]: decode → EXIF-orient → downscale → JPEG, off the main
 * thread. Shares [downscaleJpegBytes] with the camera capture launcher so gallery
 * picks and camera shots are normalised identically.
 */
class AndroidImageCompressor : ImageCompressor {
    override suspend fun compress(bytes: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray? =
        withContext(Dispatchers.Default) {
            downscaleJpegBytes(bytes, maxEdgePx, jpegQuality)
        }
}

/**
 * Decodes [raw] (downsampling during decode to bound memory), applies EXIF
 * orientation, scales the longest edge down to [maxEdgePx], and re-encodes JPEG at
 * [jpegQuality]. Returns null when the input cannot be decoded.
 *
 * Single ownership point for recycling: the `finally` recycles every bitmap this
 * function allocated, each exactly once (recycleIfDistinct guards against
 * double-recycling shared instances).
 */
@Suppress("ReturnCount")
internal fun downscaleJpegBytes(raw: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray? {
    val dimensions = raw.imageDimensions() ?: return null
    val decoded = BitmapFactory.decodeByteArray(
        raw, 0, raw.size,
        BitmapFactory.Options().apply { inSampleSize = dimensions.sampleSize(maxEdgePx) }
    ) ?: return null

    val rotation = runCatching {
        ExifInterface(ByteArrayInputStream(raw)).rotationDegrees()
    }.getOrDefault(0)

    var oriented: Bitmap? = null
    var scaled: Bitmap? = null
    try {
        oriented = if (rotation == 0) decoded else decoded.rotated(rotation)
        scaled = oriented.scaledToMaxDimension(maxEdgePx)
        return scaled.toJpegBytes(jpegQuality)
    } finally {
        scaled?.recycleIfDistinct(oriented ?: decoded, decoded)
        oriented?.recycleIfDistinct(decoded)
        decoded.recycle()
    }
}

private data class ImageDimensions(val width: Int, val height: Int) {
    fun sampleSize(maxDim: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxDim || height / (sampleSize * 2) >= maxDim) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

private fun ByteArray.imageDimensions(): ImageDimensions? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        ImageDimensions(bounds.outWidth, bounds.outHeight)
    } else {
        null
    }
}

private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun Bitmap.scaledToMaxDimension(maxDim: Int): Bitmap {
    val longEdge = maxOf(width, height).toFloat()
    val scale = longEdge / maxDim
    return if (scale > 1f) {
        val newW = (width / scale).toInt().coerceAtLeast(1)
        val newH = (height / scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(this, newW, newH, true)
    } else {
        this
    }
}

private fun Bitmap.recycleIfDistinct(vararg others: Bitmap) {
    if (others.none { this === it }) {
        recycle()
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun ExifInterface.rotationDegrees(): Int = when (
    getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

package com.danzucker.stitchpad.core.media

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

private const val TAG = "IosImageCompressor"
private const val QUALITY_DIVISOR = 100.0

/**
 * iOS [ImageCompressor]: decode bytes → downscale → JPEG, off the main thread.
 * Shares [downscaledJpegBytes] with the camera capture launcher.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
class IosImageCompressor : ImageCompressor {
    @Suppress("ReturnCount")
    override suspend fun compress(bytes: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray? =
        withContext(Dispatchers.Default) {
            try {
                val nsData = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                // UIImage(data:) applies the source's EXIF orientation automatically.
                val source = UIImage.imageWithData(nsData) ?: return@withContext null
                source.downscaledJpegBytes(maxEdgePx, jpegQuality)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.w(tag = TAG, throwable = e) { "compress failed bytes=${bytes.size}" }
                null
            }
        }
}

/**
 * Scales the longest edge down to [maxEdgePx] and encodes JPEG at [jpegQuality].
 * The UIImage's orientation is baked in by drawInRect. Returns null if encoding fails.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIImage.downscaledJpegBytes(maxEdgePx: Int, jpegQuality: Int): ByteArray? {
    val resized = scaleToMaxEdge(maxEdgePx)
    val jpegData = UIImageJPEGRepresentation(resized, jpegQuality / QUALITY_DIVISOR) ?: return null
    return jpegData.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.scaleToMaxEdge(maxEdgePx: Int): UIImage {
    val (width, height) = size.useContents { width to height }
    val longest = maxOf(width, height)
    // Nothing to do for an empty image or one already within the ceiling.
    if (width <= 0.0 || height <= 0.0 || longest <= maxEdgePx.toDouble()) return this
    val ratio = maxEdgePx.toDouble() / longest
    val targetWidth = width * ratio
    val targetHeight = height * ratio
    val targetSize = cValue<CGSize> {
        this.width = targetWidth
        this.height = targetHeight
    }
    // scale=1.0 renders at the requested pixel size instead of multiplying by the
    // device display scale; opaque=false preserves transparency before JPEG flattens it.
    UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val result = UIGraphicsGetImageFromCurrentImageContext() ?: this
    UIGraphicsEndImageContext()
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    val result = ByteArray(len)
    if (len == 0) return result
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}

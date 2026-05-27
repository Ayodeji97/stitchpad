package com.danzucker.stitchpad.feature.branding.domain

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

private const val TAG = "BrandLogoCompressor"
private const val QUALITY_DIVISOR = 100.0

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
actual class BrandLogoCompressor actual constructor() {

    @Suppress("ReturnCount") // staged early-returns make decode / encode / null failures readable
    actual suspend fun compress(
        bytes: ByteArray,
        maxEdgePx: Int,
        jpegQuality: Int,
    ): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val nsData = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            val source = UIImage.imageWithData(nsData) ?: return@withContext null
            val resized = source.scaleToMaxEdge(maxEdgePx)
            val jpegData = UIImageJPEGRepresentation(resized, jpegQuality / QUALITY_DIVISOR)
                ?: return@withContext null
            nsDataToByteArray(jpegData)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.w(tag = TAG, throwable = e) { "compress failed bytes=${bytes.size}" }
            null
        }
    }

    private fun UIImage.scaleToMaxEdge(maxEdgePx: Int): UIImage {
        val (width, height) = size.useContents { width to height }
        val longest = maxOf(width, height)
        if (longest <= maxEdgePx.toDouble()) return this
        val ratio = maxEdgePx.toDouble() / longest
        val targetWidth = width * ratio
        val targetHeight = height * ratio
        val targetSize = cValue<CGSize> {
            this.width = targetWidth
            this.height = targetHeight
        }
        // scale=1.0 produces a bitmap at the pixel dimensions we requested
        // instead of multiplying by the device's display scale. opaque=false
        // preserves transparency for PNG sources before JPEG re-encoding
        // (JPEG itself will then flatten any alpha onto black).
        UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
        drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        val result = UIGraphicsGetImageFromCurrentImageContext() ?: this
        UIGraphicsEndImageContext()
        return result
    }

    private fun nsDataToByteArray(data: NSData): ByteArray {
        val length = data.length.toInt()
        val result = ByteArray(length)
        if (length == 0) return result
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.toULong())
        }
        return result
    }
}

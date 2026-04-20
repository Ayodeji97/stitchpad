package com.danzucker.stitchpad.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

@Stable
actual class ImageCaptureLauncher internal constructor(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberImageCaptureLauncher(
    onResult: (ByteArray?) -> Unit
): ImageCaptureLauncher {
    val currentOnResult by rememberUpdatedState(onResult)

    // Hold the delegate here so it isn't garbage-collected while the picker is up.
    val delegateHolder = remember { DelegateHolder() }

    return remember {
        ImageCaptureLauncher {
            if (!UIImagePickerController.isSourceTypeAvailable(
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                )
            ) {
                currentOnResult(null)
                return@ImageCaptureLauncher
            }

            val picker = UIImagePickerController()
            picker.sourceType =
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera

            val delegate = CameraPickerDelegate { image ->
                delegateHolder.current = null
                picker.dismissViewControllerAnimated(true, completion = null)
                val bytes = image?.toDownscaledJpegBytes()
                currentOnResult(bytes)
            }
            delegateHolder.current = delegate
            picker.delegate = delegate

            val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootVc?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class DelegateHolder {
    var current: CameraPickerDelegate? = null
}

@OptIn(BetaInteropApi::class)
private class CameraPickerDelegate(
    private val onFinish: (UIImage?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        onFinish(image)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        onFinish(null)
    }
}

private const val MAX_DIM = 1920.0
private const val JPEG_QUALITY = 0.85

@Suppress("ReturnCount")
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toDownscaledJpegBytes(): ByteArray? {
    val resized = downscale() ?: this
    val data: NSData = UIImageJPEGRepresentation(resized, JPEG_QUALITY) ?: return null
    val length = data.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

@Suppress("ReturnCount")
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.downscale(): UIImage? {
    var width = 0.0
    var height = 0.0
    size.useContents {
        width = this.width
        height = this.height
    }
    if (width <= 0.0 || height <= 0.0) return null
    val longEdge = maxOf(width, height)
    if (longEdge <= MAX_DIM) return this
    val ratio = MAX_DIM / longEdge
    val newWidth = width * ratio
    val newHeight = height * ratio
    val newSize = CGSizeMake(newWidth, newHeight)
    UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, newWidth, newHeight))
    val resized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return resized
}

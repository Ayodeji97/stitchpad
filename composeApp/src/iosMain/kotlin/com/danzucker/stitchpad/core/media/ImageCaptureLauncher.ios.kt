package com.danzucker.stitchpad.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.danzucker.stitchpad.core.platform.activeKeyWindow
import kotlinx.cinterop.BetaInteropApi
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

@Stable
actual class ImageCaptureLauncher internal constructor(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@OptIn(BetaInteropApi::class)
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
                val bytes = image?.downscaledJpegBytes(MAX_DIM, JPEG_QUALITY)
                currentOnResult(bytes)
            }
            delegateHolder.current = delegate
            picker.delegate = delegate

            val rootVc = activeKeyWindow()?.rootViewController
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

// Shares the downscale used for gallery picks (see IosImageCompressor).
private const val MAX_DIM = 1920
private const val JPEG_QUALITY = 85

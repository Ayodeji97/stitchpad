package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.platform.activeKeyWindow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class ImageSharer {

    actual suspend fun shareImage(bytes: ByteArray, caption: String?) {
        if (bytes.isEmpty()) return
        // Let any dismissing Compose ModalBottomSheet finish before presenting a
        // UIKit modal — UIKit silently refuses to present mid-transition.
        delay(SHARE_PRESENT_DELAY_MS)
        withContext(Dispatchers.Main) {
            val image = UIImage.imageWithData(bytes.toNSData()) ?: return@withContext
            val items: List<Any> = buildList {
                add(image)
                if (!caption.isNullOrBlank()) add(caption)
            }
            val rootVC = activeKeyWindow()?.rootViewController ?: return@withContext
            val presenter = topmostPresenter(rootVC)
            val activityVC = UIActivityViewController(activityItems = items, applicationActivities = null)
            // iPad: a popover source is required or the sheet fails to present.
            activityVC.popoverPresentationController?.apply {
                sourceView = presenter.view
                presenter.view.bounds.useContents {
                    sourceRect = CGRectMake(
                        origin.x + size.width / 2.0,
                        origin.y + size.height / 2.0,
                        0.0,
                        0.0,
                    )
                }
            }
            presenter.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    private fun topmostPresenter(root: UIViewController): UIViewController {
        var vc: UIViewController = root
        while (true) {
            val next = vc.presentedViewController ?: return vc
            if (next.isBeingDismissed()) return vc
            vc = next
        }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private companion object {
        const val SHARE_PRESENT_DELAY_MS = 450L
    }
}

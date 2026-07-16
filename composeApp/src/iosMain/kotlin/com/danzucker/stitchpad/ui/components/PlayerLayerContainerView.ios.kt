package com.danzucker.stitchpad.ui.components

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIEvent
import platform.UIKit.UIView

/**
 * Hosts an [AVPlayerLayer] and keeps it sized to the view's bounds. Layer frames don't
 * auto-follow their host view, so we resync on every layout pass (actions disabled to avoid
 * an implicit resize animation). Shared by [VideoBackground] and [TutorialVideoPlayer] — the
 * bare-layer container is the one native-video hosting pattern that renders through Compose
 * interop (see TutorialVideoPlayer's KDoc for the AVPlayerViewController story).
 *
 * [onTap] (optional) fires on a finger-up inside the view, letting an interactive surface
 * toggle play/pause without gesture-recognizer ceremony; leave null for passive surfaces.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PlayerLayerContainerView(
    private val playerLayer: AVPlayerLayer,
    private val onTap: (() -> Unit)? = null,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.setFrame(bounds)
        CATransaction.commit()
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesEnded(touches, withEvent)
        onTap?.invoke()
    }
}

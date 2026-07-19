package com.danzucker.stitchpad.ui.components

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

/**
 * Hosts an [AVPlayerLayer] and keeps it sized to the view's bounds. Layer frames don't
 * auto-follow their host view, so we resync on every layout pass (actions disabled to avoid
 * an implicit resize animation). Shared by [VideoBackground] and [TutorialVideoPlayer] — the
 * bare-layer container is the one native-video hosting pattern that renders through Compose
 * interop (see TutorialVideoPlayer's KDoc for the AVPlayerViewController story).
 */
@OptIn(ExperimentalForeignApi::class)
internal class PlayerLayerContainerView(
    private val playerLayer: AVPlayerLayer,
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
}

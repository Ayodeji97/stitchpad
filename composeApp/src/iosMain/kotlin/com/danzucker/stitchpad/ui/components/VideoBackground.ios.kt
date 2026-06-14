package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerLooper
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

/**
 * iOS [VideoBackground] backed by [AVQueuePlayer] + [AVPlayerLooper] for seamless,
 * gap-free looping. Muted, auto-playing, and centre-cropped to fill via
 * [AVLayerVideoGravityResizeAspectFill].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoBackground(uri: String, modifier: Modifier) {
    val playback = remember(uri) { VideoPlayback(uri) }

    DisposableEffect(playback) {
        playback.play()
        onDispose { playback.release() }
    }

    UIKitView(
        factory = {
            val playerLayer = AVPlayerLayer.playerLayerWithPlayer(playback.player).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
            }
            VideoContainerView(playerLayer)
        },
        modifier = modifier,
    )
}

/**
 * Owns the looping player so it survives recomposition and can be torn down cleanly.
 */
@OptIn(ExperimentalForeignApi::class)
private class VideoPlayback(uri: String) {
    val player: AVQueuePlayer
    private val looper: AVPlayerLooper

    init {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        val item = AVPlayerItem(uRL = url)
        player = AVQueuePlayer().apply { muted = true }
        looper = AVPlayerLooper.playerLooperWithPlayer(player = player, templateItem = item)
    }

    fun play() = player.play()

    fun release() {
        looper.disableLooping()
        player.pause()
        player.removeAllItems()
    }
}

/**
 * Hosts the [AVPlayerLayer] and keeps it sized to the view's bounds. Layer frames don't
 * auto-follow their host view, so we resync on every layout pass (actions disabled to
 * avoid an implicit resize animation).
 */
@OptIn(ExperimentalForeignApi::class)
private class VideoContainerView(
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

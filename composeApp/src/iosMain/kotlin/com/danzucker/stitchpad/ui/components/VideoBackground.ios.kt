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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * iOS [VideoBackground] backed by [AVQueuePlayer] + [AVPlayerLooper] for seamless,
 * gap-free looping. Muted, auto-playing, and centre-cropped to fill via
 * [AVLayerVideoGravityResizeAspectFill].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoBackground(uri: String, modifier: Modifier) {
    val playback = remember(uri) { VideoPlayback(uri) }

    // Pause while the app is backgrounded; resume on return; release on dispose.
    DisposableEffect(playback) {
        playback.play()
        val center = NSNotificationCenter.defaultCenter
        val onBackground = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
        ) { _ -> playback.pause() }
        val onForeground = center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = null,
        ) { _ -> playback.play() }
        onDispose {
            center.removeObserver(onBackground)
            center.removeObserver(onForeground)
            playback.release()
        }
    }

    UIKitView(
        factory = {
            val playerLayer = AVPlayerLayer.playerLayerWithPlayer(playback.player).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
            }
            PlayerLayerContainerView(playerLayer)
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

    fun pause() = player.pause()

    fun release() {
        looper.disableLooping()
        player.pause()
        player.removeAllItems()
    }
}

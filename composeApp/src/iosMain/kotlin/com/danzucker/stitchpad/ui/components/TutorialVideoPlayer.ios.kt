package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

/**
 * iOS [TutorialVideoPlayer] backed by [AVPlayerViewController], which provides play/pause,
 * scrubber, mute, fullscreen, and caption controls for free. Plays once with sound. Reports
 * readiness via [onLoadingChanged] (false once the item is ready and playing) so the caller can
 * overlay a branded loading indicator during the first buffer.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TutorialVideoPlayer(
    uri: String,
    modifier: Modifier,
    onLoadingChanged: (Boolean) -> Unit,
) {
    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val playback = remember(uri) {
        TutorialPlayback(uri) { currentOnLoadingChanged(false) }
    }

    DisposableEffect(playback) {
        playback.play()
        onDispose { playback.release() }
    }

    UIKitViewController(
        factory = { playback.controller },
        modifier = modifier,
    )
}

/**
 * Owns the player + view controller so they survive recomposition and tear down cleanly.
 * A periodic time observer fires while playback progresses; the first tick where the item is
 * ready signals "loaded" via [onReady]. Reporting is idempotent, so the brief window before the
 * callback is wired up is harmless.
 */
@OptIn(ExperimentalForeignApi::class)
private class TutorialPlayback(uri: String, private val onReady: () -> Unit) {
    val controller: AVPlayerViewController
    private val player: AVPlayer
    private var timeObserver: Any? = null

    init {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        player = AVPlayer(uRL = url)
        controller = AVPlayerViewController().apply {
            this.player = player
            showsPlaybackControls = true
        }
        val interval = CMTimeMakeWithSeconds(0.25, 600)
        timeObserver = player.addPeriodicTimeObserverForInterval(interval, queue = null) { _ ->
            if (player.currentItem?.status == AVPlayerItemStatusReadyToPlay && player.rate > 0f) {
                onReady()
            }
        }
    }

    fun play() = player.play()

    fun pause() = player.pause()

    fun release() {
        timeObserver?.let { player.removeTimeObserver(it) }
        timeObserver = null
        player.pause()
    }
}

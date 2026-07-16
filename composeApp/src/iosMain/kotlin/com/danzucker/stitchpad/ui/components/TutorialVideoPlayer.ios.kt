package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import kotlin.coroutines.coroutineContext

private const val TAG = "TutorialVideoPlayer"
private const val STATUS_POLL_MS = 500L

/**
 * iOS [TutorialVideoPlayer] backed by a bare [AVPlayerLayer] in a [UIKitView]-hosted
 * [PlayerLayerContainerView] — the same hosting pattern as [VideoBackground], the one
 * native-video path proven to render inside this app's Compose tree. Plays with sound;
 * tapping the video toggles play/pause; a finished clip rewinds to the start so a tap
 * replays it; returning from the background resumes playback. Reports buffering via
 * [onLoadingChanged] (true whenever the player is waiting to play, so stalls re-show the
 * caller's loading indicator) and unrecoverable item failures via [onPlaybackError].
 *
 * Deliberately NOT `AVPlayerViewController`: hosted through Compose interop its video surface
 * never renders (black screen, audio playing) — neither via `UIKitViewController` interop nor
 * with its view embedded in a `UIKitView`, likely because its internal player layer activates
 * on view-controller-containment lifecycle callbacks that interop never delivers. The cost is
 * no native scrubber/caption controls; custom Compose controls are the follow-up if needed.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TutorialVideoPlayer(
    uri: String,
    modifier: Modifier,
    onLoadingChanged: (Boolean) -> Unit,
    onPlaybackError: () -> Unit,
) {
    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val playback = remember(uri) { TutorialPlayback(uri) }

    DisposableEffect(playback) {
        playback.play()
        // System-initiated pauses (backgrounding, calls, Siri) drop the rate silently; with no
        // native controls the foreground hook is the automatic resume path (tap is the manual one).
        val onForeground = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = null,
        ) { _ -> playback.play() }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(onForeground)
            playback.release()
        }
    }

    // Poll the item/player status (KVO from Kotlin/Native isn't worth the ceremony): surface
    // terminal failures, and mirror "waiting to play" into the caller's loading overlay so the
    // first buffer AND mid-playback stalls both show the indicator instead of a frozen frame.
    LaunchedEffect(playback) {
        while (coroutineContext.isActive) {
            if (playback.hasFailed()) {
                // Log the source kind, never the uri itself: remote uris are Firebase download
                // URLs whose token= must not reach crash telemetry (see AppLogger's rules).
                val sourceKind = if (uri.startsWith("file:")) "local" else "remote"
                AppLogger.e(tag = TAG) { "playback failed src=$sourceKind" }
                currentOnPlaybackError()
                break
            }
            currentOnLoadingChanged(playback.isWaitingToPlay())
            delay(STATUS_POLL_MS)
        }
    }

    // key(playback): the interop factory runs once per node and captures the layer, so a new
    // uri (hence new playback) must rebuild the node or the old player would stay on screen.
    key(playback) {
        UIKitView(
            factory = {
                PlayerLayerContainerView(playback.playerLayer) { playback.togglePlayPause() }
            },
            modifier = modifier,
        )
    }
}

/**
 * Owns the [AVPlayer] and its [AVPlayerLayer] so they survive recomposition and tear down
 * cleanly. When the clip plays to the end it rewinds to the first frame and stays paused,
 * so the next tap replays it without re-resolving the uri.
 */
@OptIn(ExperimentalForeignApi::class)
private class TutorialPlayback(uri: String) {
    val player: AVPlayer
    val playerLayer: AVPlayerLayer
    private var endObserver: Any? = null

    init {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        player = AVPlayer(uRL = url)
        playerLayer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
            videoGravity = AVLayerVideoGravityResizeAspect
        }
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = null,
        ) { _ -> player.seekToTime(CMTimeMake(value = 0, timescale = 1)) }
    }

    fun hasFailed(): Boolean = player.currentItem?.status == AVPlayerItemStatusFailed

    fun isWaitingToPlay(): Boolean =
        player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate

    fun play() = player.play()

    fun togglePlayPause() = if (player.rate > 0f) player.pause() else player.play()

    fun release() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        player.pause()
        // Detach so a superseded playback can't keep rendering (or retaining) its player.
        playerLayer.player = null
    }
}

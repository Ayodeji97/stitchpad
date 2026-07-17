package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import kotlin.coroutines.coroutineContext

private const val TAG = "TutorialVideoPlayer"
private const val STATUS_POLL_MS = 500L

/** UI-facing playback snapshot published by the status poll for the controls overlay. */
private data class PlayerSnapshot(
    val isPlaying: Boolean = false,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double? = null,
)

/**
 * iOS [TutorialVideoPlayer] backed by a bare [AVPlayerLayer] in a [UIKitView]-hosted
 * [PlayerLayerContainerView] — the same hosting pattern as [VideoBackground], the one
 * native-video path proven to render inside this app's Compose tree. Plays with sound;
 * a [TutorialPlayerControls] overlay provides play/pause, ±10s skip, and a scrubber; a
 * finished clip rewinds to the start (paused) so play replays it; returning from the
 * background resumes playback. Reports buffering via [onLoadingChanged] (true whenever
 * the player is waiting to play, so stalls re-show the caller's loading indicator) and
 * unrecoverable item failures via [onPlaybackError].
 *
 * Deliberately NOT `AVPlayerViewController`: hosted through Compose interop its video surface
 * never renders (black screen, audio playing) — neither via `UIKitViewController` interop nor
 * with its view embedded in a `UIKitView`, likely because its internal player layer activates
 * on view-controller-containment lifecycle callbacks that interop never delivers. Transport
 * controls are the shared [TutorialPlayerControls] Compose overlay layered above the interop
 * surface.
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
    var snapshot by remember(playback) { mutableStateOf(PlayerSnapshot()) }

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
            snapshot = PlayerSnapshot(
                isPlaying = playback.isPlaying(),
                positionSeconds = playback.positionSeconds(),
                durationSeconds = playback.durationSeconds(),
            )
            delay(STATUS_POLL_MS)
        }
    }

    // key(playback): the interop factory runs once per node and captures the layer, so a new
    // uri (hence new playback) must rebuild the node or the old player would stay on screen.
    key(playback) {
        Box(modifier = modifier) {
            UIKitView(
                factory = { PlayerLayerContainerView(playback.playerLayer) },
                modifier = Modifier.fillMaxSize(),
            )
            TutorialPlayerControls(
                isPlaying = snapshot.isPlaying,
                positionSeconds = snapshot.positionSeconds,
                durationSeconds = snapshot.durationSeconds,
                onPlayPause = {
                    playback.togglePlayPause()
                    snapshot = snapshot.copy(isPlaying = playback.isPlaying())
                },
                onSeekBy = { delta ->
                    snapshot = snapshot.copy(positionSeconds = playback.seekBy(delta))
                },
                onSeekTo = { seconds ->
                    snapshot = snapshot.copy(positionSeconds = playback.seekTo(seconds))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
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

    fun isPlaying(): Boolean = player.rate > 0f

    fun positionSeconds(): Double = CMTimeGetSeconds(player.currentTime())

    /** Item duration in seconds, or null while unknown (still resolving / indefinite). */
    fun durationSeconds(): Double? {
        val item = player.currentItem ?: return null
        val seconds = CMTimeGetSeconds(item.duration)
        return seconds.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Seeks to [seconds] clamped to the playable range; returns the clamped target. */
    fun seekTo(seconds: Double): Double {
        val upperBound = durationSeconds()
        val clamped = if (upperBound != null) {
            seconds.coerceIn(0.0, upperBound)
        } else {
            seconds.coerceAtLeast(0.0)
        }
        player.seekToTime(CMTimeMakeWithSeconds(clamped, preferredTimescale = 600))
        return clamped
    }

    fun seekBy(deltaSeconds: Double): Double = seekTo(positionSeconds() + deltaSeconds)

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

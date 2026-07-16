package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import kotlin.coroutines.coroutineContext

private const val TAG = "TutorialVideoPlayer"
private const val FAILURE_POLL_MS = 500L

/**
 * iOS [TutorialVideoPlayer] backed by a bare [AVPlayerLayer] in a [UIKitView] container —
 * the same hosting pattern as [VideoBackground], which is the one native-video path proven to
 * render inside this app's Compose tree. Plays once with sound. Reports readiness via
 * [onLoadingChanged] (false once the item is ready and playing) so the caller can overlay a
 * branded loading indicator during the first buffer, and unrecoverable item failures via
 * [onPlaybackError].
 *
 * Deliberately NOT `AVPlayerViewController`: hosted through Compose interop its video surface
 * never renders (black screen, audio playing) — neither via `UIKitViewController` interop nor
 * with its view embedded in a `UIKitView`, likely because its internal player layer activates
 * on view-controller-containment lifecycle callbacks that interop never delivers. The cost is
 * no free native controls; the clip plays once and the screen's Close button exits.
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
    val playback = remember(uri) {
        TutorialPlayback(uri) { currentOnLoadingChanged(false) }
    }

    DisposableEffect(playback) {
        playback.play()
        onDispose { playback.release() }
    }

    // The periodic time observer only ticks while time advances, so it can never see a load
    // failure — poll the item status instead (KVO from Kotlin/Native isn't worth the ceremony).
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
            delay(FAILURE_POLL_MS)
        }
    }

    UIKitView(
        factory = {
            val playerLayer = AVPlayerLayer.playerLayerWithPlayer(playback.player).apply {
                videoGravity = AVLayerVideoGravityResizeAspect
            }
            PlayerContainerView(playerLayer)
        },
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
    val player: AVPlayer
    private var timeObserver: Any? = null

    init {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        player = AVPlayer(uRL = url)
        val interval = CMTimeMakeWithSeconds(0.25, 600)
        timeObserver = player.addPeriodicTimeObserverForInterval(interval, queue = null) { _ ->
            if (player.currentItem?.status == AVPlayerItemStatusReadyToPlay && player.rate > 0f) {
                onReady()
            }
        }
    }

    fun hasFailed(): Boolean = player.currentItem?.status == AVPlayerItemStatusFailed

    fun play() = player.play()

    fun pause() = player.pause()

    fun release() {
        timeObserver?.let { player.removeTimeObserver(it) }
        timeObserver = null
        player.pause()
    }
}

/**
 * Hosts the [AVPlayerLayer] and keeps it sized to the view's bounds. Layer frames don't
 * auto-follow their host view, so we resync on every layout pass (actions disabled to
 * avoid an implicit resize animation). Mirrors [VideoBackground]'s VideoContainerView.
 */
@OptIn(ExperimentalForeignApi::class)
private class PlayerContainerView(
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

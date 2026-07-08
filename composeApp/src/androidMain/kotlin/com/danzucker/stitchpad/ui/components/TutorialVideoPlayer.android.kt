package com.danzucker.stitchpad.ui.components

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.danzucker.stitchpad.R
import com.danzucker.stitchpad.core.logging.AppLogger

private const val TAG = "TutorialVideoPlayer"

/**
 * Android [TutorialVideoPlayer] backed by Media3 ExoPlayer with the default [PlayerView]
 * controller (play/pause, scrubber, mute, settings) enabled. Plays once with sound. Reports
 * buffering via [onLoadingChanged] so the caller can overlay a branded loading indicator; the
 * PlayerView's own buffering spinner is suppressed to avoid a double indicator. Renders to a
 * TextureView (see tutorial_player_view.xml) and surfaces player errors via [onPlaybackError].
 */
@Composable
actual fun TutorialVideoPlayer(
    uri: String,
    modifier: Modifier,
    onLoadingChanged: (Boolean) -> Unit,
    onPlaybackError: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                currentOnLoadingChanged(
                    playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE,
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(tag = TAG, throwable = error) {
                    "playback failed code=${error.errorCodeName} uri=$uri"
                }
                currentOnPlaybackError()
            }
        }
        exoPlayer.addListener(listener)
        // Sample the current state too: a cached/local clip can reach STATE_READY before this
        // effect attaches the listener, so relying on the callback alone would strand the overlay.
        currentOnLoadingChanged(
            exoPlayer.playbackState == Player.STATE_BUFFERING ||
                exoPlayer.playbackState == Player.STATE_IDLE,
        )
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Pause while backgrounded (the user resumes via the controller on return); release on dispose.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val playerView = LayoutInflater.from(ctx)
                .inflate(R.layout.tutorial_player_view, null, false) as PlayerView
            playerView.apply {
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        // Rebind in update, not factory: factory runs once, but a uri change (retry after a
        // stream error resolves to the cached copy) creates a fresh player — without this the
        // view keeps rendering the old released player while the new one plays audio unseen.
        update = { playerView -> playerView.player = exoPlayer },
        onRelease = { playerView -> playerView.player = null },
    )
}

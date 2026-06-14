package com.danzucker.stitchpad.ui.components

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.danzucker.stitchpad.R

/**
 * Android [VideoBackground] backed by Media3 ExoPlayer. Muted, auto-playing, and
 * looping a single clip. The [PlayerView] is inflated from XML so it uses a
 * TextureView surface (see view_video_background.xml) for clean Compose compositing.
 */
@Composable
actual fun VideoBackground(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val playerView = LayoutInflater.from(ctx)
                .inflate(R.layout.view_video_background, null) as PlayerView
            playerView.player = exoPlayer
            playerView
        },
        onRelease = { playerView -> playerView.player = null },
    )
}

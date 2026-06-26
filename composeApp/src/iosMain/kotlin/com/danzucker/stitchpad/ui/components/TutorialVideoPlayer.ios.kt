package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

/**
 * iOS [TutorialVideoPlayer] backed by [AVPlayerViewController], which provides play/pause,
 * scrubber, mute, fullscreen, and caption controls for free. Plays once with sound.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TutorialVideoPlayer(uri: String, modifier: Modifier) {
    val controller = remember(uri) {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        AVPlayerViewController().apply {
            player = AVPlayer(uRL = url)
            showsPlaybackControls = true
        }
    }

    DisposableEffect(controller) {
        controller.player?.play()
        onDispose {
            controller.player?.pause()
        }
    }

    UIKitViewController(
        factory = { controller },
        modifier = modifier,
    )
}

package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-bleed how-to video player WITH native transport controls (play/pause, scrubber, mute,
 * fullscreen, and captions where the source provides them). Auto-plays [uri] once with sound;
 * does not loop. Sibling to [VideoBackground] — that one is decorative/no-controls, this one
 * is the interactive tutorial surface. The caller owns loading/error/retry around it.
 *
 * @param uri playable video URI — a remote https URL or a local `file://` path.
 * @param onLoadingChanged reports buffering state — `true` while loading/buffering (no frame
 *   yet), `false` once playback is ready. The caller overlays a loading indicator on `true`.
 * @param onPlaybackError reports an unrecoverable player failure (decoder init, bad stream).
 *   The caller swaps to its error + retry UI; without this the surface just stays black.
 */
@Composable
expect fun TutorialVideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    onLoadingChanged: (Boolean) -> Unit = {},
    onPlaybackError: () -> Unit = {},
)

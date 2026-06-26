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
 */
@Composable
expect fun TutorialVideoPlayer(uri: String, modifier: Modifier = Modifier)

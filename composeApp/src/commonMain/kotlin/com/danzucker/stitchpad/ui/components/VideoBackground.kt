package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-bleed, muted, seamlessly looping background video, centre-cropped to fill the
 * available space (like [androidx.compose.ui.layout.ContentScale.Crop]).
 *
 * Purely decorative — it renders no controls and exposes no state. The caller is
 * responsible for any scrim/overlay drawn on top for text legibility, and for stacking
 * a poster image *underneath* so nothing flashes black before the first frame renders.
 *
 * @param uri platform video URI, e.g. `Res.getUri("files/welcome_video.mp4")`.
 */
@Composable
expect fun VideoBackground(uri: String, modifier: Modifier = Modifier)

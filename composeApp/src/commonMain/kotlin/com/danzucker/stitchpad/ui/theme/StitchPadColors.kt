package com.danzucker.stitchpad.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand-specific semantic color slots that Material3's [androidx.compose.material3.ColorScheme]
 * doesn't model.
 *
 * - [heritageAccent] — saffron, used ONLY on rare moments (PRO badges,
 *   star/check marks, Verified Tailor chips, achievement bursts). Same hex
 *   in both modes — saffron doesn't tonally shift.
 * - [brandAccent] — a brighter step of the indigo ramp, for hero
 *   illustration strokes, marketing-hero accents, decorative motifs on
 *   empty states. Distinct from `MaterialTheme.colorScheme.primary` which
 *   is reserved for the deeper brand color used on wordmarks, links, and
 *   CTAs.
 *
 * Two slots only — anything else (elevated surfaces, muted text) is
 * already covered by Material3's surface / onSurfaceVariant slots.
 */
@Immutable
data class StitchPadColors(
    val heritageAccent: Color,
    val brandAccent: Color,
)

val LightStitchPadColors = StitchPadColors(
    heritageAccent = DesignTokens.saffron500,
    brandAccent = DesignTokens.indigo400,
)

val DarkStitchPadColors = StitchPadColors(
    heritageAccent = DesignTokens.saffron500, // saffron doesn't tonally shift
    brandAccent = DesignTokens.indigo200,
)

val LocalStitchPadColors = staticCompositionLocalOf<StitchPadColors> {
    error("StitchPadColors not provided — wrap content in StitchPadTheme")
}

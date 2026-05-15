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
 *
 * ## Token usage guide
 *
 * | Token                                          | Use for                                                           |
 * |------------------------------------------------|-------------------------------------------------------------------|
 * | `MaterialTheme.colorScheme.primary`            | Filled CTAs, brand fills (button bg, FAB, primary chip fill)      |
 * | `MaterialTheme.colorScheme.primaryContainer`   | Subtle brand tint surfaces (chip bg, hero card tint, icon circles)|
 * | `MaterialTheme.colorScheme.onPrimaryContainer` | Text/icons rendered on `primaryContainer` surfaces                |
 * | `MaterialTheme.colorScheme.onPrimary`          | Text/icons rendered on `primary` fills                            |
 * | `MaterialTheme.colorScheme.tertiary`           | Sienna accent — supplementary emphasis, "workshop warmth", warning tier between brand and red |
 * | `LocalStitchPadColors.current.heritageAccent`  | RARE — PRO badges, Verified Tailor chips, achievement bursts. Saffron #E8A800 in both modes. |
 * | `LocalStitchPadColors.current.brandAccent`     | Lifted indigo for content on hardcoded dark surfaces (AuthCard, workshop hero, dark-gradient cards). Auto-tracks light/dark. |
 *
 * For the OutstandingBalancesCard "due this week" tier, use `colorScheme.tertiary`
 * (soft warning between orange "due today" and muted "later").
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

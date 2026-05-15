package com.danzucker.stitchpad.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light Adire Atelier color scheme. Indigo primary, sienna tertiary,
 * warm paper background. Function-style (vs the old val-style) so future
 * platform-specific tweaks (Android 12+ dynamic color) don't require
 * restructuring the Theme.kt orchestrator.
 */
fun stitchPadLightColorScheme(): ColorScheme = lightColorScheme(
    primary = DesignTokens.indigo500,
    onPrimary = Color.White,
    primaryContainer = DesignTokens.indigo50,
    onPrimaryContainer = DesignTokens.indigo700,
    secondary = DesignTokens.indigo700,
    onSecondary = Color.White,
    secondaryContainer = DesignTokens.indigo100,
    onSecondaryContainer = DesignTokens.indigo900,
    tertiary = DesignTokens.sienna500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCEFE5), // sienna50 inline — not worth a token
    onTertiaryContainer = DesignTokens.sienna700,
    background = DesignTokens.paperLight,
    onBackground = DesignTokens.neutral800,
    surface = Color.White,
    onSurface = DesignTokens.neutral800,
    surfaceVariant = DesignTokens.neutral100,
    onSurfaceVariant = DesignTokens.neutral600,
    error = DesignTokens.error500,
    onError = Color.White,
    errorContainer = DesignTokens.error50,
    onErrorContainer = DesignTokens.error500,
    outline = DesignTokens.neutral200,
    outlineVariant = DesignTokens.neutral100,
)

/**
 * Dark Adire Atelier color scheme. Indigo tonally lifts (indigo500 → indigo300)
 * so the brand color reads clearly on warm-ink backgrounds. Sienna also lifts
 * (sienna500 → sienna300). Background is warm-ink, not pure black.
 */
fun stitchPadDarkColorScheme(): ColorScheme = darkColorScheme(
    // Sits between indigo300 (too light — white text fails AA at 3.7:1)
    // and indigo500 (too dark — brand text on inkDark bg drops to 2:1).
    // indigo400 (#5871B8) gives white text 4.7:1 AA AND brand text on
    // bg 5.5:1 AA. Sweet spot.
    primary = DesignTokens.indigo400,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D3B6B), // avatar bg — brighter than surface
    onPrimaryContainer = DesignTokens.indigo100,
    secondary = DesignTokens.indigo300, // lifted further for low-emphasis surfaces
    onSecondary = Color.White,
    secondaryContainer = DesignTokens.indigo900,
    onSecondaryContainer = DesignTokens.indigo200,
    tertiary = DesignTokens.sienna300,
    onTertiary = DesignTokens.neutral900,
    tertiaryContainer = Color(0xFF3D2510), // warm dark sienna container
    onTertiaryContainer = DesignTokens.sienna300,
    background = DesignTokens.inkDark,
    onBackground = DesignTokens.darkText,
    surface = DesignTokens.darkSurface,
    onSurface = DesignTokens.darkText,
    surfaceVariant = DesignTokens.darkSurface2,
    onSurfaceVariant = DesignTokens.darkTextSecondary,
    error = DesignTokens.errorDarkText,
    onError = DesignTokens.neutral900,
    errorContainer = DesignTokens.errorDarkBg,
    onErrorContainer = DesignTokens.errorDarkText,
    outline = DesignTokens.darkBorder,
    outlineVariant = DesignTokens.darkSurface2,
)

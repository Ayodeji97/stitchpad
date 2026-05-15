package com.danzucker.stitchpad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * True when StitchPadTheme is rendering with the dark color scheme. Use this
 * inside child composables instead of `isSystemInDarkTheme()` so the value
 * also reflects the user's Settings → Appearance choice (System / Light /
 * Dark), not just the OS-level setting.
 */
val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun StitchPadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) stitchPadDarkColorScheme() else stitchPadLightColorScheme()
    val stitchPadColors = if (darkTheme) DarkStitchPadColors else LightStitchPadColors

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalStitchPadColors provides stitchPadColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StitchPadTypography(),
            content = content
        )
    }
}

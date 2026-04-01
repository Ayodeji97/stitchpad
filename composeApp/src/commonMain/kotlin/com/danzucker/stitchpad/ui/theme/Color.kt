package com.danzucker.stitchpad.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = DesignTokens.primary500,
    onPrimary = DesignTokens.neutral800, // Dark text on saffron — NOT white
    primaryContainer = DesignTokens.primary50,
    onPrimaryContainer = DesignTokens.primary900,
    secondary = DesignTokens.primary600,
    onSecondary = Color.White,
    secondaryContainer = DesignTokens.primary100,
    onSecondaryContainer = DesignTokens.primary800,
    background = DesignTokens.neutral50,
    onBackground = DesignTokens.neutral800,
    surface = DesignTokens.neutral0,
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

val DarkColorScheme = darkColorScheme(
    primary = DesignTokens.primary500,
    onPrimary = Color.White, // White text on saffron in dark mode
    primaryContainer = DesignTokens.primary900,
    onPrimaryContainer = DesignTokens.primary100,
    secondary = DesignTokens.primary400,
    onSecondary = DesignTokens.neutral900,
    secondaryContainer = DesignTokens.primary800,
    onSecondaryContainer = DesignTokens.primary200,
    background = DesignTokens.darkBg,
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

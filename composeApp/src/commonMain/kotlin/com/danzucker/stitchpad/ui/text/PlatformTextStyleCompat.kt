package com.danzucker.stitchpad.ui.text

import androidx.compose.ui.text.PlatformTextStyle

/**
 * KMP-safe accessor for `PlatformTextStyle(includeFontPadding = false)`.
 * That constructor only exists on Android — iOS Native fails to link because
 * `includeFontPadding` is not a parameter of any iOS-side overload. iOS returns
 * `null` so `TextStyle.platformStyle` falls back to the platform default
 * (iOS doesn't have the legacy Android font-padding issue).
 */
expect fun platformTextStyleNoFontPadding(): PlatformTextStyle?

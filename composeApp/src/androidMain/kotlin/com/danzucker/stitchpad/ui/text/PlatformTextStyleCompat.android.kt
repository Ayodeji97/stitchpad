package com.danzucker.stitchpad.ui.text

import androidx.compose.ui.text.PlatformTextStyle

actual fun platformTextStyleNoFontPadding(): PlatformTextStyle? =
    PlatformTextStyle(includeFontPadding = false)

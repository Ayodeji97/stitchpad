package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberReduceMotionEnabled(): Boolean =
    remember { UIAccessibilityIsReduceMotionEnabled() }

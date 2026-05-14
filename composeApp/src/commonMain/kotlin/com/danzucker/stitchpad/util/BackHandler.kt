package com.danzucker.stitchpad.util

import androidx.compose.runtime.Composable

/**
 * Multiplatform shim around system back. CMP 1.10 doesn't expose a commonMain
 * BackHandler, so we delegate per-platform: Android uses
 * `androidx.activity.compose.BackHandler`, iOS is a no-op (the navigation
 * stack handles its swipe-back gesture). Web/desktop can plug in later.
 */
@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)

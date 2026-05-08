package com.danzucker.stitchpad.util

import androidx.compose.runtime.Composable

/**
 * iOS handles back navigation via the platform swipe-back gesture and the
 * Compose Navigation back stack. There's no system back-button to intercept,
 * so this is a no-op. Callers that need to swallow back during destructive
 * operations (e.g. mid-delete) should additionally remove user-facing exits
 * (no Cancel button on the Processing dialog, no nav icon, etc.).
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op
}

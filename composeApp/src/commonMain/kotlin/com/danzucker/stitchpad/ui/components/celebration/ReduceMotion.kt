package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.runtime.Composable

/**
 * Whether the OS asks apps to minimise motion (Android: animator duration scale
 * 0 / "Remove animations"; iOS: Reduce Motion). When true the celebration skips
 * confetti and springs and simply fades in.
 */
@Composable
expect fun rememberReduceMotionEnabled(): Boolean

package com.danzucker.stitchpad.core.domain

/**
 * Lightweight platform marker used by anonymous analytics paths
 * (e.g. account-deletion feedback). Filled in per-platform via expect/actual.
 */
expect val currentPlatformName: String

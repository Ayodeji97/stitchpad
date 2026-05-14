package com.danzucker.stitchpad.core.domain

/**
 * Lightweight platform marker used by anonymous analytics paths
 * (e.g. account-deletion feedback). Filled in per-platform via expect/actual.
 */
expect val currentPlatformName: String

/**
 * App marketing version (e.g. "1.2.0"). Sourced from Android BuildConfig
 * (`versionName`) and iOS Info.plist (`CFBundleShortVersionString`) so a
 * single bump in Gradle / Xcode flows through to telemetry and the
 * Settings footer without manual mirroring.
 */
expect val currentAppVersion: String

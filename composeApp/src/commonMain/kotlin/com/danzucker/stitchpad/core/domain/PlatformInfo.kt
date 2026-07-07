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

/**
 * Monotonic build number of the running app (Android `versionCode`, iOS
 * `CFBundleVersion`). Both platforms derive this from the git commit count, so it
 * only ever increases — which is exactly what the remote force-update floor needs
 * to compare against. Null when it can't be determined at runtime; the app-gate
 * treats null as "don't force" so an unreadable build never locks the user out.
 */
expect val currentAppBuildNumber: Int?

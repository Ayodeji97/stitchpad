package com.danzucker.stitchpad.core.domain

import platform.Foundation.NSBundle

actual val currentPlatformName: String = "ios"

/**
 * Reads `CFBundleShortVersionString` from the iOS app bundle. Fallback
 * "0.0.0" only fires if Info.plist is malformed at runtime — the
 * marketing version is mandatory for App Store builds.
 */
actual val currentAppVersion: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: "0.0.0"

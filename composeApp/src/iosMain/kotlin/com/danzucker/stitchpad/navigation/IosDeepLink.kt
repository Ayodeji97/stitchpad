package com.danzucker.stitchpad.navigation

import org.koin.mp.KoinPlatform

/**
 * Entry point for iOS custom-scheme deep links, called from iOSApp.swift's
 * `application(_:open:options:)`. Mirrors the Android MainActivity intent
 * handling: a `stitchpad://upgrade` URL (the renewal-reminder email "Renew"
 * button) sets the pending deep-link target so the NavGraph routes to the
 * Upgrade screen once the app is on Home.
 *
 * Returns true if the URL was a StitchPad deep link we handled, so the caller
 * can stop (and not pass it on to Google Sign-In).
 */
fun handleIosDeepLink(url: String): Boolean {
    if (!url.startsWith("stitchpad://upgrade")) return false
    KoinPlatform.getKoin().get<PendingDeepLinkHolder>().set(DeepLinkTarget.UPGRADE)
    return true
}

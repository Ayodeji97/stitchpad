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
    // Exact host match (mirrors the Android scheme+host check) so a future
    // stitchpad://upgrade-something isn't swallowed as an Upgrade deep link.
    val isUpgrade = url == "stitchpad://upgrade" ||
        url.startsWith("stitchpad://upgrade?") ||
        url.startsWith("stitchpad://upgrade/")
    if (!isUpgrade) return false
    val params = parseQuery(url)
    KoinPlatform.getKoin().get<PendingDeepLinkHolder>().setUpgrade(params["tier"], params["cadence"])
    return true
}

/** Minimal query-string parse (no android.net.Uri on iOS): "a=1&b=2" -> map. */
private fun parseQuery(url: String): Map<String, String> {
    val query = url.substringAfter('?', "")
    if (query.isEmpty()) return emptyMap()
    return query.split("&").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2 && parts[0].isNotEmpty()) parts[0] to parts[1] else null
    }.toMap()
}

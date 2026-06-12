package com.danzucker.stitchpad.navigation

import org.koin.mp.KoinPlatform

/**
 * Entry point for iOS deep links, called from iOSApp.swift for both the custom-scheme open
 * (`.onOpenURL`) and the https Universal Link (`.onContinueUserActivity` browsing-web). The
 * renewal-reminder email "Renew" button uses the https form (`https://link.getstitchpad.com/
 * upgrade`) because Gmail's iOS app refuses custom-scheme links; the `stitchpad://upgrade`
 * form is kept for Apple Mail / manual taps. Both route to the Upgrade screen once Home is
 * reached, pre-selecting the plan carried in the query.
 *
 * Returns true if the URL was a StitchPad deep link we handled, so the caller can stop (and
 * not pass it on to Google Sign-In).
 */
fun handleIosDeepLink(url: String): Boolean {
    val preselect = DeepLinkParser.parseUpgrade(url) ?: return false
    KoinPlatform.getKoin().get<PendingDeepLinkHolder>().setUpgrade(preselect.tier, preselect.cadence)
    return true
}

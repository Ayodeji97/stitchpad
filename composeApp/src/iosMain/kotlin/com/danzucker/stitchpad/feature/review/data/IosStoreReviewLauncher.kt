package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication

private const val APP_ID = "6770673562"

// itms-apps opens the App Store app directly (best on a real device). apps.apple.com is
// the graceful fallback: on a device it still routes to the App Store, and on the Simulator
// — which has no App Store app to handle itms-apps:// — it opens the listing in Safari
// instead of silently doing nothing. Mirrors the Android market:// -> https:// fallback.
private const val WRITE_REVIEW_URL = "itms-apps://itunes.apple.com/app/id$APP_ID?action=write-review"
private const val WRITE_REVIEW_WEB_URL = "https://apps.apple.com/app/id$APP_ID?action=write-review"

class IosStoreReviewLauncher : StoreReviewLauncher {

    override suspend fun requestInAppReview() {
        // Deprecated-but-supported no-scene form keeps the K/N binding simple; StoreKit
        // routes it to the active scene. Adjust to requestReviewInScene(...) if the
        // compiler requires a UIWindowScene on the linked StoreKit headers.
        // Callers may invoke this from a background dispatcher (e.g. ReviewController's
        // Dispatchers.Default scope), so hop to main before touching StoreKit/UIKit —
        // same pattern as WhatsAppLauncher.ios.kt.
        withContext(Dispatchers.Main) {
            SKStoreReviewController.requestReview()
        }
    }

    override fun openStoreListing() {
        val app = UIApplication.sharedApplication
        val openWebFallback = {
            NSURL.URLWithString(WRITE_REVIEW_WEB_URL)?.let {
                app.openURL(it, options = emptyMap<Any?, Any?>(), completionHandler = null)
            }
            Unit
        }
        val itmsUrl = NSURL.URLWithString(WRITE_REVIEW_URL)
        if (itmsUrl == null) {
            openWebFallback()
            return
        }
        // If the App Store app can't handle itms-apps:// (e.g. Simulator), fall back to web.
        app.openURL(itmsUrl, options = emptyMap<Any?, Any?>()) { opened ->
            if (!opened) openWebFallback()
        }
    }
}

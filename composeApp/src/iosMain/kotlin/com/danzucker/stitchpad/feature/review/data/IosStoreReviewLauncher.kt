package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher
import platform.Foundation.NSURL
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication

private const val APP_ID = "6770673562"
private const val WRITE_REVIEW_URL = "itms-apps://itunes.apple.com/app/id$APP_ID?action=write-review"

class IosStoreReviewLauncher : StoreReviewLauncher {

    override suspend fun requestInAppReview() {
        // Deprecated-but-supported no-scene form keeps the K/N binding simple; StoreKit
        // routes it to the active scene. Adjust to requestReviewInScene(...) if the
        // compiler requires a UIWindowScene on the linked StoreKit headers.
        SKStoreReviewController.requestReview()
    }

    override fun openStoreListing() {
        val url = NSURL.URLWithString(WRITE_REVIEW_URL) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}

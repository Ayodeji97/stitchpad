package com.danzucker.stitchpad.feature.review.domain

/**
 * Two store-rating surfaces. [requestInAppReview] shows the OS-native in-app rating UI
 * (auto-prompt path; OS-throttled, silent, fire-and-forget). [openStoreListing] deep-links
 * to the store's write-review page (manual "Rate" button path). Neither throws — platform
 * failures are swallowed and logged.
 */
interface StoreReviewLauncher {
    suspend fun requestInAppReview()
    fun openStoreListing()
}

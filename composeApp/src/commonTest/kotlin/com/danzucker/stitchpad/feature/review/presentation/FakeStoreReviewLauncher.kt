package com.danzucker.stitchpad.feature.review.presentation

import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher

class FakeStoreReviewLauncher : StoreReviewLauncher {
    var inAppRequests = 0
    var listingOpens = 0
    override suspend fun requestInAppReview() { inAppRequests += 1 }
    override fun openStoreListing() { listingOpens += 1 }
}

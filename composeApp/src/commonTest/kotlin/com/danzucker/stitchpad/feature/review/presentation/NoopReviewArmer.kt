package com.danzucker.stitchpad.feature.review.presentation

/** No-op [ReviewArmer] for ViewModel tests that don't exercise the review system. */
class NoopReviewArmer : ReviewArmer {
    override fun armFromDelight() {}
    override fun recordOrderCreated() {}
}

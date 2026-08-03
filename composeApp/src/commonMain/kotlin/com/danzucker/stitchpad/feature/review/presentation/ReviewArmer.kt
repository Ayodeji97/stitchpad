package com.danzucker.stitchpad.feature.review.presentation

/**
 * Narrow seam ViewModels use to feed the review system without seeing the whole
 * controller. Both methods are fire-and-forget (they launch on the controller scope).
 */
interface ReviewArmer {
    /** A delight moment happened (payment recorded / order delivered). Maybe shows the sheet. */
    fun armFromDelight()

    /** An order was created — feeds the engagement gate. */
    fun recordOrderCreated()
}

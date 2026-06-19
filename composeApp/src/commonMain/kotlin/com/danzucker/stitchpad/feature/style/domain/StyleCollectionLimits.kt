package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/**
 * Caps for the Style Collections feature. [foldersEnabled] false = Free (a flat
 * gallery capped at [flatCap]). When enabled, the default "My styles" folder
 * counts as one of [maxFolders].
 */
data class StyleCollectionLimits(
    val foldersEnabled: Boolean,
    val maxFolders: Int,
    val maxImagesPerFolder: Int,
    val flatCap: Int,
) {
    companion object {
        fun forInspiration(tier: SubscriptionTier): StyleCollectionLimits = when (tier) {
            SubscriptionTier.FREE -> StyleCollectionLimits(false, 0, 0, flatCap = 10)
            SubscriptionTier.PRO -> StyleCollectionLimits(true, maxFolders = 10, maxImagesPerFolder = 5, flatCap = 5)
            SubscriptionTier.ATELIER -> StyleCollectionLimits(
                true,
                maxFolders = 20,
                maxImagesPerFolder = 10,
                flatCap = 10
            )
        }

        fun forCustomer(tier: SubscriptionTier): StyleCollectionLimits = when (tier) {
            SubscriptionTier.FREE -> StyleCollectionLimits(false, 0, 0, flatCap = 5)
            SubscriptionTier.PRO -> StyleCollectionLimits(true, maxFolders = 5, maxImagesPerFolder = 3, flatCap = 3)
            SubscriptionTier.ATELIER -> StyleCollectionLimits(true, maxFolders = 5, maxImagesPerFolder = 5, flatCap = 5)
        }
    }
}

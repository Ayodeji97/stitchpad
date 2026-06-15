package com.danzucker.stitchpad.feature.style.presentation.cap

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/** Which resource hit its ceiling. */
enum class StyleCapKind { FOLDERS, STYLES }

/**
 * Snapshot carried in state when a style-collection cap is reached.
 *
 * @param kind         whether the folders or styles cap was hit.
 * @param currentTier  the tier the user is currently on — used to determine
 *                     what to upgrade to, or whether an upgrade is possible at all.
 */
data class StyleCapInfo(
    val kind: StyleCapKind,
    val currentTier: SubscriptionTier,
)

/**
 * Returns the next subscription tier above [tier], or null if the user is
 * already on the top plan (ATELIER) and cannot upgrade further.
 */
fun nextTier(tier: SubscriptionTier): SubscriptionTier? = when (tier) {
    SubscriptionTier.FREE -> SubscriptionTier.PRO
    SubscriptionTier.PRO -> SubscriptionTier.ATELIER
    SubscriptionTier.ATELIER -> null
}

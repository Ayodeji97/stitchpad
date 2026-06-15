package com.danzucker.stitchpad.feature.style.presentation.cap

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits

/** Which resource hit its ceiling. */
enum class StyleCapKind { FOLDERS, STYLES }

/**
 * Snapshot carried in state when a style-collection cap is reached.
 *
 * @param kind         whether the folders or styles cap was hit.
 * @param currentTier  the tier the user is currently on — used for copy.
 * @param upgradeTier  the tier to offer that ACTUALLY raises the limit that was hit,
 *                     or null when no higher plan increases it (e.g. customer folders
 *                     stay at 5 for both Pro and Atelier, or the user is already on the
 *                     top plan). Null → the sheet shows a "maximum reached" message
 *                     with no upgrade CTA instead of a dead-end upsell.
 */
data class StyleCapInfo(
    val kind: StyleCapKind,
    val currentTier: SubscriptionTier,
    val upgradeTier: SubscriptionTier?,
)

/**
 * Builds a [StyleCapInfo], resolving the upgrade tier that genuinely raises the hit
 * limit. [isInspiration] selects the Inspiration vs customer-closet limit table.
 */
fun styleCapInfo(
    kind: StyleCapKind,
    currentTier: SubscriptionTier,
    isInspiration: Boolean,
): StyleCapInfo = StyleCapInfo(
    kind = kind,
    currentTier = currentTier,
    upgradeTier = upgradeTierThatRaises(kind, currentTier, isInspiration),
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

/**
 * The tier to offer for a given cap, or null if no higher plan raises that specific
 * limit. Styles always benefit (a higher tier adds folders and/or more images per
 * folder), but FOLDERS only benefit when the next tier's maxFolders is actually
 * larger — e.g. customer closets cap at 5 folders on BOTH Pro and Atelier, so a Pro
 * user at the customer folder cap gets no upgrade path.
 */
private fun upgradeTierThatRaises(
    kind: StyleCapKind,
    currentTier: SubscriptionTier,
    isInspiration: Boolean,
): SubscriptionTier? {
    val next = nextTier(currentTier) ?: return null
    return when (kind) {
        StyleCapKind.STYLES -> next
        StyleCapKind.FOLDERS ->
            if (limitsFor(isInspiration, next).maxFolders > limitsFor(isInspiration, currentTier).maxFolders) {
                next
            } else {
                null
            }
    }
}

private fun limitsFor(isInspiration: Boolean, tier: SubscriptionTier): StyleCollectionLimits =
    if (isInspiration) StyleCollectionLimits.forInspiration(tier) else StyleCollectionLimits.forCustomer(tier)

package com.danzucker.stitchpad.core.smartinfra.domain.quota

/**
 * Identifies which Smart Suggestions feature consumed a free-tier quota
 * slot. The [wireName] is the lowercase string written to Firestore at
 * `users/{uid}/usage/smart_drafts.perFeature[wireName]` and recognized by
 * the server's `SmartFeatureKey` TypeScript type.
 *
 * Keep wire names in sync with `functions/src/smart/types.ts`.
 */
enum class SmartFeatureKey(val wireName: String) {
    Draft("draft"),
    PostCaption("postcaption"),
    ReferralMessage("referral_msg"),
    ReferralBio("referral_bio"),
    ContentPlanRegen("contentplan_regen"),
}

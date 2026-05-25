package com.danzucker.stitchpad.core.domain.model

/**
 * Subscription tier the user is currently on. Source of truth is the
 * `users/{uid}.subscriptionTier` Firestore field. Values must stay in
 * sync with the server-side `Tier` union in `functions/src/smart/types.ts`.
 */
enum class SubscriptionTier(val wireValue: String) {
    FREE("free"),
    PRO("pro"),
    ATELIER("atelier");

    companion object {
        /**
         * Parse a wire value into a tier, defaulting to FREE for any
         * unknown / missing input (defensive — never throws). Also
         * accepts the legacy `"premium"` value during the V1.0 migration
         * window and maps it to PRO (closer in feature set than ATELIER).
         */
        fun fromWire(value: String?): SubscriptionTier = when (value?.lowercase()) {
            "pro" -> PRO
            "atelier" -> ATELIER
            "premium" -> PRO // legacy
            else -> FREE
        }
    }
}

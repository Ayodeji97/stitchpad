package com.danzucker.stitchpad.core.config.domain.model

/**
 * Remote, console-controllable app configuration read from the `config/app`
 * Firestore document. Intentionally generic — this is the seed of the app's
 * feature-flag layer; the community fields are simply its first occupants.
 */
data class AppConfig(
    val communityEnabled: Boolean,
    val communityInviteUrl: String?,
    /**
     * Server-controlled kill switch for Android (Paystack) paid upgrades. Default
     * false so the Upgrade CTA stays inert until Paystack billing is live — flip
     * `config/app.billingEnabled = true` in lockstep with the `sk_live_` keys to
     * turn Android checkout on with no app release. iOS (Apple IAP) ignores this:
     * its availability is gated by App Store approval, not this flag.
     */
    val billingEnabled: Boolean = false,
) {
    companion object {
        /** Safe fallback used before config loads or on read failure: feature hidden. */
        val Disabled = AppConfig(communityEnabled = false, communityInviteUrl = null)
    }
}

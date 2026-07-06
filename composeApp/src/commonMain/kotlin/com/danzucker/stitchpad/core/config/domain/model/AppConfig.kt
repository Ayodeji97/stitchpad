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
    /**
     * Break-glass force-update floor: the lowest build number (versionCode /
     * CFBundleVersion) the app will run without prompting the user to update.
     * Per-platform because the two stores review on different timelines. Null =
     * no floor (default) so an unset or unreadable config never forces an update.
     * See [minSupportedBuildIos] and the app-gate that consumes these.
     */
    val minSupportedBuildAndroid: Int? = null,
    val minSupportedBuildIos: Int? = null,
    /**
     * Store URL the "Update" button opens, per platform. Remote so we never hardcode
     * the App Store numeric id (it lives in web/functions config, not the app) and can
     * repoint it without a release. Null = hide the button, still show the message.
     */
    val updateUrlAndroid: String? = null,
    val updateUrlIos: String? = null,
    /** Remote copy for the blocking update screen (editable without an app release). */
    val forceUpdateMessage: String? = null,
    /**
     * Global soft-lock. Default false — flip to true in `config/app` to show a
     * "back soon" screen during an incident without shipping a binary. Fail-open:
     * a missing/unreadable config leaves this false and the app usable.
     */
    val maintenanceMode: Boolean = false,
    /** Remote copy for the maintenance screen. */
    val maintenanceMessage: String? = null,
) {
    companion object {
        /** Safe fallback used before config loads or on read failure: feature hidden. */
        val Disabled = AppConfig(communityEnabled = false, communityInviteUrl = null)
    }
}

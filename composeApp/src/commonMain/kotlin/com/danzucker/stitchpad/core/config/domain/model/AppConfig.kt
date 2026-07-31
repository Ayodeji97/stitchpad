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
    /** Remote toggle for the restructured Settings hub (drill-down categories).
     * Default false — fail-open to the legacy flat layout on a missing/unreadable
     * config, matching [communityEnabled]. Flip `config/app.settingsHubEnabled`
     * to roll out; flip back to revert with no app release. */
    val settingsHubEnabled: Boolean = false,
    /**
     * Remote kill-switch for the Owner + Staff experience. Default **true**
     * (fail-open) — unlike the other flags, a missing/unreadable config must NOT
     * disable staff. Flip `config/app.staffFeatureEnabled = false` to instantly
     * disable the staff experience with no app release: [WorkshopSessionResolver]
     * then resolves every signed-in user to owner-of-self, so a staff member falls
     * back to their own (empty) tree — no staff nav, no owner data, no crash — the
     * break-glass for a staff release that misbehaves once real data lights up.
     */
    val staffFeatureEnabled: Boolean = true,
) {
    companion object {
        /** Safe fallback used before config loads or on read failure: feature hidden.
         * [staffFeatureEnabled] intentionally stays at its `true` default here — a
         * config read failure must not disable staff (fail-open). */
        val Disabled = AppConfig(communityEnabled = false, communityInviteUrl = null)
    }
}

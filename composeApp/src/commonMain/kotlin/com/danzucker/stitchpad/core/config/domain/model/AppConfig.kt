package com.danzucker.stitchpad.core.config.domain.model

/**
 * Remote, console-controllable app configuration read from the `config/app`
 * Firestore document. Intentionally generic — this is the seed of the app's
 * feature-flag layer; the community fields are simply its first occupants.
 */
data class AppConfig(
    val communityEnabled: Boolean,
    val communityInviteUrl: String?,
) {
    companion object {
        /** Safe fallback used before config loads or on read failure: feature hidden. */
        val Disabled = AppConfig(communityEnabled = false, communityInviteUrl = null)
    }
}

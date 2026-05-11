package com.danzucker.stitchpad.feature.auth.domain

/**
 * Apple Sign-In credential payload returned by the iOS-side credential provider.
 *
 * fullName is populated only on the very first Sign-In with Apple ever (per
 * Apple's privacy model — once you've signed in once, it's never returned again).
 * The repository layer uses it to seed Firebase Auth's displayName.
 */
data class AppleCredential(
    val idToken: String,
    val rawNonce: String,
    val fullName: String? = null,
)

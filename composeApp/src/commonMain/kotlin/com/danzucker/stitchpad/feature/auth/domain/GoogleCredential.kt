package com.danzucker.stitchpad.feature.auth.domain

/**
 * Google Sign-In credential payload returned by the platform credential provider.
 *
 * accessToken is non-null on iOS (GoogleSignIn-iOS gives both tokens) and null on
 * Android (Credential Manager only exposes the ID token). gitlive's Firebase iOS
 * wrapper requires both tokens non-null on the Obj-C side; the Android wrapper
 * accepts null. Repository passes whatever the provider returns.
 */
data class GoogleCredential(
    val idToken: String,
    val accessToken: String? = null,
)

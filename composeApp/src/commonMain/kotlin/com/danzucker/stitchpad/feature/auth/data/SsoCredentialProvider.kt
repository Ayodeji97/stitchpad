package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

/**
 * Platform credential acquisition for SSO sign-in.
 *
 * Implemented per platform:
 * - Android: Credential Manager + GoogleIdOption. Apple returns SsoError.NO_PROVIDER.
 * - iOS: GoogleSignIn-iOS SDK + AuthenticationServices (ASAuthorizationController).
 *
 * Implementations must NOT perform any Firebase calls — they return raw credentials
 * that FirebaseAuthRepository exchanges for a User.
 */
interface SsoCredentialProvider {
    suspend fun getGoogleIdToken(): Result<String, SsoError>
    suspend fun getAppleCredential(): Result<AppleCredential, SsoError>
}

package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

/**
 * Platform glue implemented in Swift (see iosApp/iosApp/AppleSignInLauncherIos.swift)
 * and registered into Koin from iosApp's AppDelegate at startup.
 *
 * Apple Sign-In on iOS uses ASAuthorizationController, which is Swift-only.
 * The Swift impl generates a cryptographic nonce, hashes it (SHA-256) for the
 * Apple request, then returns the raw nonce alongside the identity token so
 * FirebaseAuthRepository can pass them to Firebase's OAuthProvider.credential.
 *
 * fullName is populated only on the very first Sign-In with Apple ever (per
 * Apple's privacy model). FirebaseAuthRepository uses it to seed displayName.
 */
interface NativeAppleSignInLauncher {
    suspend fun launchAppleSignIn(): Result<AppleCredential, SsoError>
}

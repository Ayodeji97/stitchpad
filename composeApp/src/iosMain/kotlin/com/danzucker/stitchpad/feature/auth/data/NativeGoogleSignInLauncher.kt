package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.GoogleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

/**
 * Platform glue implemented in Swift (see iosApp/iosApp/GoogleSignInLauncherIos.swift)
 * and registered into Koin from iosApp's AppDelegate at startup.
 *
 * We can't call GIDSignIn directly from Kotlin/Native because GoogleSignIn-iOS 8.x
 * ships as a Swift-only module with no Obj-C header. So Swift implements this
 * interface and Kotlin calls it via the standard Kotlin-Swift protocol bridge.
 *
 * Returns BOTH idToken and accessToken — gitlive's Firebase iOS wrapper calls
 * FIRGoogleAuthProvider credentialWithIDToken:accessToken: which is `nonnull` for
 * both arguments on the Obj-C side, so we must supply both. GoogleSignIn returns
 * them on GIDGoogleUser as `idToken.tokenString` and `accessToken.tokenString`.
 */
interface NativeGoogleSignInLauncher {
    suspend fun launchGoogleSignIn(): Result<GoogleCredential, SsoError>
}

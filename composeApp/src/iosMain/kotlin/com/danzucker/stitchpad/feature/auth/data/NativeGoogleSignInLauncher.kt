package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.SsoError

/**
 * Platform glue implemented in Swift (see iosApp/iosApp/GoogleSignInLauncherIos.swift)
 * and registered into Koin from iosApp's AppDelegate at startup.
 *
 * We can't call GIDSignIn directly from Kotlin/Native because GoogleSignIn-iOS 8.x
 * ships as a Swift-only module with no Obj-C header. So Swift implements this
 * interface and Kotlin calls it via the standard Kotlin-Swift protocol bridge.
 *
 * The returned idToken is exchanged with Firebase by FirebaseAuthRepository.
 */
interface NativeGoogleSignInLauncher {
    suspend fun launchGoogleSignIn(): Result<String, SsoError>
}

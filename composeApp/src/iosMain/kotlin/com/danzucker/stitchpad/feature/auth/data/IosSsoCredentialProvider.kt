package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

class IosSsoCredentialProvider(
    private val googleLauncher: NativeGoogleSignInLauncher,
    private val appleLauncher: NativeAppleSignInLauncher,
) : SsoCredentialProvider {

    override suspend fun getGoogleIdToken(): Result<String, SsoError> =
        googleLauncher.launchGoogleSignIn()

    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> =
        appleLauncher.launchAppleSignIn()
}

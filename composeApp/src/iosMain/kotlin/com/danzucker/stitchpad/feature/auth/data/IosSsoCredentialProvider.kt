package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError

class IosSsoCredentialProvider(
    private val googleLauncher: NativeGoogleSignInLauncher,
) : SsoCredentialProvider {

    override suspend fun getGoogleIdToken(): Result<String, SsoError> =
        googleLauncher.launchGoogleSignIn()

    // Apple is wired in Task 17 — return UNKNOWN for now so the UI shows
    // a useful error rather than hanging.
    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> =
        Result.Error(SsoError.UNKNOWN)
}

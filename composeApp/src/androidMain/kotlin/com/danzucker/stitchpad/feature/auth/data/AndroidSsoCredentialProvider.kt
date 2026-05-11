package com.danzucker.stitchpad.feature.auth.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.GoogleCredential
import com.danzucker.stitchpad.feature.auth.domain.SsoError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

private const val TAG = "SsoProvider"

class AndroidSsoCredentialProvider(
    private val context: Context,
    private val activityHolder: CurrentActivityHolder,
    private val webClientId: String,
) : SsoCredentialProvider {

    override suspend fun getGoogleCredential(): Result<GoogleCredential, SsoError> {
        val activity = activityHolder.activity
            ?: return Result.Error(SsoError.UNKNOWN)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()
        return try {
            val response = CredentialManager.create(context).getCredential(activity, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            // Credential Manager only exposes the ID token; gitlive Android tolerates a
            // null accessToken because Firebase Android resolves it server-side.
            Result.Success(GoogleCredential(idToken = credential.idToken, accessToken = null))
        } catch (@Suppress("SwallowedException") e: GetCredentialCancellationException) {
            Result.Error(SsoError.CANCELLED)
        } catch (@Suppress("SwallowedException") e: NoCredentialException) {
            AppLogger.e(tag = TAG, throwable = e) { "Google sign-in: no credential on device" }
            Result.Error(SsoError.NO_PROVIDER)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "Google sign-in failed" }
            Result.Error(SsoError.UNKNOWN)
        }
    }

    override suspend fun getAppleCredential(): Result<AppleCredential, SsoError> =
        Result.Error(SsoError.NO_PROVIDER)
}

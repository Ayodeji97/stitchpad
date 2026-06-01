package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "VerificationEmail"

class GitLiveVerificationEmailSender(
    private val functions: FirebaseFunctions,
) : VerificationEmailSender {

    override suspend fun send(): EmptyResult<AuthError> {
        return try {
            functions.httpsCallable("sendVerificationEmail").invoke()
            Result.Success(Unit)
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) {
                "sendVerificationEmail failed: code=${e.code} message=${e.message}"
            }
            Result.Error(mapFunctionsException(e))
        } catch (e: CancellationException) {
            // Don't swallow structured-concurrency cancellation (e.g. the VM
            // was cleared mid-call) into a Result.Error — let it propagate.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // Non-Functions throwables are almost always transport-layer
            // failures (no network, DNS, TLS) — surface as a network error.
            AppLogger.e(tag = TAG, throwable = e) {
                "sendVerificationEmail threw ${e::class.simpleName}: ${e.message}"
            }
            Result.Error(AuthError.NETWORK_ERROR)
        }
    }

    private fun mapFunctionsException(e: FirebaseFunctionsException): AuthError =
        when (e.code) {
            FunctionsExceptionCode.UNAVAILABLE -> AuthError.NETWORK_ERROR
            FunctionsExceptionCode.RESOURCE_EXHAUSTED -> AuthError.TOO_MANY_REQUESTS
            FunctionsExceptionCode.UNAUTHENTICATED -> AuthError.USER_NOT_FOUND
            else -> AuthError.UNKNOWN
        }
}

package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.serialization.Serializable

private const val TAG = "ReferralRepo"

internal class CloudFunctionsReferralRepository(
    private val functions: FirebaseFunctions,
) : ReferralRepository {

    override suspend fun recordAttribution(
        code: String,
        deviceHash: String,
        source: ReferralSource,
    ): Result<AttributionOutcome, ReferralError> {
        return try {
            val response = functions
                .httpsCallable("recordReferralAttribution")
                .invoke(
                    RecordAttributionRequestDto(
                        code = code,
                        deviceHash = deviceHash,
                        source = source.wire,
                    )
                )
                .data<RecordAttributionResponseDto>()
            Result.Success(
                AttributionOutcome(
                    alreadyAttributed = response.status == STATUS_ALREADY_ATTRIBUTED,
                    marketerId = response.marketerId,
                )
            )
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) {
                "recordReferralAttribution failed: code=${e.code} message=${e.message}"
            }
            Result.Error(mapError(e))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "recordReferralAttribution threw ${e::class.simpleName}: ${e.message}"
            }
            // GitLive on iOS can drop the canonical HttpsError code, so recover the
            // intent from the server message marker before defaulting to NETWORK.
            Result.Error(recoverError(e.message, fallback = ReferralError.NETWORK))
        }
    }

    private fun mapError(e: FirebaseFunctionsException): ReferralError =
        when (e.code) {
            FunctionsExceptionCode.UNAUTHENTICATED -> ReferralError.UNAUTHENTICATED
            // missing_code / referral_code_not_found both arrive as INVALID_ARGUMENT.
            FunctionsExceptionCode.INVALID_ARGUMENT ->
                recoverError(e.message, fallback = ReferralError.CODE_NOT_FOUND)
            else -> recoverError(e.message, fallback = ReferralError.UNKNOWN)
        }
}

// Server message marker from functions/src/referral/referralConstants.ts
// (ERR_REFERRAL_CODE_NOT_FOUND). Kept in sync there — how the iOS GitLive wrapper
// recovers the server error's intent when the canonical code is lost.
private const val MARKER_CODE_NOT_FOUND = "referral_code_not_found"
private const val STATUS_ALREADY_ATTRIBUTED = "already_attributed"

internal fun recoverError(message: String?, fallback: ReferralError): ReferralError = when {
    message == null -> fallback
    message.contains(MARKER_CODE_NOT_FOUND) -> ReferralError.CODE_NOT_FOUND
    else -> fallback
}

@Serializable
private data class RecordAttributionRequestDto(
    val code: String,
    val deviceHash: String,
    val source: String,
)

@Serializable
private data class RecordAttributionResponseDto(
    val status: String,
    val marketerId: String,
)

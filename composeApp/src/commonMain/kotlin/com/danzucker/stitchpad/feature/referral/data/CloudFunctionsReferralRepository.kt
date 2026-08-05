package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralLink
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

    override suspend fun getOrCreateMyReferralLink(): Result<ReferralLink, DataError.Network> =
        try {
            val data = functions
                .httpsCallable("getOrCreateMyReferralLink")
                .invoke()
                .data<MyReferralLinkDto>()
            Result.Success(ReferralLink(code = data.code, url = data.url, playUrl = data.playUrl))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "getOrCreateMyReferralLink threw ${e::class.simpleName}: ${e.message}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }

    override suspend fun getFoundingTailorsStanding(
        code: String,
    ): Result<FoundingTailorsStanding, DataError.Network> =
        try {
            val data = functions
                .httpsCallable("getFoundingTailorsLeaderboard")
                .invoke(FoundingTailorsStandingRequestDto(code = code))
                .data<FoundingTailorsLeaderboardDto>()
            Result.Success(
                FoundingTailorsStanding(
                    monthPoints = data.you?.points ?: 0,
                    monthRank = data.you?.rank ?: 0,
                    allTimePoints = data.youAllTime?.points ?: 0,
                    allTimeRank = data.youAllTime?.rank ?: 0,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "getFoundingTailorsStanding threw ${e::class.simpleName}: ${e.message}"
            }
            Result.Error(DataError.Network.UNKNOWN)
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

@Serializable
private data class MyReferralLinkDto(
    val code: String = "",
    val url: String = "",
    val playUrl: String = "",
)

@Serializable
private data class FoundingTailorsStandingRequestDto(val code: String)

// Full response shape of getFoundingTailorsLeaderboard — EVERY field is declared so
// GitLive's kotlinx deserialization never trips on an unknown key (matches the other
// referral response DTOs). Only you/youAllTime are read.
@Serializable
private data class FoundingTailorsLeaderboardDto(
    val updatedAt: Long = 0,
    val monthId: String = "",
    val top: List<StandingRowDto> = emptyList(),
    val you: StandingEntryDto? = null,
    val youAllTime: StandingEntryDto? = null,
)

@Serializable
private data class StandingRowDto(val rank: Int = 0, val name: String = "", val points: Int = 0)

@Serializable
private data class StandingEntryDto(val rank: Int = 0, val points: Int = 0)

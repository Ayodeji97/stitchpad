package com.danzucker.stitchpad.feature.gift.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.gift.domain.GiftError
import com.danzucker.stitchpad.feature.gift.domain.GiftLink
import com.danzucker.stitchpad.feature.gift.domain.GiftRepository
import com.danzucker.stitchpad.feature.gift.domain.RedeemedGift
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.serialization.Serializable

private const val TAG = "GiftRepo"

internal class CloudFunctionsGiftRepository(
    private val functions: FirebaseFunctions,
) : GiftRepository {

    override suspend fun redeemGift(code: String): Result<RedeemedGift, GiftError> {
        return try {
            val response = functions
                .httpsCallable("redeemGift")
                .invoke(RedeemGiftRequestDto(code = code))
                .data<RedeemGiftResponseDto>()
            Result.Success(
                RedeemedGift(
                    tier = SubscriptionTier.fromWire(response.tier),
                    cadence = BillingCadence.fromWire(response.cadence),
                )
            )
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) {
                "redeemGift failed: code=${e.code} message=${e.message}"
            }
            Result.Error(mapRedeemError(e))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "redeemGift threw ${e::class.simpleName}: ${e.message}"
            }
            // GitLive on iOS can drop the canonical HttpsError code, so recover the
            // intent from the server message marker before defaulting to NETWORK.
            Result.Error(recoverGiftError(e.message, fallback = GiftError.NETWORK))
        }
    }

    override suspend fun getOrCreateGiftLink(): Result<GiftLink, GiftError> {
        return try {
            val response = functions
                .httpsCallable("createGiftLink")
                .invoke()
                .data<CreateGiftLinkResponseDto>()
            Result.Success(GiftLink(token = response.token, url = response.url))
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createGiftLink failed: code=${e.code} message=${e.message}"
            }
            Result.Error(mapLinkError(e))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createGiftLink threw ${e::class.simpleName}: ${e.message}"
            }
            Result.Error(recoverGiftError(e.message, fallback = GiftError.NETWORK))
        }
    }

    private fun mapRedeemError(e: FirebaseFunctionsException): GiftError =
        when (e.code) {
            FunctionsExceptionCode.UNAUTHENTICATED -> GiftError.UNAUTHENTICATED
            FunctionsExceptionCode.NOT_FOUND -> GiftError.NOT_FOUND
            // invalid_code arrives as INVALID_ARGUMENT — surface as not-found to the user.
            FunctionsExceptionCode.INVALID_ARGUMENT -> GiftError.NOT_FOUND
            FunctionsExceptionCode.FAILED_PRECONDITION ->
                recoverGiftError(e.message, fallback = GiftError.UNKNOWN)
            else -> recoverGiftError(e.message, fallback = GiftError.UNKNOWN)
        }

    private fun mapLinkError(e: FirebaseFunctionsException): GiftError =
        when (e.code) {
            FunctionsExceptionCode.UNAUTHENTICATED -> GiftError.UNAUTHENTICATED
            else -> recoverGiftError(e.message, fallback = GiftError.UNKNOWN)
        }
}

// Server message markers from functions/src/billing/giftBilling.ts. Kept in sync
// there — they are how the iOS GitLive wrapper recovers the server error's intent
// when the canonical FunctionsExceptionCode is lost.
private const val MARKER_NOT_FOUND = "gift_not_found"
private const val MARKER_ALREADY_CLAIMED = "gift_already_claimed"
private const val MARKER_EXPIRED = "gift_expired"
private const val MARKER_NOT_PAYABLE = "gift_not_payable"

internal fun recoverGiftError(message: String?, fallback: GiftError): GiftError = when {
    message == null -> fallback
    message.contains(MARKER_ALREADY_CLAIMED) -> GiftError.ALREADY_CLAIMED
    message.contains(MARKER_EXPIRED) -> GiftError.EXPIRED
    message.contains(MARKER_NOT_PAYABLE) -> GiftError.NOT_PAYABLE
    message.contains(MARKER_NOT_FOUND) -> GiftError.NOT_FOUND
    else -> fallback
}

@Serializable
private data class RedeemGiftRequestDto(val code: String)

@Serializable
private data class RedeemGiftResponseDto(val tier: String, val cadence: String)

@Serializable
private data class CreateGiftLinkResponseDto(val token: String, val url: String)

package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore

private const val TAG = "FreemiumDebugActions"
private const val USERS = "users"
private const val USAGE = "usage"
private const val SMART_DRAFTS = "smart_drafts"
private const val EXPIRED_WINDOW_OFFSET_MS = 35L * 24 * 60 * 60 * 1000

/**
 * Debug-only knobs for the V1.0 freemium model. Writes directly to the
 * caller's own `users/{uid}` document so testers can flip tier / welcome
 * window / coin balance without Firebase Console access.
 *
 * NOTE: Firestore security rules only allow writing your own user doc, so
 * everything here implicitly scopes to the signed-in user.
 */
interface FreemiumDebugActions {
    suspend fun setTier(tier: SubscriptionTier): DebugActionResult
    suspend fun expireWelcomeWindow(nowMs: Long): DebugActionResult
    suspend fun resetWelcomeWindow(): DebugActionResult
    suspend fun setBonusCoins(coins: Int): DebugActionResult
    suspend fun resetSmartUsage(): DebugActionResult
    suspend fun reconcileSlots(): DebugActionResult

    companion object {
        const val WELCOME_BONUS_COINS: Int = 30
    }
}

class DefaultFreemiumDebugActions(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val freemiumRepository: FreemiumRepository,
) : FreemiumDebugActions {

    override suspend fun setTier(tier: SubscriptionTier): DebugActionResult =
        userDocUpdate("setTier=${tier.wireValue}") { doc ->
            doc.set(
                mapOf(
                    "subscriptionTier" to tier.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }

    /** Backdate the welcome window past its expiry → FREE 15-customer cap. */
    override suspend fun expireWelcomeWindow(nowMs: Long): DebugActionResult {
        val backdatedMs = nowMs - EXPIRED_WINDOW_OFFSET_MS
        return userDocUpdate("expireWelcomeWindow") { doc ->
            doc.set(
                mapOf(
                    "welcomeBonusAppliedAt" to backdatedMs,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }
    }

    /** Reset welcome window to now → 30-customer cap for one calendar month. */
    override suspend fun resetWelcomeWindow(): DebugActionResult =
        userDocUpdate("resetWelcomeWindow") { doc ->
            doc.set(
                mapOf(
                    "welcomeBonusAppliedAt" to FieldValue.serverTimestamp,
                    "bonusCoins" to FreemiumDebugActions.WELCOME_BONUS_COINS,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }

    override suspend fun setBonusCoins(coins: Int): DebugActionResult =
        userDocUpdate("setBonusCoins=$coins") { doc ->
            doc.set(
                mapOf(
                    "bonusCoins" to coins,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }

    /**
     * Wipe `users/{uid}/usage/smart_drafts` so the next Smart call starts
     * from a fresh monthly window + full bonus balance.
     */
    override suspend fun resetSmartUsage(): DebugActionResult {
        val uid = authRepository.getCurrentUser()?.id
            ?: return DebugActionResult.Failure("Not signed in")
        return try {
            firestore.collection(USERS).document(uid)
                .collection(USAGE).document(SMART_DRAFTS)
                .delete()
            DebugActionResult.Success
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "resetSmartUsage failed" }
            DebugActionResult.Failure(e.message ?: "Unknown error")
        }
    }

    override suspend fun reconcileSlots(): DebugActionResult =
        when (val r = freemiumRepository.reconcileSlots()) {
            is Result.Success -> DebugActionResult.Success
            is Result.Error -> DebugActionResult.Failure(r.error.toString())
        }

    private suspend inline fun userDocUpdate(
        label: String,
        crossinline block: suspend (
            doc: dev.gitlive.firebase.firestore.DocumentReference,
        ) -> Unit,
    ): DebugActionResult {
        val uid = authRepository.getCurrentUser()?.id
            ?: return DebugActionResult.Failure("Not signed in")
        return try {
            block(firestore.collection(USERS).document(uid))
            DebugActionResult.Success
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "$label failed" }
            DebugActionResult.Failure(e.message ?: "Unknown error")
        }
    }
}

sealed interface DebugActionResult {
    data object Success : DebugActionResult
    data class Failure(val reason: String) : DebugActionResult
}

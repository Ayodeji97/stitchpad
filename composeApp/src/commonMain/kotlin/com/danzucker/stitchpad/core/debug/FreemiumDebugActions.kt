package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val TAG = "FreemiumDebugActions"
private const val USERS = "users"
private const val USAGE = "usage"
private const val SMART_DRAFTS = "smart_drafts"
private const val MS_PER_DAY = 24L * 60 * 60 * 1000

// 5 days of headroom past the rolling welcome window so backdating
// definitely lands outside it even after a partial-day rounding error.
private val EXPIRED_WINDOW_OFFSET_MS =
    (EntitlementsCalculator.WELCOME_WINDOW_DAYS + 5L) * MS_PER_DAY

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
    suspend fun setWelcomeDaysLeft(daysLeft: Int, nowMs: Long): DebugActionResult
    suspend fun setBonusCoins(coins: Int): DebugActionResult
    suspend fun resetSmartUsage(): DebugActionResult
    suspend fun setSmartUsage(
        monthlyCount: Int,
        bonusBalance: Int,
        nowMs: Long,
    ): DebugActionResult
    suspend fun reconcileSlots(): DebugActionResult
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
        // Must write a Timestamp (not a raw Long) — UserDocEntitlementsProvider
        // deserializes welcomeBonusAppliedAt via a typed @Serializable DTO whose
        // field type is Timestamp?. Writing a Long here would crash the iOS
        // listener with SerializationException on the next snapshot emit.
        val backdatedTimestamp = Timestamp(
            seconds = backdatedMs / 1000,
            nanoseconds = ((backdatedMs % 1000) * 1_000_000).toInt(),
        )
        return userDocUpdate("expireWelcomeWindow") { doc ->
            doc.set(
                mapOf(
                    "welcomeBonusAppliedAt" to backdatedTimestamp,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }
    }

    /** Reset welcome window to now → First Month customer cap (200) for 30 rolling days. */
    override suspend fun resetWelcomeWindow(): DebugActionResult =
        userDocUpdate("resetWelcomeWindow") { doc ->
            doc.set(
                mapOf(
                    "welcomeBonusAppliedAt" to FieldValue.serverTimestamp,
                    "bonusCoins" to FirebaseUserRepository.WELCOME_BONUS_COIN_COUNT,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }

    /**
     * Set the welcome window to land at exactly [daysLeft] days remaining.
     * Useful for testing the 3-day-warning banner without waiting in real time:
     * pass 2 to land in the warning state immediately, 30 to start fresh,
     * 1 to test the singular-grammar "First month · 1 day left" pill.
     *
     * Math: welcomeBonusAppliedAt = now - (30 - daysLeft) days, so the rolling
     * 30-day window ends in [daysLeft] more days. Clamps to 0..30.
     */
    override suspend fun setWelcomeDaysLeft(daysLeft: Int, nowMs: Long): DebugActionResult {
        val clamped = daysLeft.coerceIn(0, EntitlementsCalculator.WELCOME_WINDOW_DAYS)
        val daysIntoWindow = EntitlementsCalculator.WELCOME_WINDOW_DAYS - clamped
        val backdatedMs = nowMs - daysIntoWindow.toLong() * MS_PER_DAY
        val backdatedTimestamp = Timestamp(
            seconds = backdatedMs / 1000,
            nanoseconds = ((backdatedMs % 1000) * 1_000_000).toInt(),
        )
        return userDocUpdate("setWelcomeDaysLeft=$clamped") { doc ->
            doc.set(
                mapOf(
                    "welcomeBonusAppliedAt" to backdatedTimestamp,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
        }
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

    /**
     * Force the Smart usage doc to a chosen state. Bypasses real AI calls so
     * testers can land at "5/5 free drafts used → next call fires upgrade
     * sheet" or "bonus drained, free unused" without generating dozens of
     * drafts. monthYear is pinned to the current Africa/Lagos month so the
     * server doesn't reset the counter on the next call.
     *
     * NOTE: writes `bonusLiftedAt` to the current epoch ms so the server
     * does not re-lift the 30-coin welcome bonus on top of the values we
     * just wrote (server lifts exactly once, gated by this field).
     */
    override suspend fun setSmartUsage(
        monthlyCount: Int,
        bonusBalance: Int,
        nowMs: Long,
    ): DebugActionResult {
        val uid = authRepository.getCurrentUser()?.id
            ?: return DebugActionResult.Failure("Not signed in")
        val monthYear = currentMonthYear(nowMs)
        return try {
            firestore.collection(USERS).document(uid)
                .collection(USAGE).document(SMART_DRAFTS)
                .set(
                    mapOf(
                        "monthYear" to monthYear,
                        "count" to monthlyCount,
                        "bonusBalance" to bonusBalance,
                        "bonusLiftedAt" to nowMs,
                    ),
                    merge = true,
                )
            DebugActionResult.Success
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "setSmartUsage failed" }
            DebugActionResult.Failure(e.message ?: "Unknown error")
        }
    }

    private fun currentMonthYear(nowMs: Long): String {
        val local = Instant.fromEpochMilliseconds(nowMs)
            .toLocalDateTime(TimeZone.of("Africa/Lagos"))
        val mm = local.monthNumber.toString().padStart(2, '0')
        return "${local.year}-$mm"
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

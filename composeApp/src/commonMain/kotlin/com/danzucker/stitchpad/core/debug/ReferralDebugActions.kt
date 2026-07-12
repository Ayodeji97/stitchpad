package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource

private const val TAG = "ReferralDebugActions"
private const val DAY_MS = 24L * 60 * 60 * 1000

// Mirrors QUALIFY_DISTINCT_DAYS on the server grader (reconcileReferrals): the
// number of distinct Africa/Lagos days of activity a referral needs to qualify.
private const val QUALIFY_DISTINCT_DAYS = 4
private const val QA_BUSINESS_NAME = "QA Test Workshop"

/**
 * Client-side (Group A) referral debug actions — everything a tester on a debug
 * build can do with their OWN account and the paths Firestore rules allow. The
 * server-side lifecycle (grade / confirm / sweep) is admin-gated and lives in
 * [ReferralAdminDebugActions]; the top-level referral collections are Admin-SDK
 * only, so this class never reads or writes them directly.
 */
interface ReferralDebugActions {
    /** Attribute the signed-in user to [code] via the real recordReferralAttribution callable. */
    suspend fun attributeWithCode(code: String): DebugActionResult

    /**
     * Make the signed-in user QUALIFY: set a workshop [QA_BUSINESS_NAME] (→ activated)
     * and create [QUALIFY_DISTINCT_DAYS] customers on that many distinct Lagos days
     * inside the window ([nowMs], +1d, +2d, +3d). The grader still has to run
     * (nightly, or [ReferralAdminDebugActions.runGrader]) to promote the referral.
     */
    suspend fun seedQualification(nowMs: Long): DebugActionResult

    /** Clear the local attributed/checked flags so capture can be re-tested. */
    suspend fun resetCaptureState(): DebugActionResult
}

class DefaultReferralDebugActions(
    private val referralRepository: ReferralRepository,
    private val preferences: ReferralPreferencesStore,
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ReferralDebugActions {

    override suspend fun attributeWithCode(code: String): DebugActionResult {
        authRepository.getCurrentUser()
            ?: return DebugActionResult.Failure("Not signed in")
        val deviceHash = preferences.getOrCreateDeviceId()
        return when (val result = referralRepository.recordAttribution(code, deviceHash, ReferralSource.MANUAL)) {
            is Result.Success -> DebugActionResult.Success
            is Result.Error -> {
                AppLogger.e(tag = TAG) { "attributeWithCode failed: ${result.error}" }
                DebugActionResult.Failure(readableError(result.error))
            }
        }
    }

    @Suppress("ReturnCount")
    override suspend fun seedQualification(nowMs: Long): DebugActionResult {
        val user = authRepository.getCurrentUser()
            ?: return DebugActionResult.Failure("Not signed in")

        val profileResult = userRepository.updateProfile(
            userId = user.id,
            businessName = QA_BUSINESS_NAME,
            displayName = user.displayName,
            phoneNumber = user.phoneNumber,
            whatsappNumber = user.whatsappNumber,
            avatarColorIndex = user.avatarColorIndex,
        )
        if (profileResult is Result.Error) {
            return DebugActionResult.Failure("Set business name failed: ${profileResult.error}")
        }

        for (i in 0 until QUALIFY_DISTINCT_DAYS) {
            val createdAt = nowMs + i * DAY_MS
            val customer = Customer(
                id = "qa_seed_day_$i",
                userId = user.id,
                name = "QA Seed Day $i",
                phone = "",
                createdAt = createdAt,
            )
            val created = customerRepository.createCustomer(user.id, customer)
            if (created is Result.Error) {
                return DebugActionResult.Failure("Seed customer $i failed: ${created.error}")
            }
        }
        return DebugActionResult.Success
    }

    override suspend fun resetCaptureState(): DebugActionResult {
        preferences.resetForDebug()
        return DebugActionResult.Success
    }

    private fun readableError(error: ReferralError): String = when (error) {
        ReferralError.CODE_NOT_FOUND -> "Code not found (unknown or disabled)"
        ReferralError.UNAUTHENTICATED -> "Not authenticated"
        ReferralError.NETWORK -> "Network error"
        ReferralError.UNKNOWN -> "Unknown error"
    }
}

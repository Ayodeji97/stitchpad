package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.domain.DeletionReason

/**
 * Single-screen state for the multi-phase delete-account flow.
 * The screen renders a different overlay (dialog / bottom sheet / full-screen)
 * depending on [phase]. The user moves linearly through Confirm → Reason →
 * Reauth → Processing → Goodbye.
 */
data class DeleteAccountState(
    val isLoading: Boolean = true,
    val email: String = "",
    val signInProvider: SignInProvider = SignInProvider.UNKNOWN,
    val plan: String = "free",
    val daysActive: Int = 0,

    val phase: DeletePhase = DeletePhase.Confirm,

    val selectedReason: DeletionReason? = null,
    val additionalNotes: String = "",

    val reauthPassword: String = "",
    val reauthError: UiText? = null,
    val isReauthenticating: Boolean = false,
) {
    val canContinueFromReason: Boolean get() = selectedReason != null
}

enum class DeletePhase {
    /** Initial AlertDialog over a faded background. */
    Confirm,

    /** Reason picker bottom sheet. */
    Reason,

    /** Re-authentication bottom sheet (provider-aware). */
    Reauth,

    /** Spinner dialog while feedback is saved + account is deleted. */
    Processing,

    /** Goodbye full-screen with auto-navigate to Login after a brief delay. */
    Goodbye,
}

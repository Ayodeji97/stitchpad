package com.danzucker.stitchpad.feature.referral.presentation.entry

data class ReferralCodeState(
    val codeInput: String = "",
    val isSubmitting: Boolean = false,
) {
    /** Normalized to the server's code shape (strip spaces/hyphens, uppercase). */
    val code: String get() = codeInput.replace(Regex("[\\s-]"), "").uppercase()
    val canSubmit: Boolean get() = code.isNotBlank() && !isSubmitting
}

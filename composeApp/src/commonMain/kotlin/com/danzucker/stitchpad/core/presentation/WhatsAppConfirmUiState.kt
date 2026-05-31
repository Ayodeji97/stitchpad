package com.danzucker.stitchpad.core.presentation

import org.jetbrains.compose.resources.StringResource

/**
 * Shared UI state for the optional "Confirm on WhatsApp" round-trip, embedded
 * in both WorkshopSetupState and EditProfileState so the two screens never drift.
 *
 * [confirmed] is persisted (mirrors User.whatsappConfirmed). [code]/[input]/
 * [promptVisible]/[error] are session-only and reset on dismiss, success, or
 * any edit to the WhatsApp number.
 */
data class WhatsAppConfirmUiState(
    val confirmed: Boolean = false,
    val code: String? = null,
    val input: String = "",
    val promptVisible: Boolean = false,
    val error: StringResource? = null,
)

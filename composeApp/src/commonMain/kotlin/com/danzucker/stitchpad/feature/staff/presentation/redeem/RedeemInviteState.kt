package com.danzucker.stitchpad.feature.staff.presentation.redeem

import com.danzucker.stitchpad.core.presentation.UiText

/** Length of a staff invite code (Crockford-8), matching the server's generateStaffInvite. */
const val INVITE_CODE_LENGTH = 8

data class RedeemInviteState(
    /** Normalised code: uppercase, no separators, at most [INVITE_CODE_LENGTH] chars. */
    val code: String = "",
    val codeError: UiText? = null,
    val isLoading: Boolean = false,
    /** True when the code arrived via a JOIN_WORKSHOP deep link (changes the subtitle). */
    val prefilled: Boolean = false,
) {
    val canSubmit: Boolean get() = code.length == INVITE_CODE_LENGTH && !isLoading
}

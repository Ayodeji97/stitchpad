package com.danzucker.stitchpad.feature.referral.presentation.entry

sealed interface ReferralCodeAction {
    data class OnCodeChange(val value: String) : ReferralCodeAction
    data object OnApplyClick : ReferralCodeAction
    data object OnBackClick : ReferralCodeAction
}

package com.danzucker.stitchpad.feature.staff.presentation.redeem

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface RedeemInviteEvent {
    /** Redeem succeeded — go to the waiting screen for [workshopName]. */
    data class NavigateToPending(val workshopName: String) : RedeemInviteEvent
    data object NavigateBack : RedeemInviteEvent
    data object SignedOut : RedeemInviteEvent
    data class ShowError(val message: UiText) : RedeemInviteEvent
}

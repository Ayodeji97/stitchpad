package com.danzucker.stitchpad.feature.staff.presentation.redeem

sealed interface RedeemInviteAction {
    data class OnCodeChange(val code: String) : RedeemInviteAction
    data object OnJoinClick : RedeemInviteAction
    data object OnBackClick : RedeemInviteAction
    data object OnSignOutClick : RedeemInviteAction
}

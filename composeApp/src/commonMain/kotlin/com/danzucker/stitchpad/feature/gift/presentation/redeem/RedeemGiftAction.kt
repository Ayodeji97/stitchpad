package com.danzucker.stitchpad.feature.gift.presentation.redeem

sealed interface RedeemGiftAction {
    data class OnCodeChange(val code: String) : RedeemGiftAction
    data object OnRedeemClick : RedeemGiftAction
    data object OnConfirmAccept : RedeemGiftAction
    data object OnDismissSheet : RedeemGiftAction
    data object OnBack : RedeemGiftAction
}

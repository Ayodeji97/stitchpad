package com.danzucker.stitchpad.feature.gift.presentation.redeem

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface RedeemGiftEvent {
    data class ShowSnackbar(val message: UiText) : RedeemGiftEvent

    /**
     * Emitted after a successful claim. The Root shows a success snackbar and pops
     * back; the new tier flows in automatically via EntitlementsProvider.
     */
    data class Redeemed(val message: UiText) : RedeemGiftEvent

    data object NavigateBack : RedeemGiftEvent
}

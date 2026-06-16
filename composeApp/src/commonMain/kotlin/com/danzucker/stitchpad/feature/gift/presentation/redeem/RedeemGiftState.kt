package com.danzucker.stitchpad.feature.gift.presentation.redeem

data class RedeemGiftState(
    val code: String = "",
    val accountEmail: String? = null,
    val showAcceptSheet: Boolean = false,
    val isRedeeming: Boolean = false,
) {
    /** The Redeem button is enabled only with a non-blank code and no in-flight claim. */
    val canRedeem: Boolean get() = code.isNotBlank() && !isRedeeming
}

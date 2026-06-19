package com.danzucker.stitchpad.feature.gift.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.gift.domain.GiftError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.gift_error_already_claimed
import stitchpad.composeapp.generated.resources.gift_error_expired
import stitchpad.composeapp.generated.resources.gift_error_not_found
import stitchpad.composeapp.generated.resources.gift_error_not_payable
import stitchpad.composeapp.generated.resources.gift_error_unauthenticated

fun GiftError.toUiText(): UiText = when (this) {
    GiftError.NOT_FOUND -> UiText.StringResourceText(Res.string.gift_error_not_found)
    GiftError.ALREADY_CLAIMED -> UiText.StringResourceText(Res.string.gift_error_already_claimed)
    GiftError.EXPIRED -> UiText.StringResourceText(Res.string.gift_error_expired)
    GiftError.NOT_PAYABLE -> UiText.StringResourceText(Res.string.gift_error_not_payable)
    GiftError.UNAUTHENTICATED -> UiText.StringResourceText(Res.string.gift_error_unauthenticated)
    GiftError.NETWORK -> UiText.StringResourceText(Res.string.error_no_internet)
    GiftError.UNKNOWN -> UiText.StringResourceText(Res.string.error_unknown)
}

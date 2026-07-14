package com.danzucker.stitchpad.feature.referral.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.referral_code_generic_error
import stitchpad.composeapp.generated.resources.referral_code_network_error
import stitchpad.composeapp.generated.resources.referral_code_not_recognized

fun ReferralError.toUiText(): UiText = when (this) {
    ReferralError.CODE_NOT_FOUND -> UiText.StringResourceText(Res.string.referral_code_not_recognized)
    ReferralError.NETWORK -> UiText.StringResourceText(Res.string.referral_code_network_error)
    ReferralError.UNAUTHENTICATED,
    ReferralError.UNKNOWN -> UiText.StringResourceText(Res.string.referral_code_generic_error)
}

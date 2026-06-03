package com.danzucker.stitchpad.feature.freemium.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.payment_error_invalid_plan
import stitchpad.composeapp.generated.resources.payment_error_provider_unavailable
import stitchpad.composeapp.generated.resources.payment_error_unauthenticated

fun PaymentError.toUiText(): UiText = when (this) {
    PaymentError.NETWORK -> UiText.StringResourceText(Res.string.error_no_internet)
    PaymentError.UNAUTHENTICATED -> UiText.StringResourceText(Res.string.payment_error_unauthenticated)
    PaymentError.INVALID_PLAN -> UiText.StringResourceText(Res.string.payment_error_invalid_plan)
    PaymentError.PROVIDER_UNAVAILABLE -> UiText.StringResourceText(Res.string.payment_error_provider_unavailable)
    PaymentError.UNKNOWN -> UiText.StringResourceText(Res.string.error_unknown)
}

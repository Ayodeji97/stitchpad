package com.danzucker.stitchpad.feature.customer.presentation

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.customer.domain.CustomerError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_customer_already_exists
import stitchpad.composeapp.generated.resources.error_customer_not_found
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown

fun CustomerError.toUiText(): UiText = when (this) {
    CustomerError.NOT_FOUND -> UiText.StringResourceText(Res.string.error_customer_not_found)
    CustomerError.ALREADY_EXISTS -> UiText.StringResourceText(Res.string.error_customer_already_exists)
    CustomerError.UNKNOWN -> UiText.StringResourceText(Res.string.error_unknown)
}

fun DataError.Network.toCustomerUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    DataError.Network.NOT_FOUND -> UiText.StringResourceText(Res.string.error_customer_not_found)
    DataError.Network.CONFLICT -> UiText.StringResourceText(Res.string.error_customer_already_exists)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}

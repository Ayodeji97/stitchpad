package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_order_not_found
import stitchpad.composeapp.generated.resources.error_unknown

fun DataError.Network.toOrderUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    DataError.Network.NOT_FOUND -> UiText.StringResourceText(Res.string.error_order_not_found)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}

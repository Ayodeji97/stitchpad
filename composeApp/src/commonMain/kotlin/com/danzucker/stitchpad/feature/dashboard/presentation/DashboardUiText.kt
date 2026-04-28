package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown

fun DataError.Network.toDashboardUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}

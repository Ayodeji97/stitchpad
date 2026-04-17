package com.danzucker.stitchpad.feature.measurement.presentation

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.measurement.domain.MeasurementError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_measurement_not_found
import stitchpad.composeapp.generated.resources.error_measurement_unknown
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown

fun MeasurementError.toUiText(): UiText = when (this) {
    MeasurementError.NOT_FOUND -> UiText.StringResourceText(Res.string.error_measurement_not_found)
    MeasurementError.UNKNOWN -> UiText.StringResourceText(Res.string.error_measurement_unknown)
}

fun DataError.Network.toMeasurementUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    DataError.Network.NOT_FOUND -> UiText.StringResourceText(Res.string.error_measurement_not_found)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}

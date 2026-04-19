package com.danzucker.stitchpad.feature.style.presentation

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.style.domain.StyleError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_photo_too_large
import stitchpad.composeapp.generated.resources.error_style_not_found
import stitchpad.composeapp.generated.resources.error_style_unknown
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_upload_failed

fun StyleError.toUiText(): UiText = when (this) {
    StyleError.PHOTO_TOO_LARGE -> UiText.StringResourceText(Res.string.error_photo_too_large)
    StyleError.UPLOAD_FAILED -> UiText.StringResourceText(Res.string.error_upload_failed)
    StyleError.NOT_FOUND -> UiText.StringResourceText(Res.string.error_style_not_found)
    StyleError.UNKNOWN -> UiText.StringResourceText(Res.string.error_style_unknown)
}

fun DataError.Network.toStyleUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    DataError.Network.NOT_FOUND -> UiText.StringResourceText(Res.string.error_style_not_found)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}

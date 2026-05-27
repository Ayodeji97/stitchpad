package com.danzucker.stitchpad.feature.branding.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.workshop_logo_invalid_format
import stitchpad.composeapp.generated.resources.workshop_logo_too_large
import stitchpad.composeapp.generated.resources.workshop_logo_upload_failed

fun BrandLogoError.toUiText(): UiText = when (this) {
    BrandLogoError.TooLarge -> UiText.StringResourceText(Res.string.workshop_logo_too_large)
    BrandLogoError.UnsupportedFormat -> UiText.StringResourceText(Res.string.workshop_logo_invalid_format)
    is BrandLogoError.Network -> when (cause) {
        com.danzucker.stitchpad.core.domain.error.DataError.Network.NO_INTERNET ->
            UiText.StringResourceText(Res.string.error_no_internet)
        else -> UiText.StringResourceText(Res.string.workshop_logo_upload_failed)
    }
}

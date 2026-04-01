package com.danzucker.stitchpad.feature.auth.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_email_in_use
import stitchpad.composeapp.generated.resources.error_invalid_credentials
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_too_many_requests
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_user_not_found
import stitchpad.composeapp.generated.resources.error_weak_password

fun AuthError.toUiText(): UiText = when (this) {
    AuthError.INVALID_CREDENTIALS -> UiText.StringResourceText(Res.string.error_invalid_credentials)
    AuthError.EMAIL_ALREADY_IN_USE -> UiText.StringResourceText(Res.string.error_email_in_use)
    AuthError.WEAK_PASSWORD -> UiText.StringResourceText(Res.string.error_weak_password)
    AuthError.USER_NOT_FOUND -> UiText.StringResourceText(Res.string.error_user_not_found)
    AuthError.TOO_MANY_REQUESTS -> UiText.StringResourceText(Res.string.error_too_many_requests)
    AuthError.NETWORK_ERROR -> UiText.StringResourceText(Res.string.error_no_internet)
    AuthError.UNKNOWN -> UiText.StringResourceText(Res.string.error_unknown)
}

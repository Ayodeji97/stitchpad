package com.danzucker.stitchpad.feature.auth.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_email_in_use
import stitchpad.composeapp.generated.resources.error_invalid_credentials
import stitchpad.composeapp.generated.resources.error_invalid_email
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_provider_not_supported
import stitchpad.composeapp.generated.resources.error_requires_recent_login
import stitchpad.composeapp.generated.resources.error_sso_cancelled
import stitchpad.composeapp.generated.resources.error_sso_email_collision
import stitchpad.composeapp.generated.resources.error_sso_unavailable
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
    AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER -> UiText.StringResourceText(Res.string.error_sso_email_collision)
    AuthError.REQUIRES_RECENT_LOGIN -> UiText.StringResourceText(Res.string.error_requires_recent_login)
    AuthError.SSO_CANCELLED -> UiText.StringResourceText(Res.string.error_sso_cancelled)
    AuthError.SSO_UNAVAILABLE -> UiText.StringResourceText(Res.string.error_sso_unavailable)
    AuthError.INVALID_EMAIL -> UiText.StringResourceText(Res.string.error_invalid_email)
    AuthError.PROVIDER_NOT_SUPPORTED -> UiText.StringResourceText(Res.string.error_provider_not_supported)
    AuthError.UNKNOWN -> UiText.StringResourceText(Res.string.error_unknown)
}

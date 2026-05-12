package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class AuthError : Error {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    USER_NOT_FOUND,
    TOO_MANY_REQUESTS,
    NETWORK_ERROR,
    EMAIL_REGISTERED_WITH_OTHER_PROVIDER,
    REQUIRES_RECENT_LOGIN,
    SSO_CANCELLED,
    SSO_UNAVAILABLE,
    UNKNOWN,
}

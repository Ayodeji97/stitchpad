package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class SsoError : Error {
    CANCELLED,
    NO_PROVIDER,
    NETWORK,
    UNKNOWN,
}

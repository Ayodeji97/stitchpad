package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class PaymentError : Error {
    NETWORK,
    UNAUTHENTICATED,
    INVALID_PLAN,
    PROVIDER_UNAVAILABLE,
    UNKNOWN,
}

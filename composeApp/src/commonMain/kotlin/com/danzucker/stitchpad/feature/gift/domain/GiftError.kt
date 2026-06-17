package com.danzucker.stitchpad.feature.gift.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class GiftError : Error {
    NOT_FOUND,
    ALREADY_CLAIMED,
    EXPIRED,
    NOT_PAYABLE,
    UNAUTHENTICATED,
    NETWORK,
    UNKNOWN,
}

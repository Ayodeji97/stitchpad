package com.danzucker.stitchpad.feature.customer.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class CustomerError : Error {
    NOT_FOUND, ALREADY_EXISTS, UNKNOWN
}

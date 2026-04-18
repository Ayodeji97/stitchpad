package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.error.Error

enum class StyleError : Error {
    PHOTO_TOO_LARGE, UPLOAD_FAILED, NOT_FOUND, UNKNOWN
}

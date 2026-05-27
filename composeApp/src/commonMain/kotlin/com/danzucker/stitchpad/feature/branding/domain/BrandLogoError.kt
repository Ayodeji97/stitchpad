package com.danzucker.stitchpad.feature.branding.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Error

sealed interface BrandLogoError : Error {
    data object TooLarge : BrandLogoError
    data object UnsupportedFormat : BrandLogoError
    data class Network(val cause: DataError.Network) : BrandLogoError
}

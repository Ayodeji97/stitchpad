package com.danzucker.stitchpad.feature.smart.domain.error

import com.danzucker.stitchpad.core.domain.error.Error

/**
 * Errors specific to the Smart Suggestions feature. Returned wrapped in
 * Result.Error from the SmartRepository.
 */
sealed interface SmartError : Error {
    data object Network : SmartError
    data object FreeTierExhausted : SmartError
    data object InvalidInput : SmartError
    data object ServiceUnavailable : SmartError
    data object Unknown : SmartError
}

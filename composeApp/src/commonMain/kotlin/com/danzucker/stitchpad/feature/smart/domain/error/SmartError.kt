package com.danzucker.stitchpad.feature.smart.domain.error

import com.danzucker.stitchpad.core.domain.error.Error

/**
 * Errors specific to the Smart Suggestions feature. Returned wrapped in
 * Result.Error from the SmartRepository.
 */
sealed interface SmartError : Error {
    data object Network : SmartError
    data object FreeTierExhausted : SmartError

    /**
     * Pro subscriber has used all 50 monthly Smart coins. Distinct from
     * FreeTierExhausted so the UI can render "monthly limit reached" copy
     * instead of an upgrade-to-Pro CTA.
     */
    data object ProQuotaExhausted : SmartError
    data object InvalidInput : SmartError
    data object ServiceUnavailable : SmartError
    data object Unknown : SmartError
}

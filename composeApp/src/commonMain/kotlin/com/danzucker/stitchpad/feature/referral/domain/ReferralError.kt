package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.Error

/**
 * Failures from the client side of referral attribution. Attribution is best-effort
 * and fire-and-forget — these are logged, never surfaced to the user, since a
 * referred tailor's signup must succeed regardless of whether attribution lands.
 */
enum class ReferralError : Error {
    /** Unknown or disabled code (server returns referral_code_not_found). */
    CODE_NOT_FOUND,

    /** Caller wasn't authenticated — attribution requires a signed-in user. */
    UNAUTHENTICATED,

    NETWORK,
    UNKNOWN,
}

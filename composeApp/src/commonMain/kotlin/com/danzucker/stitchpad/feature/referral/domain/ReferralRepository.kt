package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.Result

/** Outcome of a successful attribution call. */
data class AttributionOutcome(
    /** True when the server had already attributed this install (idempotent replay). */
    val alreadyAttributed: Boolean,
    val marketerId: String,
)

/**
 * Client side of referral attribution. The server (`recordReferralAttribution`) owns
 * all fraud checks + the payout lifecycle; the app only reports the captured code +
 * a stable device hash once, at first authenticated launch.
 */
interface ReferralRepository {
    /**
     * Records that the signed-in user arrived via [code]. [deviceHash] is a stable
     * per-install id for best-effort device-reuse dedupe; [source] is analytics-only.
     */
    suspend fun recordAttribution(
        code: String,
        deviceHash: String,
        source: ReferralSource,
    ): Result<AttributionOutcome, ReferralError>
}

package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result

/** Outcome of a successful attribution call. */
data class AttributionOutcome(
    /** True when the server had already attributed this install (idempotent replay). */
    val alreadyAttributed: Boolean,
    val marketerId: String,
)

/** The signed-in user's own Founding Tailors referral link (self-serve, server-issued). */
data class ReferralLink(
    val code: String,
    val url: String,
    val playUrl: String,
)

/**
 * The signed-in tailor's own Founding Tailors standing. Rank 0 means the tailor
 * has a referral code but no points/rank yet (this month or lifetime).
 */
data class FoundingTailorsStanding(
    val monthPoints: Int,
    val monthRank: Int,
    val allTimePoints: Int,
    val allTimeRank: Int,
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

    /**
     * Fetches the signed-in user's own Founding Tailors referral link, creating and
     * persisting one server-side on first call (`getOrCreateMyReferralLink`).
     */
    suspend fun getOrCreateMyReferralLink(): Result<ReferralLink, DataError.Network>

    /**
     * The signed-in tailor's own month + lifetime Founding Tailors standing,
     * resolved from their [code] via the public leaderboard callable.
     */
    suspend fun getFoundingTailorsStanding(code: String): Result<FoundingTailorsStanding, DataError.Network>
}

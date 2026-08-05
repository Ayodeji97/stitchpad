package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralLink
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource

/** Test double for [ReferralRepository]; records the last attribution call. */
class FakeReferralRepository : ReferralRepository {
    var result: Result<AttributionOutcome, ReferralError> =
        Result.Success(AttributionOutcome(alreadyAttributed = false, marketerId = "mkt_test"))

    var lastCode: String? = null
    var lastDeviceHash: String? = null
    var lastSource: ReferralSource? = null
    var callCount: Int = 0

    var referralLinkResult: Result<ReferralLink, DataError.Network> =
        Result.Success(
            ReferralLink(
                code = "CODE0",
                url = "https://link.getstitchpad.com/r/CODE0",
                playUrl = "https://play.google.com/store/apps/details?id=com.danzucker.stitchpad&referrer=ref%3DCODE0",
            ),
        )
    var referralLinkCallCount: Int = 0

    var standingResult: Result<FoundingTailorsStanding, DataError.Network> =
        Result.Success(FoundingTailorsStanding(monthPoints = 0, monthRank = 0, allTimePoints = 0, allTimeRank = 0))
    var standingCallCount: Int = 0
    var lastStandingCode: String? = null

    override suspend fun recordAttribution(
        code: String,
        deviceHash: String,
        source: ReferralSource,
    ): Result<AttributionOutcome, ReferralError> {
        callCount++
        lastCode = code
        lastDeviceHash = deviceHash
        lastSource = source
        return result
    }

    override suspend fun getOrCreateMyReferralLink(): Result<ReferralLink, DataError.Network> {
        referralLinkCallCount++
        return referralLinkResult
    }

    override suspend fun getFoundingTailorsStanding(
        code: String,
    ): Result<FoundingTailorsStanding, DataError.Network> {
        standingCallCount++
        lastStandingCode = code
        return standingResult
    }
}

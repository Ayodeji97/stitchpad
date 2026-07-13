package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
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
}

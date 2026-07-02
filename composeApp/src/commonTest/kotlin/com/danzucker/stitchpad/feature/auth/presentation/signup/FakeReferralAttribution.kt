package com.danzucker.stitchpad.feature.auth.presentation.signup

import com.danzucker.stitchpad.feature.referral.domain.ReferralAttribution

/** Records the manual codes SignUp submits for attribution. */
class FakeReferralAttribution : ReferralAttribution {
    val submittedCodes = mutableListOf<String?>()

    override fun submitPendingAttribution(manualCode: String?) {
        submittedCodes += manualCode
    }
}

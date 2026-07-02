package com.danzucker.stitchpad.feature.auth.presentation.signup

import com.danzucker.stitchpad.feature.referral.domain.ReferralAttribution

/** Records the manual codes SignUp submits for attribution; capture is a no-op. */
class FakeReferralAttribution : ReferralAttribution {
    val submittedCodes = mutableListOf<String?>()

    override fun captureInstallReferrer() = Unit

    override fun submitPendingAttribution(manualCode: String?) {
        submittedCodes += manualCode
    }
}

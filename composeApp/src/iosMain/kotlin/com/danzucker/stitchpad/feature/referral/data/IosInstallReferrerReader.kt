package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.feature.referral.domain.InstallReferrerReader

/**
 * No Play Install Referrer on iOS. Attribution on iOS comes from clipboard-assisted
 * capture + the manual code field (Slice 5), so this reader always yields null.
 */
class IosInstallReferrerReader : InstallReferrerReader {
    override suspend fun readReferrer(): String? = null
}

package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.feature.referral.domain.ClipboardReferralReader

/**
 * No clipboard-assisted capture on Android — attribution comes from the Play Install
 * Referrer (deterministic, no clipboard-read prompt). Always yields null.
 */
class AndroidClipboardReferralReader : ClipboardReferralReader {
    override suspend fun readClipboard(): String? = null
}

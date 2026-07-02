package com.danzucker.stitchpad.feature.referral.data

import com.danzucker.stitchpad.feature.referral.domain.ClipboardReferralReader
import platform.UIKit.UIPasteboard

/**
 * Reads the iOS general pasteboard for a referral link. Guarded by `hasStrings` so an
 * empty/non-string clipboard is skipped without touching the contents. Reading the
 * string does surface the iOS "pasted from…" banner once — an accepted trade-off for
 * deferred referral attribution; the caller reads at most once per install and only
 * keeps the value when it's a recognizable /r/ referral URL.
 *
 * (Future refinement: UIPasteboard.detectPatterns can probe for a URL without reading
 * content — avoids the banner for organic installs. Deferred to keep this slice small.)
 */
class IosClipboardReferralReader : ClipboardReferralReader {
    override suspend fun readClipboard(): String? {
        val pasteboard = UIPasteboard.generalPasteboard
        if (!pasteboard.hasStrings) return null
        return pasteboard.string
    }
}

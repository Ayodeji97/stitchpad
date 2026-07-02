package com.danzucker.stitchpad.feature.referral.domain

/**
 * Reads the system clipboard once for a referral link (iOS clipboard-assisted capture).
 * The /r/ web landing page copies the full `link.getstitchpad.com/r/<code>` URL before
 * sending the user to the App Store; on first launch the app reads it back here.
 *
 * Returns the raw clipboard string (parsed + validated by the caller via
 * DeepLinkParser.parseReferral), or null when there's nothing to read — including on
 * Android, where attribution comes from the Play Install Referrer (no-op impl).
 */
interface ClipboardReferralReader {
    suspend fun readClipboard(): String?
}

package com.danzucker.stitchpad.feature.referral.domain

/**
 * Reads the raw Google Play Install Referrer string once (Android only). The value
 * is the URL-decoded `referrer` param the marketer's Play link carried, e.g.
 * `ref=ABCD1234` (parsed by DeepLinkParser.parseInstallReferrerCode). Returns null
 * when unavailable — no Play install, service missing, or on iOS (no-op impl).
 */
interface InstallReferrerReader {
    suspend fun readReferrer(): String?
}

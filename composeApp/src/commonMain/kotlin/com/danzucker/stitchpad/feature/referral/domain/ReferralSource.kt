package com.danzucker.stitchpad.feature.referral.domain

/**
 * How the referral code reached the app. Analytics-only on the server, but the
 * client picks the right value so the attribution record is honest about the path.
 * Wire values MUST match the server's accepted set in recordAttribution.ts (asSource).
 */
enum class ReferralSource(val wire: String) {
    /** Android Google Play Install Referrer, or a tapped /r/ App Link. */
    INSTALL_REFERRER("install_referrer"),

    /** iOS clipboard-assisted capture (Slice 5). */
    CLIPBOARD("clipboard"),

    /** User typed the code into the "Have a referral code?" field. */
    MANUAL("manual"),
}

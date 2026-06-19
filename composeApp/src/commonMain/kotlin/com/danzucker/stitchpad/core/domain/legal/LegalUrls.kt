package com.danzucker.stitchpad.core.domain.legal

/**
 * Canonical legal page URLs, served from our own domain (getstitchpad.com) rather
 * than the raw policy host. Linked from Sign Up, Settings, and the Upgrade paywall.
 * Apple Guideline 3.1.2 requires functional Privacy Policy + Terms of Use (EULA)
 * links in the binary for auto-renewable subscriptions, so keep these resolvable.
 */
object LegalUrls {
    const val PRIVACY = "https://getstitchpad.com/privacy"
    const val TERMS = "https://getstitchpad.com/terms"
}

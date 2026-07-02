package com.danzucker.stitchpad.navigation

/**
 * Pure, platform-agnostic parser for the renewal "Renew" deep link. Both the iOS
 * (`handleIosDeepLink`) and Android (`MainActivity`) entry points route through here so
 * the matching rules stay identical and unit-testable.
 *
 * Recognises two equivalent forms of the same Upgrade link:
 *  - the custom scheme `stitchpad://upgrade` (works from Apple Mail / manual taps), and
 *  - the https Universal Link / App Link `https://link.getstitchpad.com/upgrade` (the form
 *    the renewal email uses, because Gmail's iOS app refuses custom-scheme links).
 */
object DeepLinkParser {
    /** Host of the https Universal Link / App Link. Kept in sync with the apps' associated
     *  domains / intent-filters and the email link the Cloud Function builds. */
    const val UNIVERSAL_LINK_HOST = "link.getstitchpad.com"

    private const val CUSTOM_SCHEME_BASE = "stitchpad://upgrade"
    private val universalLinkBase = "https://$UNIVERSAL_LINK_HOST/upgrade"

    private const val CLAIM_CUSTOM_SCHEME_BASE = "stitchpad://claim"
    private val claimUniversalLinkBase = "https://$UNIVERSAL_LINK_HOST/claim"

    private const val REFERRAL_CUSTOM_SCHEME_BASE = "stitchpad://r"
    private val referralUniversalLinkBase = "https://$UNIVERSAL_LINK_HOST/r"

    // Referral codes are uppercase over the Crockford-ish alphabet (see the server's
    // asCode in recordAttribution.ts). Normalize + constrain here too so a manually
    // typed code is cleaned before it ever hits the wire.
    private val codeCharset = Regex("^[0-9A-Z]{1,32}$")
    private val stripChars = Regex("[\\s-]")

    /**
     * Returns the plan to pre-select (possibly empty when no query is present) when [url] is
     * an Upgrade deep link in either supported form, or null when it is not ours to handle.
     */
    fun parseUpgrade(url: String?): UpgradePreselect? {
        if (url == null || (!isExactRoute(url, CUSTOM_SCHEME_BASE) && !isExactRoute(url, universalLinkBase))) {
            return null
        }
        val query = parseQuery(url)
        return UpgradePreselect(tier = query["tier"], cadence = query["cadence"])
    }

    /**
     * Returns the bearer gift code when [url] is a gift-claim deep link
     * (https://link.getstitchpad.com/claim?code= from the gift email, or the
     * stitchpad://claim?code= custom scheme for Apple Mail / manual taps), or null
     * when it is not a claim link or carries no code.
     */
    fun parseClaimGift(url: String?): String? {
        if (url == null ||
            (!isExactRoute(url, CLAIM_CUSTOM_SCHEME_BASE) && !isExactRoute(url, claimUniversalLinkBase))
        ) {
            return null
        }
        return parseQuery(url)["code"]?.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns the normalized referral code when [url] is a referral link
     * (https://link.getstitchpad.com/r/&lt;code&gt; App Link, or the stitchpad://r custom
     * scheme), or null when it is not ours / carries no valid code. The code may be a
     * path segment (/r/CODE) or a query param (?ref= / ?code=).
     */
    @Suppress("ReturnCount") // staged guards (null url, non-referral) read clearer than nesting
    fun parseReferral(url: String?): String? {
        if (url == null) return null
        val base = when {
            isExactRoute(url, REFERRAL_CUSTOM_SCHEME_BASE) -> REFERRAL_CUSTOM_SCHEME_BASE
            isExactRoute(url, referralUniversalLinkBase) -> referralUniversalLinkBase
            else -> return null
        }
        val query = parseQuery(url)
        val fromPath = url.removePrefix(base)
            .removePrefix("/")
            .substringBefore('?')
            .substringBefore('/')
            .takeIf { it.isNotEmpty() }
        return normalizeReferralCode(query["ref"] ?: query["code"] ?: fromPath)
    }

    /**
     * Extracts the referral code from a raw Play Install Referrer string (e.g.
     * "ref=ABCD1234" or "utm_source=x&ref=ABCD1234"), normalized, or null.
     */
    fun parseInstallReferrerCode(referrer: String?): String? {
        if (referrer.isNullOrEmpty()) return null
        val query = parseQuery("?$referrer")
        return normalizeReferralCode(query["ref"] ?: query["code"])
    }

    /** Uppercases, strips spaces/hyphens, and validates against the code charset. */
    fun normalizeReferralCode(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.replace(stripChars, "").uppercase()
        return if (codeCharset.matches(cleaned)) cleaned else null
    }

    /** Exact route match (optionally with a query or sub-path), so "/upgradezzz" never matches. */
    private fun isExactRoute(url: String, base: String): Boolean =
        url == base || url.startsWith("$base?") || url.startsWith("$base/")

    /** Minimal query-string parse (no android.net.Uri on iOS): "a=1&b=2" -> map. */
    private fun parseQuery(url: String): Map<String, String> {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotEmpty()) parts[0] to parts[1] else null
        }.toMap()
    }
}

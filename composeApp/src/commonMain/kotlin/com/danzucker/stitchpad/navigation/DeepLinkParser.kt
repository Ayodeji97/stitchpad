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

    /**
     * Returns the plan to pre-select (possibly empty when no query is present) when [url] is
     * an Upgrade deep link in either supported form, or null when it is not ours to handle.
     */
    fun parseUpgrade(url: String?): UpgradePreselect? {
        if (url == null || (!isUpgradeRoute(url, CUSTOM_SCHEME_BASE) && !isUpgradeRoute(url, universalLinkBase))) {
            return null
        }
        val query = parseQuery(url)
        return UpgradePreselect(tier = query["tier"], cadence = query["cadence"])
    }

    /** Exact route match (optionally with a query or sub-path), so "/upgradezzz" never matches. */
    private fun isUpgradeRoute(url: String, base: String): Boolean =
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

package com.danzucker.stitchpad.navigation

object PushTargetParser {
    const val TARGET_KEY = "target"
    const val ORDER_ID_KEY = "orderId"
    const val TARGET_INBOX = "inbox"
    const val TARGET_ORDER = "order"
    const val TARGET_TO_COLLECT = "to_collect"
    const val TARGET_DASHBOARD = "dashboard"
    const val TARGET_FOUNDING_TAILORS = "founding_tailors"

    data class Parsed(val target: DeepLinkTarget, val orderId: String? = null)

    /**
     * Maps a push payload's `target` to a navigation target. Keep the accepted
     * values in sync with CAMPAIGN_TARGETS in functions/src/notifications/engagementConfig.ts.
     *
     * An unrecognised target returns null rather than throwing: the notification
     * still displays and the tap just opens the app normally. That is what lets the
     * server introduce a new target before every client has updated.
     */
    fun parse(data: Map<String, String>): Parsed? = when (data[TARGET_KEY]) {
        TARGET_INBOX -> Parsed(DeepLinkTarget.INBOX)
        TARGET_TO_COLLECT -> Parsed(DeepLinkTarget.TO_COLLECT)
        TARGET_DASHBOARD -> Parsed(DeepLinkTarget.DASHBOARD)
        TARGET_FOUNDING_TAILORS -> Parsed(DeepLinkTarget.FOUNDING_TAILORS)
        TARGET_ORDER -> data[ORDER_ID_KEY]?.takeIf { it.isNotBlank() }?.let { Parsed(DeepLinkTarget.ORDER, it) }
        else -> null
    }
}

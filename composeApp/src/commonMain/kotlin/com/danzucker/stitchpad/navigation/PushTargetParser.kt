package com.danzucker.stitchpad.navigation

object PushTargetParser {
    const val TARGET_KEY = "target"
    const val ORDER_ID_KEY = "orderId"
    const val TARGET_INBOX = "inbox"
    const val TARGET_ORDER = "order"
    const val TARGET_TO_COLLECT = "to_collect"

    data class Parsed(val target: DeepLinkTarget, val orderId: String? = null)

    fun parse(data: Map<String, String>): Parsed? = when (data[TARGET_KEY]) {
        TARGET_INBOX -> Parsed(DeepLinkTarget.INBOX)
        TARGET_TO_COLLECT -> Parsed(DeepLinkTarget.TO_COLLECT)
        TARGET_ORDER -> data[ORDER_ID_KEY]?.takeIf { it.isNotBlank() }?.let { Parsed(DeepLinkTarget.ORDER, it) }
        else -> null
    }
}

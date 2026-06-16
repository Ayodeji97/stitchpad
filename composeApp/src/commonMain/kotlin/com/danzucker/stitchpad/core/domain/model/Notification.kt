package com.danzucker.stitchpad.core.domain.model

enum class NotificationType { OVERDUE, DUE_SOON, TO_COLLECT, GIFT_RECEIVED, UNKNOWN }

data class Notification(
    val id: String,
    val orderId: String,
    val type: NotificationType,
    val customerName: String,
    val garmentSummary: String,
    /** Naira owed; meaningful only for [NotificationType.TO_COLLECT]. */
    val amount: Double? = null,
    /** Epoch millis; meaningful only for OVERDUE / DUE_SOON. */
    val deadline: Long? = null,
    /** Gifted tier wire value (pro/atelier); meaningful only for [NotificationType.GIFT_RECEIVED]. */
    val tier: String? = null,
    /** Name of the gifter, if shared; meaningful only for [NotificationType.GIFT_RECEIVED]. */
    val gifterName: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = 0L,
)

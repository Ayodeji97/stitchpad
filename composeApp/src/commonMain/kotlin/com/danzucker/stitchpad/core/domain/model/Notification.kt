package com.danzucker.stitchpad.core.domain.model

enum class NotificationType {
    OVERDUE,
    DUE_SOON,
    TO_COLLECT,
    GIFT_RECEIVED,

    /** Owner inbox: a staff member moved an order's status (server: orderActivity.ts). */
    STAFF_STATUS,

    /** Staff inbox: the owner assigned an order to this staff member. Never carries money. */
    ORDER_ASSIGNED,
    UNKNOWN,
}

data class Notification(
    val id: String,
    val orderId: String,
    val type: NotificationType,
    val customerName: String,
    val garmentSummary: String,
    /** Major units owed in [currency]; meaningful only for [NotificationType.TO_COLLECT]. */
    val amount: Double? = null,
    /** Epoch millis; meaningful only for OVERDUE / DUE_SOON. */
    val deadline: Long? = null,
    /** Gifted tier wire value (pro/atelier); meaningful only for [NotificationType.GIFT_RECEIVED]. */
    val tier: String? = null,
    /** Name of the gifter, if shared; meaningful only for [NotificationType.GIFT_RECEIVED]. */
    val gifterName: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = 0L,
    /** True when a TO_COLLECT item is >= 7 days since Ready/Delivered; false otherwise. */
    val isOverdue: Boolean = false,
    /** Currency stamped on the order when the notification was produced. Legacy inbox docs are NGN. */
    val currency: Currency = Currency.NGN,
    /** New order status; meaningful only for [NotificationType.STAFF_STATUS]. */
    val status: OrderStatus? = null,
    /** Who did it (roster name); meaningful only for [NotificationType.STAFF_STATUS]. */
    val actorName: String? = null,
)

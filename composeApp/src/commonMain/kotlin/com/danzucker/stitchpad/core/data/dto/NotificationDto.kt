package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    /** Present in the doc body only by default; the mapper uses the Firestore document ID instead. */
    val id: String = "",
    val orderId: String = "",
    val type: String = "UNKNOWN",
    val customerName: String = "",
    val garmentSummary: String = "",
    val amount: Double? = null,
    val deadline: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long = 0L,
)

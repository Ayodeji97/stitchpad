package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
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

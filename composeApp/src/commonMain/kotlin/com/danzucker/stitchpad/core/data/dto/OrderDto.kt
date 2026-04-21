package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val status: String = "PENDING",
    val priority: String = "NORMAL",
    val totalPrice: Double = 0.0,
    val depositPaid: Double = 0.0,
    val balanceRemaining: Double = 0.0,
    val deadline: Long? = null,
    val notes: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val statusHistory: List<StatusChangeDto> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null
)

@Serializable
data class StatusChangeDto(
    val status: String = "PENDING",
    val changedAt: Long = 0L
)

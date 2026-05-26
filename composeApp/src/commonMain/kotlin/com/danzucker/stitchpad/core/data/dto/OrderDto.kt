package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val status: String = "PENDING",
    val subStatus: String? = null,
    val priority: String = "NORMAL",
    val totalPrice: Double = 0.0,
    /** Legacy field kept for backward-compat when reading old Firestore docs. */
    val depositPaid: Double = 0.0,
    /** Legacy field kept for backward-compat when reading old Firestore docs. */
    val balanceRemaining: Double = 0.0,
    val payments: List<PaymentDto> = emptyList(),
    val deadline: Long? = null,
    val notes: String? = null,
    val archivedAt: Long? = null,
    val items: List<OrderItemDto> = emptyList(),
    val statusHistory: List<StatusChangeDto> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class PaymentDto(
    val id: String = "",
    val amount: Double = 0.0,
    val method: String = "OTHER",
    val type: String = "DEPOSIT",
    val recordedAt: Long = 0L,
    val note: String? = null,
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
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
)

@Serializable
data class StatusChangeDto(
    val status: String = "PENDING",
    val changedAt: Long = 0L
)

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
    val quantity: Int = 1,
    val measurementId: String? = null,
    val fabricName: String? = null,
    // PTSP-11 — source of truth going forward
    val styleImages: List<StyleImageRefDto> = emptyList(),
    val fabricImages: List<FabricImageRefDto> = emptyList(),
    // Legacy single fields — kept for backward read (pre-PTSP-11 docs) AND
    // for forward double-write so older app versions can render the first image.
    // Mapper prefers `styleImages` / `fabricImages` if non-empty; falls back to
    // these otherwise. Removable in mid-2027.
    val styleId: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
)

@Serializable
data class StyleImageRefDto(
    val source: String = "UPLOADED",
    val styleId: String? = null,
    val photoUrl: String? = null,
    val photoStoragePath: String? = null,
)

@Serializable
data class FabricImageRefDto(
    val photoUrl: String = "",
    val photoStoragePath: String = "",
)

@Serializable
data class StatusChangeDto(
    val status: String = "PENDING",
    val changedAt: Long = 0L
)

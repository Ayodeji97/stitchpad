package com.danzucker.stitchpad.core.domain.model

enum class OrderStatus {
    PENDING,
    IN_PROGRESS,
    READY,
    DELIVERED
}

enum class OrderPriority {
    NORMAL,
    URGENT,
    RUSH
}

data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val description: String,
    val price: Double,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null
)

data class StatusChange(
    val status: OrderStatus,
    val changedAt: Long
)

data class Order(
    val id: String,
    val userId: String,
    val customerId: String,
    val customerName: String,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val priority: OrderPriority,
    val statusHistory: List<StatusChange>,
    val totalPrice: Double,
    val depositPaid: Double,
    val balanceRemaining: Double,
    val deadline: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

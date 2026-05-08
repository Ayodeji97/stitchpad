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

/**
 * Sub-stages within IN_PROGRESS that match how tailors narrate work
 * (cutting → sewing → fitting). Only meaningful when [Order.status] is
 * [OrderStatus.IN_PROGRESS]; null otherwise.
 */
enum class OrderSubStatus {
    CUTTING,
    SEWING,
    FITTING,
}

data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val description: String,
    val price: Double,
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String? = null,
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
    val subStatus: OrderSubStatus? = null,
    val priority: OrderPriority,
    val statusHistory: List<StatusChange>,
    val totalPrice: Double,
    val payments: List<Payment> = emptyList(),
    val deadline: Long?,
    val notes: String?,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Sum of all recorded payments. Replaces the prior persisted `depositPaid` field. */
    val depositPaid: Double get() = payments.sumOf { it.amount }

    /** Outstanding balance. Always recomputed from [totalPrice] and [payments]. */
    val balanceRemaining: Double get() = (totalPrice - depositPaid).coerceAtLeast(0.0)
}

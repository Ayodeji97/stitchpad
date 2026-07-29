package com.danzucker.stitchpad.feature.collection.domain

import com.danzucker.stitchpad.core.domain.model.OrderStatus

/** One order that is done (Ready/Delivered) but still owes money. */
data class CollectibleOrder(
    val orderId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val balanceRemaining: Double,
    val owedSince: Long,
    val daysOwed: Int,
    val isOverdue: Boolean,
    val status: OrderStatus,
)

data class CollectionSummary(
    val totalOutstanding: Double,
    val orderCount: Int,
    val overdueCount: Int,
)

enum class CollectionSort { OLDEST_OWED, BIGGEST_BALANCE, NEWEST, CUSTOMER_NAME }

sealed interface CollectionFilter {
    data object None : CollectionFilter
    data object OverdueOnly : CollectionFilter
    data class ByStatus(val status: OrderStatus) : CollectionFilter
    data class ByCustomer(val customerId: String) : CollectionFilter
}

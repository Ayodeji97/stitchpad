package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import kotlin.time.Clock

fun OrderDto.toOrder(userId: String): Order = Order(
    id = id,
    userId = userId,
    customerId = customerId,
    customerName = customerName,
    items = items.map { it.toOrderItem() },
    status = runCatching { OrderStatus.valueOf(status) }
        .getOrDefault(OrderStatus.PENDING),
    priority = runCatching { OrderPriority.valueOf(priority) }
        .getOrDefault(OrderPriority.NORMAL),
    statusHistory = statusHistory.map { it.toStatusChange() },
    totalPrice = totalPrice,
    depositPaid = depositPaid,
    balanceRemaining = balanceRemaining,
    deadline = deadline,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Order.toOrderDto(): OrderDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return OrderDto(
        id = id,
        customerId = customerId,
        customerName = customerName,
        status = status.name,
        priority = priority.name,
        totalPrice = totalPrice,
        depositPaid = depositPaid,
        balanceRemaining = balanceRemaining,
        deadline = deadline,
        notes = notes,
        items = items.map { it.toOrderItemDto() },
        statusHistory = statusHistory.map { it.toStatusChangeDto() },
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )
}

fun OrderItemDto.toOrderItem(): OrderItem = OrderItem(
    id = id,
    garmentType = parseGarmentType(garmentType),
    description = description,
    price = price,
    styleId = styleId,
    measurementId = measurementId,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath
)

private fun parseGarmentType(value: String): GarmentType = when (value) {
    "SENATOR_KAFTAN" -> GarmentType.SENATOR
    "BUBA_AND_SKIRT" -> GarmentType.TWO_PIECE
    else -> runCatching { GarmentType.valueOf(value) }.getOrDefault(GarmentType.SHIRT)
}

fun OrderItem.toOrderItemDto(): OrderItemDto = OrderItemDto(
    id = id,
    garmentType = garmentType.name,
    description = description,
    price = price,
    styleId = styleId,
    measurementId = measurementId,
    fabricPhotoUrl = fabricPhotoUrl,
    fabricPhotoStoragePath = fabricPhotoStoragePath
)

fun StatusChangeDto.toStatusChange(): StatusChange = StatusChange(
    status = runCatching { OrderStatus.valueOf(status) }
        .getOrDefault(OrderStatus.PENDING),
    changedAt = changedAt
)

fun StatusChange.toStatusChangeDto(): StatusChangeDto = StatusChangeDto(
    status = status.name,
    changedAt = changedAt
)

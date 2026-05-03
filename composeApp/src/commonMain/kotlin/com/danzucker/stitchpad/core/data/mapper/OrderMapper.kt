package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import kotlin.time.Clock

fun OrderDto.toOrder(userId: String): Order {
    // Migrate legacy docs: if payments list is empty but the old depositPaid field has a value,
    // synthesise a single payment so the computed Order.depositPaid is non-zero.
    val resolvedPayments = if (payments.isNotEmpty()) {
        payments.map { it.toPayment() }
    } else if (depositPaid > 0.0) {
        listOf(
            Payment(
                id = "legacy-deposit",
                amount = depositPaid,
                method = PaymentMethod.OTHER,
                type = PaymentType.DEPOSIT,
                recordedAt = updatedAt,
            )
        )
    } else {
        emptyList()
    }
    return Order(
        id = id,
        userId = userId,
        customerId = customerId,
        customerName = customerName,
        items = items.map { it.toOrderItem() },
        status = runCatching { OrderStatus.valueOf(status) }
            .getOrDefault(OrderStatus.PENDING),
        subStatus = subStatus?.let { runCatching { OrderSubStatus.valueOf(it) }.getOrNull() },
        priority = runCatching { OrderPriority.valueOf(priority) }
            .getOrDefault(OrderPriority.NORMAL),
        statusHistory = statusHistory.map { it.toStatusChange() },
        totalPrice = totalPrice,
        payments = resolvedPayments,
        deadline = deadline,
        notes = notes,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Order.toOrderDto(): OrderDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return OrderDto(
        id = id,
        customerId = customerId,
        customerName = customerName,
        status = status.name,
        subStatus = subStatus?.name,
        priority = priority.name,
        totalPrice = totalPrice,
        payments = payments.map { it.toPaymentDto() },
        deadline = deadline,
        notes = notes,
        archivedAt = archivedAt,
        items = items.map { it.toOrderItemDto() },
        statusHistory = statusHistory.map { it.toStatusChangeDto() },
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now,
    )
}

fun PaymentDto.toPayment(): Payment = Payment(
    id = id,
    amount = amount,
    method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.OTHER),
    type = runCatching { PaymentType.valueOf(type) }.getOrDefault(PaymentType.DEPOSIT),
    recordedAt = recordedAt,
    note = note,
)

fun Payment.toPaymentDto(): PaymentDto = PaymentDto(
    id = id,
    amount = amount,
    method = method.name,
    type = type.name,
    recordedAt = recordedAt,
    note = note,
)

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

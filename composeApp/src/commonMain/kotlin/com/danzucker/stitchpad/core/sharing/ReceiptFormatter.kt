package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object ReceiptFormatter {

    private const val FALLBACK_BUSINESS_NAME = "StitchPad"
    private const val ORDER_ID_PREFIX_LENGTH = 4

    fun format(
        order: Order,
        user: User,
        garmentNames: Map<GarmentType, String>
    ): ReceiptData {
        val createdDate = Instant.fromEpochMilliseconds(order.createdAt)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dateFormatted = "${createdDate.dayOfMonth} ${
            createdDate.month.name.lowercase()
                .replaceFirstChar { it.uppercase() }.take(3)
        } ${createdDate.year}"

        val deadlineFormatted = order.deadline?.let { millis ->
            val d = Instant.fromEpochMilliseconds(millis)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            "${d.dayOfMonth} ${
                d.month.name.lowercase()
                    .replaceFirstChar { it.uppercase() }.take(3)
            } ${d.year}"
        }

        val groupedItems = order.items
            .groupBy { it.garmentType }
            .map { (type, items) ->
                ReceiptItem(
                    quantity = items.size,
                    garmentName = garmentNames[type] ?: type.name,
                    formattedPrice = "\u20A6${formatPrice(items.sumOf { it.price })}"
                )
            }

        val shortId = order.id
            .take(ORDER_ID_PREFIX_LENGTH)
            .uppercase()

        return ReceiptData(
            businessName = "\u2702\uFE0F ${user.businessName ?: FALLBACK_BUSINESS_NAME}",
            businessPhone = user.phoneNumber?.let { "\uD83D\uDCDE $it" },
            customerName = order.customerName,
            dateFormatted = dateFormatted,
            items = groupedItems,
            totalFormatted = "\u20A6${formatPrice(order.totalPrice)}",
            depositFormatted = "\u20A6${formatPrice(order.depositPaid)}",
            balanceFormatted = "\u20A6${formatPrice(order.balanceRemaining)}",
            isFullyPaid = order.balanceRemaining <= 0.0,
            statusLabel = statusToLabel(order.status),
            statusColorHex = statusToColorHex(order.status),
            deadlineFormatted = deadlineFormatted,
            priorityLabel = when (order.priority) {
                OrderPriority.NORMAL -> null
                OrderPriority.URGENT -> "URGENT"
                OrderPriority.RUSH -> "RUSH"
            },
            orderIdShort = "ORD-$shortId"
        )
    }

    private fun statusToLabel(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "Pending"
        OrderStatus.IN_PROGRESS -> "In Progress"
        OrderStatus.READY -> "Ready"
        OrderStatus.DELIVERED -> "Delivered"
    }

    private fun statusToColorHex(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "#2B7FD4"
        OrderStatus.IN_PROGRESS -> "#E8A800"
        OrderStatus.READY -> "#2D9E6B"
        OrderStatus.DELIVERED -> "#7D7970"
    }
}

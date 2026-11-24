package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.NotificationDto
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.domain.model.OrderStatus

fun NotificationDto.toNotification(docId: String): Notification = Notification(
    id = docId,
    orderId = orderId,
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.UNKNOWN),
    customerName = customerName,
    garmentSummary = garmentSummary,
    amount = amount,
    deadline = deadline,
    tier = tier,
    gifterName = gifterName,
    isRead = isRead,
    createdAt = createdAt,
    isOverdue = isOverdue,
    currency = Currency.fromCode(currency?.trim()) ?: Currency.NGN,
    status = status?.let { s -> OrderStatus.entries.firstOrNull { it.name == s } },
    actorName = actorName?.trim()?.takeIf { it.isNotEmpty() },
)

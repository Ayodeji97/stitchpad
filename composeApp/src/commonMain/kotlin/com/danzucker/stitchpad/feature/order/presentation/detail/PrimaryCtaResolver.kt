package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus

enum class PrimaryCta {
    StartWork,
    UpdateStatus,
    ConfirmFitting,
    MarkDelivered,
    ShareReceipt,
    SendReminder,
}

enum class SecondaryCta {
    RecordPayment,
    MessageCustomer,
    StartWork,
    UpdateStatus,
    MarkDelivered,
    DuplicateOrder,
}

data class CtaPair(val primary: PrimaryCta, val secondary: SecondaryCta)

internal fun resolvePrimaryCta(
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    isOverdue: Boolean,
    balanceRemaining: Double,
): CtaPair {
    val balanceSecondary = if (balanceRemaining > 0.0) {
        SecondaryCta.RecordPayment
    } else {
        SecondaryCta.MessageCustomer
    }
    return when {
        status == OrderStatus.DELIVERED ->
            CtaPair(PrimaryCta.ShareReceipt, SecondaryCta.DuplicateOrder)
        isOverdue && status == OrderStatus.PENDING ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.StartWork)
        isOverdue && status == OrderStatus.IN_PROGRESS ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.UpdateStatus)
        isOverdue && status == OrderStatus.READY ->
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.MarkDelivered)
        status == OrderStatus.PENDING ->
            CtaPair(PrimaryCta.StartWork, balanceSecondary)
        status == OrderStatus.IN_PROGRESS && subStatus == OrderSubStatus.FITTING ->
            CtaPair(PrimaryCta.ConfirmFitting, balanceSecondary)
        status == OrderStatus.IN_PROGRESS ->
            CtaPair(PrimaryCta.UpdateStatus, balanceSecondary)
        status == OrderStatus.READY ->
            CtaPair(PrimaryCta.MarkDelivered, SecondaryCta.MessageCustomer)
        else -> CtaPair(PrimaryCta.UpdateStatus, balanceSecondary) // unreachable
    }
}

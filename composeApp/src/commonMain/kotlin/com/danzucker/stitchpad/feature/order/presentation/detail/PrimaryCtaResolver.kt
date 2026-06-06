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
    StartWork,
    UpdateStatus,
    MarkDelivered,
    ShareReceipt,
}

data class CtaPair(val primary: PrimaryCta, val secondary: SecondaryCta?)

@Suppress("CyclomaticComplexMethod")
internal fun resolvePrimaryCta(
    status: OrderStatus,
    subStatus: OrderSubStatus?,
    isOverdue: Boolean,
    balanceRemaining: Double,
): CtaPair {
    // PTSP-29: the secondary slot defaults to "Share Receipt" so the card always
    // shows two buttons — the primary action never spreads full-width. When a
    // balance is owed, "Record Payment" takes that slot instead (more urgent).
    val balanceSecondary = if (balanceRemaining > 0.0) {
        SecondaryCta.RecordPayment
    } else {
        SecondaryCta.ShareReceipt
    }
    return when {
        // PTSP-29: keep "Update Status" on the card even when delivered (so the
        // status can still be moved back — PTSP-28), and put "Share Receipt"
        // beside it as the secondary. Duplicate now lives only in the top-bar
        // overflow menu, not on the card.
        status == OrderStatus.DELIVERED ->
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.ShareReceipt)
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
            CtaPair(PrimaryCta.MarkDelivered, balanceSecondary)
        else -> CtaPair(PrimaryCta.UpdateStatus, balanceSecondary) // unreachable
    }
}

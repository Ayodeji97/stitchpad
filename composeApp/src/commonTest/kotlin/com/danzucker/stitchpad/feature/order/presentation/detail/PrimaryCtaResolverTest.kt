package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PrimaryCtaResolverTest {

    private fun cta(
        status: OrderStatus,
        sub: OrderSubStatus? = null,
        overdue: Boolean = false,
        balance: Double = 0.0,
    ): CtaPair = resolvePrimaryCta(
        status = status,
        subStatus = sub,
        isOverdue = overdue,
        balanceRemaining = balance,
    )

    @Test
    fun pending_normal_offersStartWorkPlusRecordPayment() {
        assertEquals(
            CtaPair(PrimaryCta.StartWork, SecondaryCta.RecordPayment),
            cta(OrderStatus.PENDING, balance = 60_000.0),
        )
    }

    @Test
    fun pending_overdue_swapsToReminderPrimary() {
        assertEquals(
            CtaPair(PrimaryCta.SendReminder, SecondaryCta.StartWork),
            cta(OrderStatus.PENDING, overdue = true, balance = 60_000.0),
        )
    }

    @Test
    fun inProgressFitting_offersConfirmFitting() {
        assertEquals(
            CtaPair(PrimaryCta.ConfirmFitting, SecondaryCta.RecordPayment),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.FITTING, balance = 20_000.0),
        )
    }

    @Test
    fun inProgressSewing_offersUpdateStatus() {
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.RecordPayment),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.SEWING, balance = 30_000.0),
        )
    }

    @Test
    fun readyFullyPaid_offersMarkDeliveredPlusShareReceipt() {
        // PTSP-29: with nothing to record, the secondary defaults to Share
        // Receipt so the primary never spreads full-width across the card.
        assertEquals(
            CtaPair(PrimaryCta.MarkDelivered, SecondaryCta.ShareReceipt),
            cta(OrderStatus.READY),
        )
    }

    @Test
    fun readyWithBalance_offersRecordPayment() {
        // At pickup the tailor often collects the outstanding balance, so a
        // READY order that still owes money surfaces the Record payment shortcut.
        assertEquals(
            CtaPair(PrimaryCta.MarkDelivered, SecondaryCta.RecordPayment),
            cta(OrderStatus.READY, balance = 20_000.0),
        )
    }

    @Test
    fun delivered_keepsUpdateStatusPrimaryWithShareReceiptSecondary() {
        // PTSP-29: a delivered order keeps "Update Status" on the card (so the
        // status can still be moved back) with "Share Receipt" beside it.
        // Duplicate moves to the top-bar overflow menu, off the card.
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.ShareReceipt),
            cta(OrderStatus.DELIVERED, balance = 0.0),
        )
    }

    @Test
    fun zeroBalance_fallsBackToShareReceiptSecondary() {
        // PTSP-29: no balance to record, so the secondary is Share Receipt rather
        // than empty — keeps the two-button layout, primary not full-width.
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.ShareReceipt),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.CUTTING, balance = 0.0),
        )
    }
}

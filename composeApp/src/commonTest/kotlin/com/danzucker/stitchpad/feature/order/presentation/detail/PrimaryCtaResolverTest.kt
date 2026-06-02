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
    fun readyFullyPaid_offersMarkDeliveredNoSecondary() {
        // Nothing to record once the balance is cleared — primary goes full-width.
        assertEquals(
            CtaPair(PrimaryCta.MarkDelivered, null),
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
    fun delivered_offersShareReceiptPlusDuplicate() {
        assertEquals(
            CtaPair(PrimaryCta.ShareReceipt, SecondaryCta.DuplicateOrder),
            cta(OrderStatus.DELIVERED, balance = 0.0),
        )
    }

    @Test
    fun zeroBalance_dropsSecondary() {
        // With nothing to record and Call/WhatsApp already on the Customer card,
        // the secondary slot is empty — the primary goes full-width.
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, null),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.CUTTING, balance = 0.0),
        )
    }
}

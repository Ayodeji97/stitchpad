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
    fun ready_offersMarkDeliveredPlusMessage() {
        assertEquals(
            CtaPair(PrimaryCta.MarkDelivered, SecondaryCta.MessageCustomer),
            cta(OrderStatus.READY, balance = 80_000.0),
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
    fun zeroBalance_replacesRecordPaymentWithMessageCustomer() {
        // When balance is 0 the "Record payment" secondary doesn't make sense
        // — fall back to "Message customer" so the secondary is still useful.
        assertEquals(
            CtaPair(PrimaryCta.UpdateStatus, SecondaryCta.MessageCustomer),
            cta(OrderStatus.IN_PROGRESS, sub = OrderSubStatus.CUTTING, balance = 0.0),
        )
    }
}

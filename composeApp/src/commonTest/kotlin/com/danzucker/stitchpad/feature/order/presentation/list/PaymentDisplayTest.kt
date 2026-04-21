package com.danzucker.stitchpad.feature.order.presentation.list

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentDisplayTest {

    @Test
    fun fullyPaidReturnsPaid() {
        assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 10_000.0, totalPrice = 10_000.0))
    }

    @Test
    fun overpaidReturnsPaid() {
        assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 12_000.0, totalPrice = 10_000.0))
    }

    @Test
    fun zeroTotalReturnsPaid() {
        assertEquals(PaymentDisplay.Paid, formatPaymentStatus(depositPaid = 0.0, totalPrice = 0.0))
    }

    @Test
    fun partialPaymentReturnsPartial() {
        assertEquals(
            PaymentDisplay.Partial(amountPaid = 5_000.0),
            formatPaymentStatus(depositPaid = 5_000.0, totalPrice = 10_000.0)
        )
    }

    @Test
    fun zeroPaidReturnsUnpaid() {
        assertEquals(PaymentDisplay.Unpaid, formatPaymentStatus(depositPaid = 0.0, totalPrice = 10_000.0))
    }

    @Test
    fun abbreviateBelowOneThousandKeepsExact() {
        assertEquals("\u20A6500", PaymentDisplay.Partial(500.0).formatAbbreviated())
    }

    @Test
    fun abbreviateAtOneThousandUsesK() {
        assertEquals("\u20A61k", PaymentDisplay.Partial(1_000.0).formatAbbreviated())
    }

    @Test
    fun abbreviateTenThousandUsesK() {
        assertEquals("\u20A610k", PaymentDisplay.Partial(10_000.0).formatAbbreviated())
    }

    @Test
    fun abbreviateNineHundredNinetyNineKeepsExact() {
        assertEquals("\u20A6999", PaymentDisplay.Partial(999.0).formatAbbreviated())
    }
}

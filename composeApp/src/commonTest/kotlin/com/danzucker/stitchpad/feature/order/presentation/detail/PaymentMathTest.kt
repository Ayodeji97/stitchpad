package com.danzucker.stitchpad.feature.order.presentation.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMathTest {

    @Test
    fun progressUnpaidOrderIsZero() {
        val p = paymentProgress(totalPrice = 25_000.0, balanceRemaining = 25_000.0, discount = 0.0)
        assertEquals(25_000.0, p.netTotal)
        assertEquals(0.0, p.paid)
        assertEquals(0f, p.fraction)
    }

    @Test
    fun progressPartiallyPaidOrder() {
        val p = paymentProgress(totalPrice = 25_000.0, balanceRemaining = 15_000.0, discount = 0.0)
        assertEquals(10_000.0, p.paid)
        assertEquals(0.4f, p.fraction)
    }

    @Test
    fun progressFullyPaidOrderIsOne() {
        val p = paymentProgress(totalPrice = 25_000.0, balanceRemaining = 0.0, discount = 0.0)
        assertEquals(25_000.0, p.paid)
        assertEquals(1f, p.fraction)
    }

    @Test
    fun progressUsesNetTotalAfterDiscount() {
        // A ₦5,000 discount lowers the net total; paying it all off is 100% even though
        // the gross price is higher.
        val p = paymentProgress(totalPrice = 25_000.0, balanceRemaining = 0.0, discount = 5_000.0)
        assertEquals(20_000.0, p.netTotal)
        assertEquals(20_000.0, p.paid)
        assertEquals(1f, p.fraction)
    }

    @Test
    fun progressClampsOverpaymentAndNegativeNet() {
        // Balance above net (data skew) can't drive paid negative; a fully-discounted
        // order has no net total, so progress is zero rather than NaN.
        assertEquals(0.0, paymentProgress(10_000.0, 12_000.0, 0.0).paid)
        assertEquals(0f, paymentProgress(10_000.0, 0.0, 10_000.0).fraction)
    }

    @Test
    fun capDigitsUnderBalancePassesThrough() {
        assertEquals("5000", capPaymentDigits("5000", 10_000.0))
    }

    @Test
    fun capDigitsOverBalanceReducesToWholeBalance() {
        assertEquals("10000", capPaymentDigits("99999", 10_000.0))
    }

    @Test
    fun capDigitsTrimsLeadingZeros() {
        assertEquals("500", capPaymentDigits("000500", 10_000.0))
    }

    @Test
    fun capDigitsEmptyWhenOnlyZeros() {
        assertEquals("", capPaymentDigits("000", 10_000.0))
    }

    @Test
    fun capDigitsWithZeroBalancePassesThrough() {
        // When balance is already zero, capping is a no-op so the dialog can still echo
        // user input for an order that was already fully paid (edge case).
        assertEquals("1234", capPaymentDigits("1234", 0.0))
    }
}

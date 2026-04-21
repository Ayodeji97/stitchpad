package com.danzucker.stitchpad.feature.order.presentation.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMathTest {

    @Test
    fun partialPaymentIncrementsDeposit() {
        val result = computeRecordedPayment(
            currentDeposit = 30_000.0,
            totalPrice = 100_000.0,
            amountJustPaid = 20_000.0,
        )
        assertEquals(50_000.0, result.newDeposit)
        assertEquals(50_000.0, result.newBalance)
    }

    @Test
    fun paymentEqualToBalanceFullyPaysOrder() {
        val result = computeRecordedPayment(
            currentDeposit = 30_000.0,
            totalPrice = 100_000.0,
            amountJustPaid = 70_000.0,
        )
        assertEquals(100_000.0, result.newDeposit)
        assertEquals(0.0, result.newBalance)
    }

    @Test
    fun paymentExceedingTotalClampsToTotal() {
        val result = computeRecordedPayment(
            currentDeposit = 30_000.0,
            totalPrice = 100_000.0,
            amountJustPaid = 999_999.0,
        )
        assertEquals(100_000.0, result.newDeposit)
        assertEquals(0.0, result.newBalance)
    }

    @Test
    fun zeroPaymentLeavesValuesUntouched() {
        val result = computeRecordedPayment(
            currentDeposit = 30_000.0,
            totalPrice = 100_000.0,
            amountJustPaid = 0.0,
        )
        assertEquals(30_000.0, result.newDeposit)
        assertEquals(70_000.0, result.newBalance)
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

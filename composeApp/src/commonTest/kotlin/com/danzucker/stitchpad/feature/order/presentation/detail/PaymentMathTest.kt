package com.danzucker.stitchpad.feature.order.presentation.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMathTest {

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

package com.danzucker.stitchpad.core.sharing

import kotlin.test.Test
import kotlin.test.assertEquals

class PriceFormatterTest {

    @Test
    fun wholeNumberFormatsWithoutDecimals() {
        assertEquals("5,000", formatPrice(5000.0))
        assertEquals("15,000", formatPrice(15000.0))
        assertEquals("1,000,000", formatPrice(1000000.0))
    }

    @Test
    fun decimalFormatsToTwoPlaces() {
        assertEquals("5,000.50", formatPrice(5000.5))
        assertEquals("1,234.56", formatPrice(1234.56))
    }

    @Test
    fun zeroFormatsCorrectly() {
        assertEquals("0", formatPrice(0.0))
    }

    @Test
    fun smallNumberNoSeparator() {
        assertEquals("500", formatPrice(500.0))
        assertEquals("99", formatPrice(99.0))
    }

    @Test
    fun negativeNumber() {
        assertEquals("-1,500", formatPrice(-1500.0))
        assertEquals("-500.75", formatPrice(-500.75))
    }

    @Test
    fun singleDecimalPadded() {
        assertEquals("1,000.50", formatPrice(1000.5))
    }
}

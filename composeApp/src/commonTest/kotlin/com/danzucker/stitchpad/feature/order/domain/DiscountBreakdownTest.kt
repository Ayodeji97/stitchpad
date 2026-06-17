package com.danzucker.stitchpad.feature.order.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscountBreakdownTest {

    @Test
    fun noDiscountString() {
        val b = discountBreakdown(subtotal = 108_000.0, discountInput = "")
        assertEquals(0.0, b.amount)
        assertEquals(0, b.percent)
        assertEquals(108_000.0, b.payable)
    }

    @Test
    fun zeroDiscount() {
        val b = discountBreakdown(subtotal = 108_000.0, discountInput = "0")
        assertEquals(0.0, b.amount)
        assertEquals(108_000.0, b.payable)
    }

    @Test
    fun normalDiscountRoundsPercent() {
        val b = discountBreakdown(subtotal = 108_000.0, discountInput = "20000")
        assertEquals(20_000.0, b.amount)
        assertEquals(19, b.percent)
        assertEquals(88_000.0, b.payable)
    }

    @Test
    fun discountEqualsSubtotalIsFreeOrder() {
        val b = discountBreakdown(subtotal = 30_000.0, discountInput = "30000")
        assertEquals(30_000.0, b.amount)
        assertEquals(100, b.percent)
        assertEquals(0.0, b.payable)
    }

    @Test
    fun discountOverSubtotalKeepsRawAmountAndFloorsPayable() {
        val b = discountBreakdown(subtotal = 30_000.0, discountInput = "50000")
        assertEquals(50_000.0, b.amount)
        assertEquals(0.0, b.payable)
        assertEquals(167, b.percent)
    }

    @Test
    fun zeroSubtotalNoDivideByZero() {
        val b = discountBreakdown(subtotal = 0.0, discountInput = "5000")
        assertEquals(0, b.percent)
        assertEquals(0.0, b.payable)
    }
}

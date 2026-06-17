package com.danzucker.stitchpad.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderTest {

    private fun order(totalPrice: Double, discount: Double = 0.0, deposit: Double = 0.0) = Order(
        id = "o1", userId = "u1", customerId = "c1", customerName = "Mummy AY",
        items = emptyList(), status = OrderStatus.PENDING, priority = OrderPriority.NORMAL,
        statusHistory = emptyList(), totalPrice = totalPrice, discount = discount,
        payments = if (deposit > 0) listOf(
            Payment("p1", deposit, PaymentMethod.CASH, PaymentType.DEPOSIT, 0L, null)
        ) else emptyList(),
        deadline = null, notes = null, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `no discount keeps payableTotal equal to totalPrice`() {
        assertEquals(32_500.0, order(totalPrice = 32_500.0).payableTotal)
    }

    @Test
    fun `payableTotal subtracts discount`() {
        assertEquals(30_000.0, order(totalPrice = 32_500.0, discount = 2_500.0).payableTotal)
    }

    @Test
    fun `discount larger than subtotal floors payableTotal at zero`() {
        assertEquals(0.0, order(totalPrice = 5_000.0, discount = 9_000.0).payableTotal)
    }

    @Test
    fun `balance is payableTotal minus deposit`() {
        assertEquals(20_000.0, order(totalPrice = 32_500.0, discount = 2_500.0, deposit = 10_000.0).balanceRemaining)
    }

    @Test
    fun `deposit covering discounted total clears balance`() {
        assertEquals(0.0, order(totalPrice = 32_500.0, discount = 2_500.0, deposit = 30_000.0).balanceRemaining)
    }
}

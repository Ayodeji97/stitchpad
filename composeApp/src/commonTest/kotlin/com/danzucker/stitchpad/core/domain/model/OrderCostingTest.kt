package com.danzucker.stitchpad.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderCostingTest {

    private fun order(
        totalPrice: Double = 50_000.0,
        discount: Double = 0.0,
        costs: List<OrderCost> = emptyList(),
    ) = Order(
        id = "o1", userId = "u1", customerId = "c1", customerName = "Amaka",
        items = emptyList(), status = OrderStatus.PENDING, priority = OrderPriority.NORMAL,
        statusHistory = emptyList(), totalPrice = totalPrice, discount = discount,
        costs = costs, deadline = null, notes = null, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `totalCost sums all cost amounts`() {
        val o = order(costs = listOf(
            OrderCost(CostCategory.FABRIC, 25_000.0),
            OrderCost(CostCategory.LABOUR, 7_000.0),
        ))
        assertEquals(32_000.0, o.totalCost)
    }

    @Test
    fun `profit is payableTotal minus totalCost`() {
        val o = order(totalPrice = 50_000.0, costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0)))
        assertEquals(6_000.0, o.profit)
    }

    @Test
    fun `profit uses payableTotal net of discount`() {
        val o = order(totalPrice = 50_000.0, discount = 10_000.0,
            costs = listOf(OrderCost(CostCategory.FABRIC, 30_000.0)))
        assertEquals(10_000.0, o.profit) // (50k-10k) - 30k
    }

    @Test
    fun `profit is negative when costs exceed payable`() {
        val o = order(totalPrice = 50_000.0, costs = listOf(OrderCost(CostCategory.FABRIC, 58_000.0)))
        assertEquals(-8_000.0, o.profit)
    }

    @Test
    fun `profitMargin is profit over payableTotal`() {
        val o = order(totalPrice = 50_000.0, costs = listOf(OrderCost(CostCategory.FABRIC, 44_000.0)))
        assertEquals(6_000.0 / 50_000.0, o.profitMargin)
    }

    @Test
    fun `profitMargin is null when payableTotal is zero`() {
        val o = order(totalPrice = 0.0, costs = listOf(OrderCost(CostCategory.FABRIC, 1_000.0)))
        assertNull(o.profitMargin)
    }

    @Test
    fun `hasCosts reflects presence of cost lines`() {
        assertFalse(order().hasCosts)
        assertTrue(order(costs = listOf(OrderCost(CostCategory.OTHER, 500.0))).hasCosts)
    }
}

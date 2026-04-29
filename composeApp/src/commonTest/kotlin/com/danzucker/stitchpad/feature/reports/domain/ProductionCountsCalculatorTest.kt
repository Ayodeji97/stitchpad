package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductionCountsCalculatorTest {

    private fun order(id: String, status: OrderStatus): Order = Order(
        id = id,
        userId = "u",
        customerId = "c1",
        customerName = "Test",
        items = listOf(OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = 0.0)),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        depositPaid = 0.0,
        balanceRemaining = 0.0,
        deadline = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun emptyOrdersGivesAllZeroes() {
        val counts = ProductionCountsCalculator.compute(emptyList())
        assertEquals(0, counts.pending)
        assertEquals(0, counts.inProgress)
        assertEquals(0, counts.ready)
        assertEquals(0, counts.delivered)
        assertEquals(0, counts.total)
    }

    @Test
    fun groupsOrdersByOrderStatus() {
        val orders = listOf(
            order("a", OrderStatus.PENDING),
            order("b", OrderStatus.PENDING),
            order("c", OrderStatus.IN_PROGRESS),
            order("d", OrderStatus.READY),
            order("e", OrderStatus.READY),
            order("f", OrderStatus.READY),
            order("g", OrderStatus.DELIVERED)
        )
        val counts = ProductionCountsCalculator.compute(orders)
        assertEquals(2, counts.pending)
        assertEquals(1, counts.inProgress)
        assertEquals(3, counts.ready)
        assertEquals(1, counts.delivered)
        assertEquals(7, counts.total)
    }
}

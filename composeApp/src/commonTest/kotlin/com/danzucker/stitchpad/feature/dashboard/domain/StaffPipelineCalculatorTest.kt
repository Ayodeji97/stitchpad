package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffPipelineCalculatorTest {

    private fun order(
        id: String = "o",
        status: OrderStatus = OrderStatus.PENDING,
        subStatus: OrderSubStatus? = null,
    ): Order = Order(
        id = id,
        userId = "u",
        customerId = "c1",
        customerName = "Test",
        items = listOf(OrderItem(id = "i-$id", garmentType = GarmentType.AGBADA, description = "", price = 0.0)),
        status = status,
        subStatus = subStatus,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        deadline = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun emptyOrdersProduceEmptyCounts() {
        val counts = StaffPipelineCalculator.compute(emptyList())
        assertEquals(StaffPipelineCounts(), counts)
        assertTrue(counts.isEmpty)
        assertEquals(0, counts.inProgressTotal)
    }

    @Test
    fun inProgressOrdersGroupBySubStatus() {
        val counts = StaffPipelineCalculator.compute(
            listOf(
                order("a", OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
                order("b", OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                order("c", OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                order("d", OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
            )
        )
        assertEquals(StaffPipelineCounts(cutting = 1, sewing = 2, fitting = 1, ready = 0), counts)
        assertEquals(4, counts.inProgressTotal)
        assertFalse(counts.isEmpty)
    }

    @Test
    fun inProgressWithNullSubStatusCountsAsCutting() {
        val counts = StaffPipelineCalculator.compute(
            listOf(order("a", OrderStatus.IN_PROGRESS, subStatus = null))
        )
        assertEquals(1, counts.cutting)
        assertEquals(0, counts.sewing)
        assertEquals(0, counts.fitting)
    }

    @Test
    fun readyOrdersCountSeparatelyFromInProgress() {
        val counts = StaffPipelineCalculator.compute(
            listOf(
                order("a", OrderStatus.READY),
                order("b", OrderStatus.READY),
                order("c", OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            )
        )
        assertEquals(2, counts.ready)
        assertEquals(1, counts.inProgressTotal)
    }

    @Test
    fun pendingAndDeliveredOrdersDoNotContribute() {
        val counts = StaffPipelineCalculator.compute(
            listOf(
                order("a", OrderStatus.PENDING),
                order("b", OrderStatus.DELIVERED),
            )
        )
        assertEquals(StaffPipelineCounts(), counts)
        assertTrue(counts.isEmpty)
    }
}

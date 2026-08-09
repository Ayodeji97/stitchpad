package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamWorkloadTest {

    private fun order(
        id: String,
        assignedMemberId: String?,
        status: OrderStatus,
        archivedAt: Long? = null,
    ) = Order(
        id = id,
        userId = "u",
        customerId = "c",
        customerName = "C",
        items = listOf(OrderItem(id = "i", garmentType = GarmentType.SUIT, description = "", price = 0.0)),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        deadline = null,
        notes = null,
        archivedAt = archivedAt,
        assignedMemberId = assignedMemberId,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun countsOpenOrdersPerAssignee_excludingDelivered() {
        val orders = listOf(
            order(id = "1", assignedMemberId = "a", status = OrderStatus.PENDING),
            order(id = "2", assignedMemberId = "a", status = OrderStatus.DELIVERED), // excluded
            order(id = "3", assignedMemberId = null, status = OrderStatus.READY),
            order(id = "4", assignedMemberId = "b", status = OrderStatus.IN_PROGRESS),
        )
        assertEquals(mapOf<String?, Int>("a" to 1, null to 1, "b" to 1), openOrderCountsByAssignee(orders))
    }

    @Test
    fun archivedOrdersAreExcludedEvenIfNotDelivered() {
        val orders = listOf(
            order(id = "1", assignedMemberId = "a", status = OrderStatus.PENDING, archivedAt = 12345L),
            order(id = "2", assignedMemberId = "a", status = OrderStatus.PENDING),
        )
        assertEquals(mapOf<String?, Int>("a" to 1), openOrderCountsByAssignee(orders))
    }

    @Test
    fun emptyOrdersProducesEmptyMap() {
        assertEquals(emptyMap(), openOrderCountsByAssignee(emptyList()))
    }
}

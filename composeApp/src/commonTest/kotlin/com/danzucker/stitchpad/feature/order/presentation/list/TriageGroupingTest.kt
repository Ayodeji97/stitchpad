package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriageGroupingTest {

    private val now = 1_700_000_000_000L // fixed reference
    private val oneDay = 24L * 60 * 60 * 1000

    private fun order(
        id: String,
        status: OrderStatus = OrderStatus.PENDING,
        deadline: Long? = null,
        createdAt: Long = 0L
    ) = Order(
        id = id,
        userId = "u", customerId = "c", customerName = "C",
        items = listOf(OrderItem(id = "i", garmentType = GarmentType.SUIT, description = "", price = 0.0)),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        depositPaid = 0.0,
        balanceRemaining = 0.0,
        deadline = deadline,
        notes = null,
        createdAt = createdAt,
        updatedAt = 0L
    )

    @Test
    fun emptyListReturnsEmptyMap() {
        val result = groupOrdersIntoTriage(emptyList(), now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun deliveredOrdersAreHidden() {
        val delivered = order("d", status = OrderStatus.DELIVERED, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(delivered), now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun readyOrdersGoToReadyForPickupEvenIfOverdue() {
        val ready = order("r", status = OrderStatus.READY, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(ready), now)
        assertEquals(listOf(ready), result[TriageGroup.READY_FOR_PICKUP])
        assertEquals(null, result[TriageGroup.OVERDUE])
    }

    @Test
    fun pastDeadlinePendingGoesToOverdue() {
        val overdue = order("o", status = OrderStatus.PENDING, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(overdue), now)
        assertEquals(listOf(overdue), result[TriageGroup.OVERDUE])
    }

    @Test
    fun pastDeadlineInProgressGoesToOverdue() {
        val overdue = order("o", status = OrderStatus.IN_PROGRESS, deadline = now - oneDay)
        val result = groupOrdersIntoTriage(listOf(overdue), now)
        assertEquals(listOf(overdue), result[TriageGroup.OVERDUE])
    }

    @Test
    fun inProgressWithFutureDeadlineGoesToInProgress() {
        val ip = order("ip", status = OrderStatus.IN_PROGRESS, deadline = now + 3 * oneDay)
        val result = groupOrdersIntoTriage(listOf(ip), now)
        assertEquals(listOf(ip), result[TriageGroup.IN_PROGRESS])
    }

    @Test
    fun pendingWithinSevenDaysGoesToDueThisWeek() {
        val soon = order("s", status = OrderStatus.PENDING, deadline = now + 3 * oneDay)
        val result = groupOrdersIntoTriage(listOf(soon), now)
        assertEquals(listOf(soon), result[TriageGroup.DUE_THIS_WEEK])
    }

    @Test
    fun pendingExactlyAtSevenDaysGoesToDueThisWeek() {
        val sevenDays = order("s", status = OrderStatus.PENDING, deadline = now + 7 * oneDay)
        val result = groupOrdersIntoTriage(listOf(sevenDays), now)
        assertEquals(listOf(sevenDays), result[TriageGroup.DUE_THIS_WEEK])
    }

    @Test
    fun pendingBeyondSevenDaysGoesToPending() {
        val far = order("f", status = OrderStatus.PENDING, deadline = now + 14 * oneDay)
        val result = groupOrdersIntoTriage(listOf(far), now)
        assertEquals(listOf(far), result[TriageGroup.PENDING])
    }

    @Test
    fun pendingWithNoDeadlineGoesToPending() {
        val noDl = order("n", status = OrderStatus.PENDING, deadline = null)
        val result = groupOrdersIntoTriage(listOf(noDl), now)
        assertEquals(listOf(noDl), result[TriageGroup.PENDING])
    }

    @Test
    fun withinGroupSortedByDeadlineAscendingNullsLast() {
        val a = order("a", status = OrderStatus.PENDING, deadline = null)
        val b = order("b", status = OrderStatus.PENDING, deadline = now + 14 * oneDay)
        val c = order("c", status = OrderStatus.PENDING, deadline = now + 10 * oneDay)
        val result = groupOrdersIntoTriage(listOf(a, b, c), now)
        assertEquals(listOf(c, b, a), result[TriageGroup.PENDING])
    }

    @Test
    fun emptyGroupsAreOmitted() {
        val pending = order("p", status = OrderStatus.PENDING, deadline = null)
        val result = groupOrdersIntoTriage(listOf(pending), now)
        assertTrue(TriageGroup.OVERDUE !in result)
        assertTrue(TriageGroup.IN_PROGRESS !in result)
        assertTrue(TriageGroup.READY_FOR_PICKUP !in result)
        assertTrue(TriageGroup.DUE_THIS_WEEK !in result)
    }

    @Test
    fun resultIsInDisplayOrder() {
        val overdue = order("o", status = OrderStatus.PENDING, deadline = now - oneDay)
        val ready = order("r", status = OrderStatus.READY, deadline = null)
        val pending = order("p", status = OrderStatus.PENDING, deadline = null)
        val inProg = order("ip", status = OrderStatus.IN_PROGRESS, deadline = now + 30 * oneDay)
        val result = groupOrdersIntoTriage(listOf(pending, ready, inProg, overdue), now)
        assertEquals(
            listOf(TriageGroup.OVERDUE, TriageGroup.IN_PROGRESS, TriageGroup.READY_FOR_PICKUP, TriageGroup.PENDING),
            result.keys.toList()
        )
    }
}

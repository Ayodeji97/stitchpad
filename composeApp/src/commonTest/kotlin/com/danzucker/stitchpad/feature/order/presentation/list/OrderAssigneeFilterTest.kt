package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [assigneeFilterLabelName] backs [OrderListState.assigneeFilterName] — the text the
 * Task 8 assignee chip renders ("Assigned to <name>"). Kept as a pure function (a
 * sibling of [allChipSelected] in OrderChipSelection.kt) so it's testable without
 * spinning up the ViewModel.
 */
class OrderAssigneeFilterTest {

    private fun fakeOrder(
        id: String,
        assignedMemberId: String? = null,
        assignedMemberName: String? = null,
    ) = Order(
        id = id,
        userId = "test-uid",
        customerId = "c1",
        customerName = "Ada Lovelace",
        items = listOf(OrderItem(id = "i-$id", garmentType = GarmentType.SUIT, description = "", price = 0.0)),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        deadline = null,
        notes = null,
        assignedMemberId = assignedMemberId,
        assignedMemberName = assignedMemberName,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun matchingOrderFound_returnsAssignedMemberName() {
        val orders = listOf(
            fakeOrder(id = "o1", assignedMemberId = "m1", assignedMemberName = "Musa"),
            fakeOrder(id = "o2", assignedMemberId = "other", assignedMemberName = "Other"),
        )

        assertEquals("Musa", assigneeFilterLabelName(orders, "m1"))
    }

    @Test
    fun noMatchingOrderYet_fallsBackToRawId() {
        // A cold deep link can render before the first Firestore snapshot with a
        // matching row has arrived — fall back to the id rather than a blank label.
        val orders = listOf(fakeOrder(id = "o1", assignedMemberId = "other", assignedMemberName = "Other"))

        assertEquals("m1", assigneeFilterLabelName(orders, "m1"))
    }

    @Test
    fun emptyOrders_fallsBackToRawId() {
        assertEquals("m1", assigneeFilterLabelName(emptyList(), "m1"))
    }

    @Test
    fun unassignedFilter_returnsNull_regardlessOfOrders() {
        // "none" has no name to look up — the Screen renders order_filter_unassigned
        // for this case instead of formatting a name, so this must be null, not "none".
        val orders = listOf(fakeOrder(id = "o1", assignedMemberId = "m1", assignedMemberName = "Musa"))

        assertNull(assigneeFilterLabelName(orders, OrderListFilter.ASSIGNEE_NONE_ID))
    }

    @Test
    fun matchingOrderHasNullName_fallsBackToRawId() {
        // Defensive: an assigned order whose name never got denormalized shouldn't
        // surface a null/blank chip label either.
        val orders = listOf(fakeOrder(id = "o1", assignedMemberId = "m1", assignedMemberName = null))

        assertEquals("m1", assigneeFilterLabelName(orders, "m1"))
    }
}

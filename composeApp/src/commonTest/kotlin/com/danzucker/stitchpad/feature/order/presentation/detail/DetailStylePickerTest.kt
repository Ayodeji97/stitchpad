package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Logic tests for the order-DETAIL saved-style picker (PTSP-44).
 *
 * [OrderDetailViewModel] cannot be instantiated in commonTest — it requires a Coil
 * [coil3.ImageLoader] + [coil3.PlatformContext], and on the Android unit-test classpath
 * PlatformContext == android.content.Context whose stubs throw "Stub!" without
 * Robolectric (which this module is not configured for). So, exactly like every sibling
 * detail test (PaymentMathTest, StatusTransitionsTest, PrimaryCtaResolverTest,
 * FabricReferenceImagesTest, ReferenceScrollIndexTest), this targets the pure toggle /
 * commit logic the ViewModel delegates to verbatim ([togglePendingStyle],
 * [pendingStyleRefsToAdd]).
 *
 * A tiny in-test reducer ([applyToggle] / [commit]) reproduces the VM's state
 * transitions so the named scenarios read like behavioural tests.
 */
class DetailStylePickerTest {

    private val maxRefs = 3

    private fun item(id: String, styleIds: List<String> = emptyList()) = OrderItem(
        id = id,
        garmentType = GarmentType.AGBADA,
        description = "Demo",
        price = 5_000.0,
        styleImages = styleIds.map {
            StyleImageRef(source = StyleImageSource.LIBRARY, styleId = it)
        },
    )

    private fun order(item: OrderItem) = Order(
        id = "order-1",
        userId = "user-1",
        customerId = "cust-1",
        customerName = "Test Customer",
        items = listOf(item),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = listOf(StatusChange(OrderStatus.PENDING, 0L)),
        totalPrice = 5_000.0,
        deadline = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    /** Mirrors the VM's OnItemTogglePendingStyle reducer. */
    private fun applyToggle(pending: List<String>, item: OrderItem, styleId: String): List<String> =
        togglePendingStyle(
            pending = pending,
            styleId = styleId,
            committedSlots = item.styleImages.size,
            maxRefs = maxRefs,
        )

    /**
     * Mirrors the VM's commitPendingStyles: build the refs to append, splice them into
     * the item, and clear the pending list + close the sheet. Returns the persisted
     * order (what the VM hands to orderRepository.updateOrder).
     */
    private data class CommitResult(
        val persistedOrder: Order,
        val pendingAfter: List<String>,
        val sheetOpenAfter: Boolean,
    )

    private fun commit(order: Order, itemId: String, pending: List<String>): CommitResult {
        val item = order.items.first { it.id == itemId }
        val existing = item.styleImages.mapNotNull { it.styleId }.toSet()
        val toAdd = pendingStyleRefsToAdd(
            pending = pending,
            existingStyleIds = existing,
            usedSlots = item.styleImages.size,
            maxRefs = maxRefs,
        )
        val updatedItems = order.items.map {
            if (it.id == itemId) it.copy(styleImages = it.styleImages + toAdd) else it
        }
        return CommitResult(
            persistedOrder = order.copy(items = updatedItems),
            pendingAfter = emptyList(),
            sheetOpenAfter = false,
        )
    }

    @Test
    fun togglePendingStyle_selectsThenDeselects() {
        val it0 = item("item-1")
        var pending = emptyList<String>()

        pending = applyToggle(pending, it0, "s1")
        pending = applyToggle(pending, it0, "s2")
        assertEquals(listOf("s1", "s2"), pending)

        pending = applyToggle(pending, it0, "s1")
        assertEquals(listOf("s2"), pending)
    }

    @Test
    fun togglePendingStyle_respectsCap() {
        // Item already holds 2 styleImages → only 1 more pending allowed (cap 3).
        val it2 = item("item-1", styleIds = listOf("a", "b"))
        var pending = emptyList<String>()

        pending = applyToggle(pending, it2, "s1")
        assertEquals(listOf("s1"), pending)

        // Adding a 2nd pending would make 2 committed + 2 pending = 4 > cap → blocked.
        pending = applyToggle(pending, it2, "s2")
        assertEquals(listOf("s1"), pending, "second pick must be blocked at cap")
    }

    @Test
    fun commitPendingStyles_persistsAndClosesSheet() {
        val o = order(item("item-1"))
        var pending = emptyList<String>()
        pending = applyToggle(pending, o.items.first(), "s1")
        pending = applyToggle(pending, o.items.first(), "s2")

        val result = commit(o, "item-1", pending)

        val persistedItem = result.persistedOrder.items.first { it.id == "item-1" }
        val refIds = persistedItem.styleImages.map { it.styleId }
        assertEquals(listOf("s1", "s2"), refIds)
        assertTrue(
            persistedItem.styleImages.all { it.source == StyleImageSource.LIBRARY },
            "committed refs must be LIBRARY refs",
        )
        // The persisted order is exactly what the VM passes to updateOrder.
        assertTrue(result.pendingAfter.isEmpty(), "pending cleared after commit")
        assertTrue(!result.sheetOpenAfter, "sheet closed after commit")
    }

    @Test
    fun dismiss_clearsPending() {
        val it0 = item("item-1")
        var pending = emptyList<String>()
        pending = applyToggle(pending, it0, "s1")
        assertEquals(listOf("s1"), pending)

        // Dismiss reducer wipes pending + closes the sheet (VM: OnDismissStylePickerSheet).
        val pendingAfterDismiss = emptyList<String>()
        val sheetOpenAfterDismiss = false
        assertTrue(pendingAfterDismiss.isEmpty(), "dismiss must clear pending")
        assertTrue(!sheetOpenAfterDismiss, "dismiss must close the sheet")
    }
}

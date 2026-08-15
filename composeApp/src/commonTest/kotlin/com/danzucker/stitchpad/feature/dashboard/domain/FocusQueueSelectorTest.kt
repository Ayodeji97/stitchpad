package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val VIEWER = "staff-uid"

class FocusQueueSelectorTest {

    private fun row(
        orderId: String,
        assignedMemberId: String? = VIEWER,
        stage: PipelineStage? = PipelineStage.CUTTING,
        daysLate: Int? = null,
        daysUntilDeadline: Int? = null,
        createdAtEpochMillis: Long = 0L,
    ) = DashboardOrderRow(
        orderId = orderId,
        customerName = "Customer $orderId",
        primaryLabel = "Garment",
        daysLate = daysLate,
        daysUntilDeadline = daysUntilDeadline,
        createdAtEpochMillis = createdAtEpochMillis,
        assignedMemberId = assignedMemberId,
        stage = stage,
    )

    @Test
    fun noAssignedOpenOrders_heroIsNull() {
        val queue = computeFocusQueue(rows = emptyList(), viewerMemberId = VIEWER)

        assertNull(queue.hero)
        assertEquals(emptyList(), queue.thenQueue)
    }

    @Test
    fun onlyOtherMembersOrders_heroIsNull() {
        val rows = listOf(row(orderId = "o1", assignedMemberId = "other-uid"))

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertNull(queue.hero)
    }

    @Test
    fun readyOrdersExcludedFromHeroAndQueue() {
        val rows = listOf(
            row(orderId = "ready1", stage = PipelineStage.READY, daysLate = 5),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertNull(queue.hero)
        assertEquals(emptyList(), queue.thenQueue)
    }

    @Test
    fun lateBeatsSoonAndOk() {
        val rows = listOf(
            row(orderId = "onTrack", daysUntilDeadline = null),
            row(orderId = "soon", daysUntilDeadline = 2),
            row(orderId = "late", daysLate = 1),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("late", queue.hero?.orderId)
        assertEquals(listOf("soon", "onTrack"), queue.thenQueue.map { it.orderId })
    }

    @Test
    fun soonBeatsOk() {
        val rows = listOf(
            row(orderId = "onTrack", daysUntilDeadline = null),
            row(orderId = "soon", daysUntilDeadline = 1),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("soon", queue.hero?.orderId)
        assertEquals(listOf("onTrack"), queue.thenQueue.map { it.orderId })
    }

    @Test
    fun moreDaysLateSortsBeforeFewerDaysLate() {
        val rows = listOf(
            row(orderId = "late2", daysLate = 2),
            row(orderId = "late5", daysLate = 5),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("late5", queue.hero?.orderId)
        assertEquals(listOf("late2"), queue.thenQueue.map { it.orderId })
    }

    @Test
    fun soonestDeadlineSortsBeforeLaterDeadlineAmongNonLate() {
        val rows = listOf(
            row(orderId = "in3", daysUntilDeadline = 3),
            row(orderId = "in1", daysUntilDeadline = 1),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("in1", queue.hero?.orderId)
        assertEquals(listOf("in3"), queue.thenQueue.map { it.orderId })
    }

    @Test
    fun oldestCreatedAtBreaksTieAmongEquallyOnTrackOrders() {
        val rows = listOf(
            row(orderId = "newer", createdAtEpochMillis = 200L),
            row(orderId = "older", createdAtEpochMillis = 100L),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("older", queue.hero?.orderId)
        assertEquals(listOf("newer"), queue.thenQueue.map { it.orderId })
    }

    @Test
    fun unassignedRowsNeverBecomeHeroButAppearInShopQueue() {
        val rows = listOf(
            row(orderId = "mine", assignedMemberId = VIEWER),
            row(orderId = "unassigned1", assignedMemberId = null),
            row(orderId = "unassigned2", assignedMemberId = null, daysLate = 3),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("mine", queue.hero?.orderId)
        assertEquals(listOf("unassigned2", "unassigned1"), queue.shopQueue.map { it.orderId })
    }

    // Design review (PR #366): staff observe the WHOLE workshop, so a
    // teammate's order must still surface — in the shop queue, never in
    // "Then" (which is viewer-only) and never as the hero.
    @Test
    fun teammateOrdersNeverBecomeHeroOrThenButAppearInShopQueue() {
        val rows = listOf(
            row(orderId = "mine", assignedMemberId = VIEWER),
            row(orderId = "colleague", assignedMemberId = "other-uid"),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals("mine", queue.hero?.orderId)
        assertEquals(emptyList(), queue.thenQueue)
        assertEquals(listOf("colleague"), queue.shopQueue.map { it.orderId })
    }

    @Test
    fun shopQueueMixesUnassignedAndTeammateRowsInPriorityOrder() {
        val rows = listOf(
            row(orderId = "colleagueOnTrack", assignedMemberId = "other-uid"),
            row(orderId = "unassignedLate", assignedMemberId = null, daysLate = 4),
        )

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertEquals(listOf("unassignedLate", "colleagueOnTrack"), queue.shopQueue.map { it.orderId })
    }

    @Test
    fun nullViewerId_noHeroEvenIfRowsAreUnassigned() {
        val rows = listOf(row(orderId = "o1", assignedMemberId = null))

        val queue = computeFocusQueue(rows, viewerMemberId = null)

        assertNull(queue.hero)
        assertEquals(listOf("o1"), queue.shopQueue.map { it.orderId })
    }

    @Test
    fun nullViewerId_teammateRowsStillAppearInShopQueue() {
        val rows = listOf(row(orderId = "o1", assignedMemberId = "other-uid"))

        val queue = computeFocusQueue(rows, viewerMemberId = null)

        assertNull(queue.hero)
        assertEquals(listOf("o1"), queue.shopQueue.map { it.orderId })
    }

    @Test
    fun rowsWithNullStageAreExcludedEntirely() {
        val rows = listOf(row(orderId = "o1", stage = null))

        val queue = computeFocusQueue(rows, viewerMemberId = VIEWER)

        assertNull(queue.hero)
        assertEquals(emptyList(), queue.shopQueue)
    }
}

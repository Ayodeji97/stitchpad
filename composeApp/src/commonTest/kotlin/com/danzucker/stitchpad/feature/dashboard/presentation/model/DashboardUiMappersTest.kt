package com.danzucker.stitchpad.feature.dashboard.presentation.model

import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardUiMappersTest {

    // — helpers —

    private fun row(
        id: String = "o",
        customerName: String = "Test Customer",
        primaryLabel: String = "Senator",
        daysLate: Int? = null,
        daysUntilDeadline: Int? = null,
    ) = DashboardOrderRow(
        orderId = id,
        customerName = customerName,
        primaryLabel = primaryLabel,
        daysLate = daysLate,
        daysUntilDeadline = daysUntilDeadline,
    )

    // — buildTodayWorkRows —

    @Test
    fun buildTodayWorkRowsReturnsEmptyWhenAllBucketsEmpty() {
        val rows = buildTodayWorkRows(
            overdue = emptyList(),
            dueToday = emptyList(),
            ready = emptyList(),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun buildTodayWorkRowsOrdersByPriorityOverdueFirst() {
        val overdue = listOf(row(id = "ovd", daysLate = 2))
        val dueToday = listOf(row(id = "due"))
        val ready = listOf(row(id = "rdy"))

        val rows = buildTodayWorkRows(overdue = overdue, dueToday = dueToday, ready = ready)

        assertEquals(listOf("ovd", "due", "rdy"), rows.map { it.orderId })
    }

    @Test
    fun buildTodayWorkRowsCapsAtFiveRows() {
        // 3 overdue + 2 dueToday + 2 ready = 7 total, should be capped at 5
        val overdue = listOf(row("o1", daysLate = 3), row("o2", daysLate = 2), row("o3", daysLate = 1))
        val dueToday = listOf(row("d1"), row("d2"))
        val ready = listOf(row("r1"), row("r2"))

        val rows = buildTodayWorkRows(overdue = overdue, dueToday = dueToday, ready = ready)

        assertEquals(5, rows.size)
        // First 3 should come from overdue, next 2 from dueToday
        assertEquals(listOf("o1", "o2", "o3", "d1", "d2"), rows.map { it.orderId })
    }

    @Test
    fun buildTodayWorkRowsOverdueBucketAloneRespectsCap() {
        val overdue = (1..8).map { row("o$it", daysLate = it) }

        val rows = buildTodayWorkRows(overdue = overdue, dueToday = emptyList(), ready = emptyList())

        assertEquals(5, rows.size)
        assertEquals((1..5).map { "o$it" }, rows.map { it.orderId })
    }

    @Test
    fun buildTodayWorkRowsChipTextForOverdueWithDaysLate() {
        val rows = buildTodayWorkRows(
            overdue = listOf(row("o1", daysLate = 3)),
            dueToday = emptyList(),
            ready = emptyList(),
        )
        assertEquals("3d late", rows.single().chipText)
    }

    @Test
    fun buildTodayWorkRowsChipTextForOverdueFallbackWhenNoDaysLate() {
        val rows = buildTodayWorkRows(
            overdue = listOf(row("o1", daysLate = null)),
            dueToday = emptyList(),
            ready = emptyList(),
        )
        assertEquals("Overdue", rows.single().chipText)
    }

    @Test
    fun buildTodayWorkRowsChipTextForDueToday() {
        val rows = buildTodayWorkRows(
            overdue = emptyList(),
            dueToday = listOf(row("d1")),
            ready = emptyList(),
        )
        assertEquals("Due today", rows.single().chipText)
    }

    @Test
    fun buildTodayWorkRowsChipTextForReady() {
        val rows = buildTodayWorkRows(
            overdue = emptyList(),
            dueToday = emptyList(),
            ready = listOf(row("r1")),
        )
        assertEquals("Ready", rows.single().chipText)
    }

    @Test
    fun buildTodayWorkRowsPreservesCustomerNameAndPrimaryLabel() {
        val rows = buildTodayWorkRows(
            overdue = listOf(row("o1", customerName = "Ada Obi", primaryLabel = "Bridal Gown", daysLate = 1)),
            dueToday = emptyList(),
            ready = emptyList(),
        )
        val single = rows.single()
        assertEquals("Ada Obi", single.customerName)
        assertEquals("Bridal Gown", single.primaryLabel)
    }

}

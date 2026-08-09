package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 2a smoke finding: landing on the Orders list via the "Mine" dashboard tile seeds
 * [OrderListState.myWorkOnly] = true with [OrderListState.statusFilter] = null. The "All"
 * chip must not highlight in that state — "All" means NO filter (status, archived, or
 * myWorkOnly) is active.
 */
class OrderChipSelectionTest {

    @Test
    fun noFiltersActive_allChipSelected() {
        assertTrue(allChipSelected(showArchived = false, selectedStatus = null, myWorkOnly = false))
    }

    @Test
    fun seededMyWorkOnly_statusNull_allChipNotSelected() {
        // The bug this guards: dashboard "Mine" tile deep-link -> myWorkOnly = true,
        // statusFilter = null. Previously "All" highlighted alongside "My work".
        assertFalse(allChipSelected(showArchived = false, selectedStatus = null, myWorkOnly = true))
    }

    @Test
    fun statusFilterActive_allChipNotSelected() {
        assertFalse(
            allChipSelected(showArchived = false, selectedStatus = OrderStatus.PENDING, myWorkOnly = false)
        )
    }

    @Test
    fun archivedActive_allChipNotSelected() {
        assertFalse(allChipSelected(showArchived = true, selectedStatus = null, myWorkOnly = false))
    }

    @Test
    fun archivedAndMyWorkBothActive_allChipNotSelected() {
        assertFalse(allChipSelected(showArchived = true, selectedStatus = null, myWorkOnly = true))
    }

    @Test
    fun myWorkAndStatusBothActive_allChipNotSelected() {
        assertFalse(
            allChipSelected(showArchived = false, selectedStatus = OrderStatus.IN_PROGRESS, myWorkOnly = true)
        )
    }

    // --- Task 8: assignee: deep link — "All" must not highlight while an
    // assignee filter is active either, same reasoning as the myWorkOnly conjunct. ---

    @Test
    fun assigneeFilterActive_allChipNotSelected() {
        assertFalse(
            allChipSelected(
                showArchived = false,
                selectedStatus = null,
                myWorkOnly = false,
                assigneeFilter = "m1",
            )
        )
    }

    @Test
    fun assigneeFilterActive_withUnassignedValue_allChipNotSelected() {
        assertFalse(
            allChipSelected(
                showArchived = false,
                selectedStatus = null,
                myWorkOnly = false,
                assigneeFilter = "none",
            )
        )
    }

    @Test
    fun noAssigneeFilter_defaultParam_allChipSelected() {
        // assigneeFilter defaults to null so every pre-Task-8 call site (a bare
        // 3-arg call) keeps compiling and behaving exactly as before.
        assertTrue(allChipSelected(showArchived = false, selectedStatus = null, myWorkOnly = false))
    }
}

package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage

/**
 * Output of [computeFocusQueue] — the three sections the staff dashboard's
 * order-list portion renders below the pipeline bar.
 */
data class FocusQueue(
    /** The single highest-priority actionable order assigned to the viewer, or null. */
    val hero: DashboardOrderRow?,
    /** The viewer's remaining assigned open orders, same priority order, minus [hero]. */
    val thenQueue: List<DashboardOrderRow>,
    /**
     * Every other open order in the workshop, same priority order: unassigned
     * orders AND orders assigned to a teammate (not the viewer). Staff observe
     * the whole workshop, not just their own slice, so a teammate's order must
     * still be visible here — with the teammate's name on its assignee chip —
     * not silently dropped. Rendered at reduced opacity as one "rest of the
     * shop" queue.
     */
    val shopQueue: List<DashboardOrderRow>,
)

/**
 * Selects the staff dashboard focus-queue's hero + "Then" queue + "rest of the
 * shop" queue sections from [rows] (typically `Buckets.openQueue`).
 *
 * Candidates are rows with a non-null [DashboardOrderRow.stage] that isn't
 * [PipelineStage.READY] — a READY order is awaiting owner pickup, nothing
 * left for staff to advance, so it drops out of both the hero and the queue
 * the moment it gets there (a null `stage` — legacy call sites — is treated
 * the same as "not actionable" and dropped too).
 *
 * Priority order (both viewer-assigned and shop-queue lists): most days late
 * (descending) → soonest deadline (ascending) → oldest `createdAt` (ascending).
 * This is a single multi-key sort because `daysLate` is only ever positive
 * when the order is genuinely overdue (see `BucketCalculator.toQueueRow`), so
 * sorting by it descending naturally ranks every late order above every
 * non-late one before the softer deadline/age tie-breakers apply.
 */
fun computeFocusQueue(rows: List<DashboardOrderRow>, viewerMemberId: String?): FocusQueue {
    val candidates = rows.filter { it.stage != null && it.stage != PipelineStage.READY }
    val priorityOrder = compareByDescending<DashboardOrderRow> { it.daysLate ?: 0 }
        .thenBy { it.daysUntilDeadline ?: Int.MAX_VALUE }
        .thenBy { it.createdAtEpochMillis }
    val isViewerRow = { row: DashboardOrderRow ->
        row.assignedMemberId != null && row.assignedMemberId == viewerMemberId
    }

    val viewerRows = candidates.filter(isViewerRow).sortedWith(priorityOrder)
    // Everything NOT the viewer's own — unassigned rows AND teammate-assigned
    // rows both belong here, regardless of whether viewerMemberId itself is
    // resolved yet (an unassigned row must never be excluded just because we
    // don't yet know who's looking).
    val shopRows = candidates.filterNot(isViewerRow).sortedWith(priorityOrder)

    return FocusQueue(
        hero = viewerRows.firstOrNull(),
        thenQueue = viewerRows.drop(1),
        shopQueue = shopRows,
    )
}

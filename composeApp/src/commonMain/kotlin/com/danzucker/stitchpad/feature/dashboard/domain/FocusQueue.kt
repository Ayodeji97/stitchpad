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
    /** Unassigned open orders, same priority order. */
    val unassigned: List<DashboardOrderRow>,
)

/**
 * Selects the staff dashboard focus-queue's hero + "Then" queue + "Unassigned
 * in the shop" sections from [rows] (typically `Buckets.openQueue`).
 *
 * Candidates are rows with a non-null [DashboardOrderRow.stage] that isn't
 * [PipelineStage.READY] — a READY order is awaiting owner pickup, nothing
 * left for staff to advance, so it drops out of both the hero and the queue
 * the moment it gets there (a null `stage` — legacy call sites — is treated
 * the same as "not actionable" and dropped too).
 *
 * Priority order (both viewer-assigned and unassigned lists): most days late
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

    val viewerRows = candidates
        .filter { it.assignedMemberId != null && it.assignedMemberId == viewerMemberId }
        .sortedWith(priorityOrder)
    val unassignedRows = candidates
        .filter { it.assignedMemberId == null }
        .sortedWith(priorityOrder)

    return FocusQueue(
        hero = viewerRows.firstOrNull(),
        thenQueue = viewerRows.drop(1),
        unassigned = unassignedRows,
    )
}

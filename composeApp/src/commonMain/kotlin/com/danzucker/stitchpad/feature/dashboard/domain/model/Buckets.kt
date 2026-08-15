package com.danzucker.stitchpad.feature.dashboard.domain.model

/**
 * Triage / pipeline / outstanding aggregates produced by [BucketCalculator]. UI
 * state derives from these — `overdue` + `dueToday` flip the dashboard into
 * `BusyDay`, `ready` into `ReadyForPickup`, etc.
 *
 * Outstanding fields cover only active (non-DELIVERED) orders with a positive
 * balance — the user's view of money still owed.
 *
 * Pipeline lists are capped at preview size. Totals reflect the full count
 * before the cap so the UI can render "see N more" affordances.
 *
 * `nextBestActions` is intentionally NOT part of this aggregate — it is its own
 * concern, computed by the NBA calculator and threaded through the ViewModel.
 *
 * `openQueue` (2026-08-14 focus-queue design) is the uncapped, unfiltered set of
 * active PENDING/IN_PROGRESS orders — the full candidate pool the staff
 * dashboard's hero + "Then" queue + "Unassigned in the shop" sections select
 * from (via `computeFocusQueue`). Unlike `overdue`/`dueToday`/pipeline*, each
 * row here carries whichever of `daysLate`/`daysUntilDeadline` applies (or
 * neither, for a deadline-less or on-track order) so on-track ("OK") orders —
 * invisible to every other bucket — are still represented.
 */
data class Buckets(
    val overdue: List<DashboardOrderRow>,
    val dueToday: List<DashboardOrderRow>,
    val ready: List<DashboardOrderRow>,
    val outstandingAmount: Double,
    val outstandingOrderCount: Int,
    val pipelineInProgress: List<DashboardOrderRow>,
    val pipelineInProgressTotal: Int,
    val pipelinePending: List<DashboardOrderRow>,
    val pipelinePendingTotal: Int,
    val openQueue: List<DashboardOrderRow> = emptyList(),
)

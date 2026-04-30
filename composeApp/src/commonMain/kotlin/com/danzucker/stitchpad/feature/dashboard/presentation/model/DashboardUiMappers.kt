package com.danzucker.stitchpad.feature.dashboard.presentation.model

import androidx.compose.ui.graphics.Color
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens

private const val MAX_TODAY_WORK_ROWS = 5

/**
 * Converts BucketCalculator output lists into a [TodayWorkRowUi] list for [TodayWorkCard].
 *
 * Priority order: overdue first, then dueToday, then ready.
 * Capped at [MAX_TODAY_WORK_ROWS] total rows.
 *
 * Accent colours follow the same semantic colouring used by the old TodaysWorkList:
 * - Overdue → error500 / error50
 * - Due today → warning500 / warning50
 * - Ready → success500 / success50
 */
internal fun buildTodayWorkRows(
    overdue: List<DashboardOrderRow>,
    dueToday: List<DashboardOrderRow>,
    ready: List<DashboardOrderRow>,
): List<TodayWorkRowUi> {
    val combined = buildList {
        overdue.forEach { add(it to BucketLabel.Overdue) }
        dueToday.forEach { add(it to BucketLabel.DueToday) }
        ready.forEach { add(it to BucketLabel.Ready) }
    }
    return combined.take(MAX_TODAY_WORK_ROWS).map { (row, label) ->
        val accent = accentColorFor(label)
        val bg = accentBgFor(label)
        TodayWorkRowUi(
            orderId = row.orderId,
            customerName = row.customerName,
            primaryLabel = row.primaryLabel,
            accentColor = accent,
            chipText = chipTextFor(row, label),
            chipTextColor = accent,
            chipBackground = bg,
        )
    }
}

// — private helpers —

private enum class BucketLabel { Overdue, DueToday, Ready }

private fun accentColorFor(label: BucketLabel): Color = when (label) {
    BucketLabel.Overdue -> DesignTokens.error500
    BucketLabel.DueToday -> DesignTokens.warning500
    BucketLabel.Ready -> DesignTokens.success500
}

private fun accentBgFor(label: BucketLabel): Color = when (label) {
    BucketLabel.Overdue -> DesignTokens.error50
    BucketLabel.DueToday -> DesignTokens.warning50
    BucketLabel.Ready -> DesignTokens.success50
}

/**
 * Returns a short chip label for the row:
 * - Overdue: "<N>d late" (if daysLate is available), else "Overdue"
 * - DueToday: "Due today"
 * - Ready: "Ready"
 *
 * These are plain strings intentionally — the mapper lives in the presentation
 * layer and is called inside a `remember` block in the composable, so
 * string-resource calls that require a Composable context are unavailable.
 * The chip text is short enough that English-only is acceptable for V1.
 */
private fun chipTextFor(row: DashboardOrderRow, label: BucketLabel): String = when (label) {
    BucketLabel.Overdue -> row.daysLate?.let { "${it}d late" } ?: "Overdue"
    BucketLabel.DueToday -> "Due today"
    BucketLabel.Ready -> "Ready"
}

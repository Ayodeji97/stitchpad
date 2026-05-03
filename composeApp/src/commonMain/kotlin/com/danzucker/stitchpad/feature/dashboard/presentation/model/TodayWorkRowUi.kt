package com.danzucker.stitchpad.feature.dashboard.presentation.model

import androidx.compose.ui.graphics.Color

/**
 * Triage bucket the row belongs to. Drives bucket-specific visual treatment
 * in the rich row composable: chip icon, status-hint text + icon, and the
 * accent stripe colour.
 */
enum class TodayWorkBucket {
    Overdue,
    DueToday,
    Ready,
}

/**
 * UI model for a single Today's Work row. Built in DashboardViewModel from
 * BucketCalculator output; consumed by TodayWorkCard. Holds pre-computed
 * styling so the composable stays dumb.
 */
data class TodayWorkRowUi(
    val orderId: String,
    val customerName: String,
    val primaryLabel: String,
    val accentColor: Color,
    val chipText: String,
    val chipTextColor: Color,
    val chipBackground: Color,
    /**
     * Triage bucket — lets the rich row composable pick the correct chip
     * icon ("clock" for overdue, "calendar" for due today, "check" for
     * ready) and footer status-hint copy ("Pickup not ready" / etc.)
     * without re-deriving the bucket from the colours.
     */
    val bucket: TodayWorkBucket = TodayWorkBucket.Overdue,
) {
    companion object {
        /** Convenience factory used only in @Preview composables. */
        fun preview(
            orderId: String,
            customerName: String,
            primaryLabel: String,
            accent: Color,
            chip: String,
            bucket: TodayWorkBucket = TodayWorkBucket.Overdue,
        ) = TodayWorkRowUi(
            orderId = orderId,
            customerName = customerName,
            primaryLabel = primaryLabel,
            accentColor = accent,
            chipText = chip,
            chipTextColor = accent,
            chipBackground = accent.copy(alpha = 0.12f),
            bucket = bucket,
        )
    }
}

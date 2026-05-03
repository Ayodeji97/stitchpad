package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * Coaching bucket for the motivation pill on the WeeklyGoalsCard.
 * Resolved by `WeeklyGoalPaceCalculator` from collected vs expected pace
 * and consumed by `DashboardScreen` to pick a string resource.
 */
enum class WeeklyGoalPace {
    /** Actual progress materially below expected pace — nudge to catch up. */
    Behind,

    /** Actual progress within ±10% of expected — generic affirmation. */
    OnPace,

    /** Actual progress materially above expected pace — celebrate. */
    Ahead,

    /** Collected ≥85% of target regardless of pace — final push. */
    NearGoal,
}

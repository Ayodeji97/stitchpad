package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalPace

private const val DAYS_IN_WEEK = 7
private const val NEAR_GOAL_THRESHOLD = 0.85
private const val PACE_TOLERANCE = 0.10

/**
 * Maps weekly-goal progress against time-elapsed to a coaching bucket the
 * motivation pill on [WeeklyGoalsCard] uses to pick its copy.
 *
 * Inputs come straight from `WeeklyGoalUi` — the calculator is intentionally
 * stateless and doesn't reach for orders, so it stays trivially testable.
 *
 * Buckets, in priority order:
 *   1. **NearGoal** when the user has collected ≥85% of target — they only need
 *      a small final push and shouldn't be told to "pick up the pace" if they're
 *      sitting at 90% on day 1.
 *   2. **Ahead** when actual progress runs ≥10% above expected pace.
 *   3. **Behind** when actual progress runs ≥10% below expected pace.
 *   4. **OnPace** otherwise — covers the comfortable middle band.
 *
 * Expected pace = `(daysElapsed) / 7` where `daysElapsed = 7 - daysLeft`.
 * `daysLeft` is the dashboard's "days remaining after today" count, so on
 * the goal-creation day `daysLeft = 6 → daysElapsed = 1`. Achieved goals
 * (progress ≥ 100%) render the trophy card and skip motivation entirely,
 * so we don't bother branching for them here.
 */
object WeeklyGoalPaceCalculator {

    fun compute(
        collectedAmount: Double,
        targetAmount: Double,
        daysLeft: Int
    ): WeeklyGoalPace {
        if (targetAmount <= 0.0) return WeeklyGoalPace.OnPace
        val progress = (collectedAmount / targetAmount).coerceAtLeast(0.0)
        val daysElapsed = (DAYS_IN_WEEK - daysLeft).coerceIn(0, DAYS_IN_WEEK)
        val expected = daysElapsed.toDouble() / DAYS_IN_WEEK.toDouble()
        val delta = progress - expected
        return when {
            progress >= NEAR_GOAL_THRESHOLD -> WeeklyGoalPace.NearGoal
            delta >= PACE_TOLERANCE -> WeeklyGoalPace.Ahead
            delta <= -PACE_TOLERANCE -> WeeklyGoalPace.Behind
            else -> WeeklyGoalPace.OnPace
        }
    }
}

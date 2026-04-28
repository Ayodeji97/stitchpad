package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * UI-layer rendering model for the WeeklyGoalsCard.
 *
 * [targetAmount] is the user-configured weekly goal sourced from `WeeklyGoalRepository`.
 * [collectedAmount] is computed from the current week's order data, and [daysLeft]
 * counts the remaining days in the week. [progressPercent] is derived for the
 * progress bar and clamped to `0f..1f`.
 */
data class WeeklyGoalUi(
    val targetAmount: Double,
    val collectedAmount: Double,
    val daysLeft: Int
) {
    val progressPercent: Float
        get() = if (targetAmount > 0) {
            (collectedAmount / targetAmount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

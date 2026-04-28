package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * UI-layer rendering model for the WeeklyGoalsCard. Domain model + persistence
 * are deferred to PR 8. For now, the ViewModel uses a hardcoded target and
 * computes [collectedAmount] from real order data within the current week.
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

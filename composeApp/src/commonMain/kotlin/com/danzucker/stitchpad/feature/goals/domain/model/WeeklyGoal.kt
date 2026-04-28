package com.danzucker.stitchpad.feature.goals.domain.model

/**
 * The user's revenue target for a single ISO week (Monday-Sunday). Stored
 * per-user in Firestore at users/{uid}/goals/weekly. Only one active goal
 * exists at a time — newer values overwrite older ones.
 *
 * Progress is computed in the dashboard ViewModel by summing collected revenue
 * from orders within the current week — see DashboardViewModel.computeWeeklyGoalCollected.
 */
data class WeeklyGoal(
    val targetAmount: Double,
    val updatedAt: Long
)

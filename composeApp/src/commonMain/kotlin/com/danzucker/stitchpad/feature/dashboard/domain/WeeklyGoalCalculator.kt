package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

private const val DAYS_IN_WEEK = 7

/**
 * Builds the WeeklyGoalsCard render model from the user's saved [goal] and the
 * collected revenue derived from orders updated in the current ISO week
 * (Monday–Sunday). Returns `null` when the user has not set a goal yet — the
 * card renders its empty "Set your first goal" state in that case.
 *
 * Collected amount = `Σ (totalPrice − balanceRemaining)`, clamped at 0, over
 * orders whose `updatedAt` falls on or after the start of the current week.
 * `daysLeft` counts forward including today (Mon = 6 left … Sun = 0 left).
 */
object WeeklyGoalCalculator {

    fun compute(
        orders: List<Order>,
        today: LocalDate,
        goal: WeeklyGoal?,
        timeZone: TimeZone
    ): WeeklyGoalUi? {
        if (goal == null) return null
        val daysFromMonday = today.dayOfWeek.ordinal
        val weekStart = today.minus(daysFromMonday, DateTimeUnit.DAY)
        val weekStartMillis = weekStart.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val collected = orders
            .filter { it.updatedAt >= weekStartMillis }
            .sumOf { (it.totalPrice - it.balanceRemaining).coerceAtLeast(0.0) }
        val daysLeft = (DAYS_IN_WEEK - 1 - daysFromMonday).coerceIn(0, DAYS_IN_WEEK)
        return WeeklyGoalUi(
            targetAmount = goal.targetAmount,
            collectedAmount = collected,
            daysLeft = daysLeft
        )
    }
}

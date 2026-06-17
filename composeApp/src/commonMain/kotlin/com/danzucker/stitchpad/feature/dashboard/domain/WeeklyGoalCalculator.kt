package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private const val DAYS_IN_WEEK = 7

/**
 * Builds the WeeklyGoalsCard render model from the user's saved [goal] and
 * collected revenue from orders updated within the current 7-day cycle.
 *
 * Cycles are anchored to the goal's creation day (`goal.updatedAt`), not the
 * ISO calendar week. A goal set on a Saturday gives the user a full Sat-Fri
 * window; once that window ends, the next 7-day cycle starts automatically
 * on the same week-day. This avoids the "I just set a goal but only have 1
 * day left because it's Saturday" surprise.
 *
 * Collected amount = `Σ depositPaid`, over
 * orders whose `updatedAt` falls inside the active cycle window.
 * `daysLeft` counts days remaining *after* today (cycle start = 6, cycle
 * end = 0).
 */
object WeeklyGoalCalculator {

    fun compute(
        orders: List<Order>,
        today: LocalDate,
        goal: WeeklyGoal?,
        timeZone: TimeZone
    ): WeeklyGoalUi? {
        if (goal == null) return null

        val goalStartDate = Instant.fromEpochMilliseconds(goal.updatedAt)
            .toLocalDateTime(timeZone)
            .date

        // toEpochDays() returns Long on iOS / Kotlin-Native and Int on the JVM
        // — explicit .toLong() keeps the math portable across both targets.
        val todayEpoch = today.toEpochDays().toLong()
        val goalStartEpoch = goalStartDate.toEpochDays().toLong()
        val daysInWeek = DAYS_IN_WEEK.toLong()

        val daysSinceGoalStart = (todayEpoch - goalStartEpoch).coerceAtLeast(0L)
        val cyclesElapsed = daysSinceGoalStart / daysInWeek
        val cycleStart = goalStartDate.plus(cyclesElapsed * daysInWeek, DateTimeUnit.DAY)
        val cycleStartMillis = cycleStart.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val nextCycleStartMillis = cycleStart
            .plus(daysInWeek, DateTimeUnit.DAY)
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()

        val collected = orders
            .filter { it.updatedAt in cycleStartMillis until nextCycleStartMillis }
            .sumOf { it.depositPaid }

        val cycleStartEpoch = cycleStart.toEpochDays().toLong()
        val daysIntoCycle = (todayEpoch - cycleStartEpoch).coerceIn(0L, daysInWeek - 1L)
        val daysLeft = (daysInWeek - 1L - daysIntoCycle).coerceIn(0L, daysInWeek).toInt()

        val pace = WeeklyGoalPaceCalculator.compute(
            collectedAmount = collected,
            targetAmount = goal.targetAmount,
            daysLeft = daysLeft
        )
        return WeeklyGoalUi(
            targetAmount = goal.targetAmount,
            collectedAmount = collected,
            daysLeft = daysLeft,
            pace = pace
        )
    }
}

package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlinx.datetime.TimeZone

sealed interface DeadlineDisplay {
    data object NoDeadline : DeadlineDisplay
    data class DaysLate(val days: Int) : DeadlineDisplay
    data object DueToday : DeadlineDisplay
    data object DueTomorrow : DeadlineDisplay
    data class DueInDays(val days: Int, val soon: Boolean) : DeadlineDisplay
    data object PickupReady : DeadlineDisplay
    data object Completed : DeadlineDisplay
}

private const val SOON_DAYS_MAX = 3

fun formatDeadline(
    deadline: Long?,
    now: Long,
    status: OrderStatus,
    zone: TimeZone = TimeZone.currentSystemDefault()
): DeadlineDisplay = when {
    status == OrderStatus.DELIVERED -> DeadlineDisplay.Completed
    status == OrderStatus.READY -> DeadlineDisplay.PickupReady
    deadline == null -> DeadlineDisplay.NoDeadline
    deadline < now -> {
        // Calendar-day diff in the user's zone; coerce sub-day late (same calendar day but
        // clock is past deadline) to 1 so the row reads "1 day late" instead of "0 days late".
        val daysLate = calendarDaysBetween(deadline, now, zone).coerceAtLeast(1)
        DeadlineDisplay.DaysLate(daysLate)
    }
    else -> {
        val daysUntil = calendarDaysBetween(now, deadline, zone)
        when {
            daysUntil == 0 -> DeadlineDisplay.DueToday
            daysUntil == 1 -> DeadlineDisplay.DueTomorrow
            daysUntil in 2..SOON_DAYS_MAX -> DeadlineDisplay.DueInDays(days = daysUntil, soon = true)
            else -> DeadlineDisplay.DueInDays(days = daysUntil, soon = false)
        }
    }
}

package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus

sealed interface DeadlineDisplay {
    data object NoDeadline : DeadlineDisplay
    data class DaysLate(val days: Int) : DeadlineDisplay
    data object DueToday : DeadlineDisplay
    data object DueTomorrow : DeadlineDisplay
    data class DueInDays(val days: Int, val soon: Boolean) : DeadlineDisplay
    data object PickupReady : DeadlineDisplay
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

fun formatDeadline(deadline: Long?, now: Long, status: OrderStatus): DeadlineDisplay = when {
    status == OrderStatus.READY -> DeadlineDisplay.PickupReady
    deadline == null -> DeadlineDisplay.NoDeadline
    deadline < now -> {
        // Floor-divide absolute overdue millis to whole days; minimum 1 so
        // sub-day overdue reads as "1 day late" instead of "0 days late".
        val daysLate = ((now - deadline) / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
        DeadlineDisplay.DaysLate(daysLate)
    }
    else -> {
        val daysUntil = ((deadline - now) / MILLIS_PER_DAY).toInt()
        when {
            daysUntil == 0 -> DeadlineDisplay.DueToday
            daysUntil == 1 -> DeadlineDisplay.DueTomorrow
            daysUntil in 2..3 -> DeadlineDisplay.DueInDays(days = daysUntil, soon = true)
            else -> DeadlineDisplay.DueInDays(days = daysUntil, soon = false)
        }
    }
}

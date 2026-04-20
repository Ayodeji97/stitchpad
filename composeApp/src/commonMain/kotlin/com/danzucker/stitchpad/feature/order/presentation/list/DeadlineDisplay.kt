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

fun formatDeadline(deadline: Long?, now: Long, status: OrderStatus): DeadlineDisplay {
    if (status == OrderStatus.READY) return DeadlineDisplay.PickupReady
    if (deadline == null) return DeadlineDisplay.NoDeadline

    val deltaMillis = deadline - now
    if (deltaMillis < 0) {
        // floor-divide absolute value, min 1
        val daysLate = ((-deltaMillis) / MILLIS_PER_DAY).toInt()
        return DeadlineDisplay.DaysLate(daysLate.coerceAtLeast(1))
    }

    val daysUntil = (deltaMillis / MILLIS_PER_DAY).toInt()
    return when {
        daysUntil == 0 -> DeadlineDisplay.DueToday
        daysUntil == 1 -> DeadlineDisplay.DueTomorrow
        daysUntil in 2..3 -> DeadlineDisplay.DueInDays(days = daysUntil, soon = true)
        else -> DeadlineDisplay.DueInDays(days = daysUntil, soon = false)
    }
}

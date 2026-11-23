package com.danzucker.stitchpad.feature.order.presentation.form

import com.danzucker.stitchpad.feature.order.presentation.list.calendarDaysBetween
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** One-tap deadline chips on the order form's Details step, in days from today. */
internal val DEADLINE_QUICK_PICK_DAYS: List<Int> = listOf(3, 7, 14)

/** A brand-new order starts with a deadline this many days out so it is never forgotten. */
internal const val DEFAULT_NEW_ORDER_DEADLINE_DAYS = 7

/**
 * Epoch millis of the start of the local calendar day [days] days after the day containing
 * [nowMillis] in [zone]. Matches the custom date picker, which stores a picked date as
 * `LocalDate.atStartOfDayIn(zone)`, so a quick-pick and a picker choice of the same date
 * produce identical values.
 */
internal fun deadlineDaysFromToday(nowMillis: Long, days: Int, zone: TimeZone): Long {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    return today.plus(days, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
}

/**
 * Which quick-pick chip (if any) the current [deadline] corresponds to, compared by local
 * calendar day (same arithmetic as the order list's deadline triage). Null when there is
 * no deadline or it doesn't land on a chip's date.
 */
internal fun matchingDeadlineQuickPick(deadline: Long?, nowMillis: Long, zone: TimeZone): Int? {
    if (deadline == null) return null
    val daysOut = calendarDaysBetween(nowMillis, deadline, zone)
    return DEADLINE_QUICK_PICK_DAYS.firstOrNull { it == daysOut }
}

package com.danzucker.stitchpad.feature.order.presentation.list

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Returns the calendar-day difference (toDate − fromDate) in [zone]. Positive when [toMillis]
 * is on a later calendar day than [fromMillis]. Triage + deadline formatting both require
 * calendar-day arithmetic (not `millis / 86_400_000`) so that a deadline reads as "due
 * tomorrow" at 3am instead of still showing "due today".
 */
internal fun calendarDaysBetween(fromMillis: Long, toMillis: Long, zone: TimeZone): Int {
    val fromDate = Instant.fromEpochMilliseconds(fromMillis).toLocalDateTime(zone).date
    val toDate = Instant.fromEpochMilliseconds(toMillis).toLocalDateTime(zone).date
    return fromDate.daysUntil(toDate)
}

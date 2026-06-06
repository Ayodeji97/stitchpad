package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

fun notificationRelativeTime(createdAtMillis: Long, nowMillis: Long, tz: TimeZone): String {
    if (createdAtMillis > nowMillis) {
        return "now"
    }

    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)
    val created = Instant.fromEpochMilliseconds(createdAtMillis).toLocalDateTime(tz)

    // .toLong() required: toEpochDays() returns Long on iOS Native, Int on JVM —
    // mixing them fails the iOS compile. Looks redundant on JVM; do not remove.
    val epochDayDiff = now.date.toEpochDays().toLong() - created.date.toEpochDays().toLong()

    return when {
        epochDayDiff == 0L -> {
            val totalMinutes = (nowMillis - createdAtMillis) / MILLIS_PER_MINUTE
            when {
                totalMinutes < 1L -> "now"
                totalMinutes < MINUTES_PER_HOUR -> "${totalMinutes}m"
                else -> "${totalMinutes / MINUTES_PER_HOUR}h"
            }
        }
        epochDayDiff in 1L..6L -> dayOfWeekAbbrev(created.dayOfWeek)
        else -> "${created.dayOfMonth} ${monthAbbrev(created.month)}"
    }
}

private fun dayOfWeekAbbrev(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun monthAbbrev(m: Month): String = when (m) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
}

data class NotificationSection(val isToday: Boolean, val items: List<Notification>)

fun groupNotificationsByDay(
    items: List<Notification>,
    nowMillis: Long,
    tz: TimeZone,
): List<NotificationSection> {
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)

    val todayItems = mutableListOf<Notification>()
    val earlierItems = mutableListOf<Notification>()

    for (item in items) {
        val created = Instant.fromEpochMilliseconds(item.createdAt).toLocalDateTime(tz)
        // .toLong() required for iOS Native (see note above); do not remove.
        val epochDayDiff = now.date.toEpochDays().toLong() - created.date.toEpochDays().toLong()
        if (epochDayDiff <= 0L) {
            todayItems += item
        } else {
            earlierItems += item
        }
    }

    val sections = mutableListOf<NotificationSection>()
    if (todayItems.isNotEmpty()) {
        sections += NotificationSection(isToday = true, items = todayItems)
    }
    if (earlierItems.isNotEmpty()) {
        sections += NotificationSection(isToday = false, items = earlierItems)
    }
    return sections
}

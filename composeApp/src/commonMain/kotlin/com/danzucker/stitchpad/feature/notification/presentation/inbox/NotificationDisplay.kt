package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/**
 * Structured result for relative-time display. All user-facing text is resolved
 * in the UI layer via compose string resources — see [relativeTimeLabel] in
 * NotificationRow.kt.
 */
sealed interface RelativeTime {
    data object Now : RelativeTime
    data class Minutes(val value: Long) : RelativeTime
    data class Hours(val value: Long) : RelativeTime
    data class Weekday(val day: DayOfWeek) : RelativeTime
    data class MonthDay(val day: Int, val month: Month) : RelativeTime
}

fun notificationRelativeTime(createdAtMillis: Long, nowMillis: Long, tz: TimeZone): RelativeTime {
    if (createdAtMillis > nowMillis) {
        return RelativeTime.Now
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
                totalMinutes < 1L -> RelativeTime.Now
                totalMinutes < MINUTES_PER_HOUR -> RelativeTime.Minutes(totalMinutes)
                else -> RelativeTime.Hours(totalMinutes / MINUTES_PER_HOUR)
            }
        }
        epochDayDiff in 1L..6L -> RelativeTime.Weekday(created.dayOfWeek)
        else -> RelativeTime.MonthDay(created.dayOfMonth, created.month)
    }
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

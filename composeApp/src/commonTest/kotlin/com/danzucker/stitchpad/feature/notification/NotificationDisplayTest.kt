package com.danzucker.stitchpad.feature.notification

import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationSection
import com.danzucker.stitchpad.feature.notification.presentation.inbox.RelativeTime
import com.danzucker.stitchpad.feature.notification.presentation.inbox.groupNotificationsByDay
import com.danzucker.stitchpad.feature.notification.presentation.inbox.notificationRelativeTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

// Anchor: 2026-06-03 12:00:00 Lagos time (UTC+1) = 1_780_484_400_000 ms
private const val NOW = 1_780_484_400_000L
private val LAGOS = TimeZone.of("Africa/Lagos")

private fun notifAt(createdAt: Long, id: String = "n1") = Notification(
    id = id,
    orderId = "ord-$id",
    type = NotificationType.OVERDUE,
    customerName = "Fola",
    garmentSummary = "Agbada",
    createdAt = createdAt,
)

class NotificationDisplayTest {

    // ------------------------------------------------------------------ //
    // notificationRelativeTime — same calendar day cases
    // ------------------------------------------------------------------ //

    @Test
    fun relativeTime_sameDay_30min_returns30m() {
        val created = NOW - 30 * 60 * 1000L
        assertEquals(RelativeTime.Minutes(30), notificationRelativeTime(created, NOW, LAGOS))
    }

    @Test
    fun relativeTime_sameDay_2hours_returns2h() {
        val created = NOW - 2 * 60 * 60 * 1000L
        assertEquals(RelativeTime.Hours(2), notificationRelativeTime(created, NOW, LAGOS))
    }

    @Test
    fun relativeTime_sameDay_90min_returns1h() {
        val created = NOW - 90 * 60 * 1000L
        assertEquals(RelativeTime.Hours(1), notificationRelativeTime(created, NOW, LAGOS))
    }

    @Test
    fun relativeTime_sameDay_30sec_returnsNow() {
        val created = NOW - 30 * 1000L
        assertEquals(RelativeTime.Now, notificationRelativeTime(created, NOW, LAGOS))
    }

    // ------------------------------------------------------------------ //
    // notificationRelativeTime — future returns Now
    // ------------------------------------------------------------------ //

    @Test
    fun relativeTime_future_returnsNow() {
        val created = NOW + 60 * 60 * 1000L // +1h in the future
        assertEquals(RelativeTime.Now, notificationRelativeTime(created, NOW, LAGOS))
    }

    // ------------------------------------------------------------------ //
    // notificationRelativeTime — previous calendar day (day abbrev range)
    // ------------------------------------------------------------------ //

    @Test
    fun relativeTime_25hoursAgo_previousCalendarDay_returnsTuesday() {
        // now - 25h = 2026-06-02 11:00 Lagos → Tuesday, epochDayDiff=1
        val created = NOW - 25 * 60 * 60 * 1000L
        assertEquals(RelativeTime.Weekday(DayOfWeek.TUESDAY), notificationRelativeTime(created, NOW, LAGOS))
    }

    @Test
    fun relativeTime_6daysBefore_returnsThursday() {
        // now - 6d = 2026-05-28 12:00 Lagos → Thursday, epochDayDiff=6
        val created = NOW - 6 * 24 * 60 * 60 * 1000L
        assertEquals(RelativeTime.Weekday(DayOfWeek.THURSDAY), notificationRelativeTime(created, NOW, LAGOS))
    }

    // ------------------------------------------------------------------ //
    // notificationRelativeTime — 7+ days ago (date format)
    // ------------------------------------------------------------------ //

    @Test
    fun relativeTime_7daysBefore_returnsDayMonth() {
        // now - 7d = 2026-05-27 12:00 Lagos → epochDayDiff=7 → MonthDay(27, MAY)
        val created = NOW - 7 * 24 * 60 * 60 * 1000L
        assertEquals(RelativeTime.MonthDay(27, Month.MAY), notificationRelativeTime(created, NOW, LAGOS))
    }

    // ------------------------------------------------------------------ //
    // groupNotificationsByDay
    // ------------------------------------------------------------------ //

    @Test
    fun groupByDay_emptyList_returnsEmpty() {
        val result = groupNotificationsByDay(emptyList(), NOW, LAGOS)
        assertEquals(emptyList(), result)
    }

    @Test
    fun groupByDay_allTodayItems_returnsSingleTodaySection() {
        val items = listOf(
            notifAt(NOW - 30 * 60 * 1000L, "a"),  // 30min ago
            notifAt(NOW - 60 * 60 * 1000L, "b"),  // 1h ago
        )
        val result = groupNotificationsByDay(items, NOW, LAGOS)
        assertEquals(1, result.size)
        assertEquals(true, result[0].isToday)
        assertEquals(listOf("a", "b"), result[0].items.map { it.id })
    }

    @Test
    fun groupByDay_allEarlierItems_returnsSingleEarlierSection() {
        val items = listOf(
            notifAt(NOW - 25 * 60 * 60 * 1000L, "x"),  // yesterday
            notifAt(NOW - 7 * 24 * 60 * 60 * 1000L, "y"), // 7 days ago
        )
        val result = groupNotificationsByDay(items, NOW, LAGOS)
        assertEquals(1, result.size)
        assertEquals(false, result[0].isToday)
        assertEquals(listOf("x", "y"), result[0].items.map { it.id })
    }

    @Test
    fun groupByDay_mixedItems_returnsTodayFirstThenEarlier() {
        val items = listOf(
            notifAt(NOW - 30 * 60 * 1000L, "today1"),   // today
            notifAt(NOW - 25 * 60 * 60 * 1000L, "old1"), // yesterday
            notifAt(NOW - 60 * 60 * 1000L, "today2"),   // today
            notifAt(NOW - 7 * 24 * 60 * 60 * 1000L, "old2"), // 7 days ago
        )
        val result = groupNotificationsByDay(items, NOW, LAGOS)
        assertEquals(2, result.size)
        assertEquals(true, result[0].isToday)
        assertEquals(false, result[1].isToday)
        // Order within sections preserved
        assertEquals(listOf("today1", "today2"), result[0].items.map { it.id })
        assertEquals(listOf("old1", "old2"), result[1].items.map { it.id })
    }

    @Test
    fun groupByDay_futureItem_countsAsToday() {
        // Items with createdAt > nowMillis should be treated as "today" (epochDayDiff <= 0)
        val items = listOf(
            notifAt(NOW + 60 * 60 * 1000L, "future"), // 1h in future
        )
        val result = groupNotificationsByDay(items, NOW, LAGOS)
        assertEquals(1, result.size)
        assertEquals(true, result[0].isToday)
    }
}

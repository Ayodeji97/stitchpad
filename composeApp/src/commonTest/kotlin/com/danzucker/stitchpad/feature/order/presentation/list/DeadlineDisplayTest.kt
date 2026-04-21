package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineDisplayTest {

    // 2023-11-14 17:13:20 in America/New_York — mid-afternoon, leaves headroom for same-day
    // +3h offsets used below without crossing midnight.
    private val now = 1_700_000_000_000L
    private val oneDay = 24L * 60 * 60 * 1000
    private val zone = TimeZone.of("America/New_York")

    @Test
    fun readyStatusReturnsPickupReadyRegardlessOfDeadline() {
        assertEquals(DeadlineDisplay.PickupReady, formatDeadline(now - oneDay, now, OrderStatus.READY, zone))
        assertEquals(DeadlineDisplay.PickupReady, formatDeadline(null, now, OrderStatus.READY, zone))
    }

    @Test
    fun nullDeadlineReturnsNoDeadline() {
        assertEquals(DeadlineDisplay.NoDeadline, formatDeadline(null, now, OrderStatus.PENDING, zone))
    }

    @Test
    fun deadlinePastReturnsDaysLate() {
        val three = formatDeadline(now - 3 * oneDay, now, OrderStatus.PENDING, zone)
        assertEquals(DeadlineDisplay.DaysLate(3), three)
    }

    @Test
    fun oneDayLateReturnsDaysLateOne() {
        val one = formatDeadline(now - oneDay - 1000, now, OrderStatus.PENDING, zone)
        assertEquals(DeadlineDisplay.DaysLate(1), one)
    }

    @Test
    fun deliveredOrderReturnsCompletedRegardlessOfDeadline() {
        // DELIVERED rows can still appear in the "Delivered" status filter, so the
        // formatter short-circuits to Completed — completed work must never render as late.
        assertEquals(
            DeadlineDisplay.Completed,
            formatDeadline(now - oneDay, now, OrderStatus.DELIVERED, zone)
        )
        assertEquals(
            DeadlineDisplay.Completed,
            formatDeadline(null, now, OrderStatus.DELIVERED, zone)
        )
    }

    @Test
    fun deadlineTodayReturnsDueToday() {
        // same calendar day, a few hours in the future
        assertEquals(
            DeadlineDisplay.DueToday,
            formatDeadline(now + 3 * 60 * 60 * 1000, now, OrderStatus.PENDING, zone)
        )
    }

    @Test
    fun deadlineTomorrowReturnsDueTomorrow() {
        assertEquals(
            DeadlineDisplay.DueTomorrow,
            formatDeadline(now + oneDay + 60_000, now, OrderStatus.PENDING, zone)
        )
    }

    @Test
    fun deadlineIn3DaysReturnsDueInDaysSoon() {
        val res = formatDeadline(now + 3 * oneDay + 60_000, now, OrderStatus.PENDING, zone)
        assertEquals(DeadlineDisplay.DueInDays(days = 3, soon = true), res)
    }

    @Test
    fun deadlineIn4DaysReturnsDueInDaysMuted() {
        val res = formatDeadline(now + 4 * oneDay + 60_000, now, OrderStatus.PENDING, zone)
        assertEquals(DeadlineDisplay.DueInDays(days = 4, soon = false), res)
    }

    @Test
    fun deadlineFarFutureReturnsDueInDaysMuted() {
        val res = formatDeadline(now + 12 * oneDay, now, OrderStatus.PENDING, zone)
        assertEquals(DeadlineDisplay.DueInDays(days = 12, soon = false), res)
    }

    @Test
    fun deadlineEarlyTomorrowMorningDoesNotReadAsToday() {
        // Regression guard for the spec's 3am case: at 17:13 local, a deadline 10h later
        // (03:13 next day) must read as "due tomorrow", not "due today".
        val tenHours = 10L * 60 * 60 * 1000
        assertEquals(
            DeadlineDisplay.DueTomorrow,
            formatDeadline(now + tenHours, now, OrderStatus.PENDING, zone)
        )
    }
}

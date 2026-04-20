package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineDisplayTest {

    private val now = 1_700_000_000_000L
    private val oneDay = 24L * 60 * 60 * 1000

    @Test
    fun readyStatusReturnsPickupReadyRegardlessOfDeadline() {
        assertEquals(DeadlineDisplay.PickupReady, formatDeadline(now - oneDay, now, OrderStatus.READY))
        assertEquals(DeadlineDisplay.PickupReady, formatDeadline(null, now, OrderStatus.READY))
    }

    @Test
    fun nullDeadlineReturnsNoDeadline() {
        assertEquals(DeadlineDisplay.NoDeadline, formatDeadline(null, now, OrderStatus.PENDING))
    }

    @Test
    fun deadlinePastReturnsDaysLate() {
        val three = formatDeadline(now - 3 * oneDay, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DaysLate(3), three)
    }

    @Test
    fun oneDayLateReturnsDaysLateOne() {
        val one = formatDeadline(now - oneDay - 1000, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DaysLate(1), one)
    }

    @Test
    fun deliveredOrderWithPastDeadlineStillReturnsDaysLate() {
        // formatter does not special-case DELIVERED — grouping logic excludes those rows
        val res = formatDeadline(now - oneDay, now, OrderStatus.DELIVERED)
        assertEquals(DeadlineDisplay.DaysLate(1), res)
    }

    @Test
    fun deadlineTodayReturnsDueToday() {
        // same calendar day, a few hours in the future
        assertEquals(DeadlineDisplay.DueToday, formatDeadline(now + 3 * 60 * 60 * 1000, now, OrderStatus.PENDING))
    }

    @Test
    fun deadlineTomorrowReturnsDueTomorrow() {
        assertEquals(DeadlineDisplay.DueTomorrow, formatDeadline(now + oneDay + 60_000, now, OrderStatus.PENDING))
    }

    @Test
    fun deadlineIn3DaysReturnsDueInDaysSoon() {
        val res = formatDeadline(now + 3 * oneDay + 60_000, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DueInDays(days = 3, soon = true), res)
    }

    @Test
    fun deadlineIn4DaysReturnsDueInDaysMuted() {
        val res = formatDeadline(now + 4 * oneDay + 60_000, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DueInDays(days = 4, soon = false), res)
    }

    @Test
    fun deadlineFarFutureReturnsDueInDaysMuted() {
        val res = formatDeadline(now + 12 * oneDay, now, OrderStatus.PENDING)
        assertEquals(DeadlineDisplay.DueInDays(days = 12, soon = false), res)
    }
}

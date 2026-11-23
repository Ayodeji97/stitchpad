package com.danzucker.stitchpad.feature.order.presentation.form

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeadlineQuickPickTest {

    private val lagos = TimeZone.of("Africa/Lagos")

    private fun millisAt(dateTime: LocalDateTime, zone: TimeZone): Long =
        dateTime.toInstant(zone).toEpochMilliseconds()

    private fun startOfDay(date: LocalDate, zone: TimeZone): Long =
        date.atStartOfDayIn(zone).toEpochMilliseconds()

    @Test
    fun addsDaysAndLandsOnStartOfLocalDay_likeTheDatePicker() {
        val now = millisAt(LocalDateTime(2026, 11, 23, 15, 42), lagos)

        assertEquals(startOfDay(LocalDate(2026, 11, 26), lagos), deadlineDaysFromToday(now, 3, lagos))
        assertEquals(startOfDay(LocalDate(2026, 11, 30), lagos), deadlineDaysFromToday(now, 7, lagos))
    }

    @Test
    fun rollsOverMonthEnd() {
        val now = millisAt(LocalDateTime(2026, 1, 28, 9, 0), lagos)

        assertEquals(startOfDay(LocalDate(2026, 2, 4), lagos), deadlineDaysFromToday(now, 7, lagos))
        assertEquals(startOfDay(LocalDate(2026, 2, 11), lagos), deadlineDaysFromToday(now, 14, lagos))
    }

    @Test
    fun rollsOverYearEnd() {
        val now = millisAt(LocalDateTime(2026, 12, 29, 20, 0), lagos)

        assertEquals(startOfDay(LocalDate(2027, 1, 1), lagos), deadlineDaysFromToday(now, 3, lagos))
        assertEquals(startOfDay(LocalDate(2027, 1, 12), lagos), deadlineDaysFromToday(now, 14, lagos))
    }

    @Test
    fun handlesLeapDay() {
        val now = millisAt(LocalDateTime(2028, 2, 26, 8, 0), lagos)

        assertEquals(startOfDay(LocalDate(2028, 2, 29), lagos), deadlineDaysFromToday(now, 3, lagos))
    }

    @Test
    fun usesLocalDate_notUtcDate_justAfterMidnight() {
        // 00:30 on Dec 1 in Lagos is still Nov 30 in UTC. "Today" must be the local Dec 1.
        val now = millisAt(LocalDateTime(2026, 12, 1, 0, 30), lagos)

        assertEquals(startOfDay(LocalDate(2026, 12, 4), lagos), deadlineDaysFromToday(now, 3, lagos))
    }

    @Test
    fun acrossDstChange_stillStartOfTargetDay() {
        val paris = TimeZone.of("Europe/Paris")
        // Clocks go forward on 2028-03-26 in Paris.
        val now = millisAt(LocalDateTime(2028, 3, 24, 12, 0), paris)

        assertEquals(startOfDay(LocalDate(2028, 3, 27), paris), deadlineDaysFromToday(now, 3, paris))
    }

    @Test
    fun matchingQuickPick_matchesByCalendarDay() {
        val now = millisAt(LocalDateTime(2026, 12, 29, 20, 0), lagos)

        assertEquals(3, matchingDeadlineQuickPick(deadlineDaysFromToday(now, 3, lagos), now, lagos))
        assertEquals(7, matchingDeadlineQuickPick(deadlineDaysFromToday(now, 7, lagos), now, lagos))
        assertEquals(14, matchingDeadlineQuickPick(deadlineDaysFromToday(now, 14, lagos), now, lagos))
        // Any time on the chip's day still counts as that chip.
        val laterSameDay = millisAt(LocalDateTime(2027, 1, 5, 18, 0), lagos)
        assertEquals(7, matchingDeadlineQuickPick(laterSameDay, now, lagos))
    }

    @Test
    fun matchingQuickPick_nullForNoDeadlineOrOtherDates() {
        val now = millisAt(LocalDateTime(2026, 11, 23, 10, 0), lagos)

        assertNull(matchingDeadlineQuickPick(null, now, lagos))
        assertNull(matchingDeadlineQuickPick(deadlineDaysFromToday(now, 5, lagos), now, lagos))
        assertNull(matchingDeadlineQuickPick(deadlineDaysFromToday(now, 0, lagos), now, lagos))
    }
}

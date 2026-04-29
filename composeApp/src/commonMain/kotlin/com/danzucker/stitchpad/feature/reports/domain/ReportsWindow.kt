package com.danzucker.stitchpad.feature.reports.domain

import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val DAYS_IN_WEEK = 7

/**
 * Resolves the [start, end) epoch-millisecond window for a Reports period,
 * shifted [periodsBack] periods into the past (0 = current, 1 = previous, ...).
 *
 * Week windows snap to Monday 00:00 in [timeZone]. Month windows snap to the
 * 1st of the month at 00:00 in [timeZone]. The end is exclusive so callers
 * should use `updatedAt in start until end`.
 */
internal fun reportsWindow(
    period: ReportsPeriod,
    today: LocalDate,
    timeZone: TimeZone,
    periodsBack: Int
): Pair<Long, Long> = when (period) {
    ReportsPeriod.WEEK -> {
        val daysFromMonday = today.dayOfWeek.ordinal
        val currentWeekStart = today.minus(daysFromMonday, DateTimeUnit.DAY)
        val start = currentWeekStart.minus(periodsBack * DAYS_IN_WEEK, DateTimeUnit.DAY)
        val end = start.plus(DAYS_IN_WEEK, DateTimeUnit.DAY)
        start.atStartOfDayIn(timeZone).toEpochMilliseconds() to
            end.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }
    ReportsPeriod.MONTH -> {
        val currentMonthStart = LocalDate(today.year, today.month, 1)
        val start = currentMonthStart.minus(periodsBack, DateTimeUnit.MONTH)
        val end = start.plus(1, DateTimeUnit.MONTH)
        start.atStartOfDayIn(timeZone).toEpochMilliseconds() to
            end.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }
}

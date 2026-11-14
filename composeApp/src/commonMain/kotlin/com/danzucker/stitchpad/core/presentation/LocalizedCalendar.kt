package com.danzucker.stitchpad.core.presentation

import androidx.compose.runtime.Composable
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.calendar_months_long
import stitchpad.composeapp.generated.resources.calendar_months_short
import stitchpad.composeapp.generated.resources.weekday_abbrev_fri
import stitchpad.composeapp.generated.resources.weekday_abbrev_mon
import stitchpad.composeapp.generated.resources.weekday_abbrev_sat
import stitchpad.composeapp.generated.resources.weekday_abbrev_sun
import stitchpad.composeapp.generated.resources.weekday_abbrev_thu
import stitchpad.composeapp.generated.resources.weekday_abbrev_tue
import stitchpad.composeapp.generated.resources.weekday_abbrev_wed

/** Resource-backed display names; month numbers and saved dates remain unchanged. */
@Composable
fun localizedMonthName(month: Int, short: Boolean = false): String {
    val names = stringResource(if (short) Res.string.calendar_months_short else Res.string.calendar_months_long)
    return names.split("|").getOrElse(month - 1) { "" }
}

/** Non-composable twin of [localizedMonthName] for suspend contexts (ViewModels, formatters' callers). */
suspend fun localizedMonthNameAsync(month: Int, short: Boolean = false): String =
    localizedMonthNamesAsync(short).getOrElse(month - 1) { "" }

/** All twelve localized month names in calendar order (index 0 = January). */
suspend fun localizedMonthNamesAsync(short: Boolean = false): List<String> =
    getString(if (short) Res.string.calendar_months_short else Res.string.calendar_months_long).split("|")

/** Resource-backed short weekday name ("Fri" / "Ven"). */
@Composable
fun localizedWeekdayShort(day: DayOfWeek): String = stringResource(weekdayShortResource(day))

/** Non-composable twin of [localizedWeekdayShort] for suspend contexts. */
suspend fun localizedWeekdayShortAsync(day: DayOfWeek): String = getString(weekdayShortResource(day))

private fun weekdayShortResource(day: DayOfWeek): StringResource = when (day) {
    DayOfWeek.MONDAY -> Res.string.weekday_abbrev_mon
    DayOfWeek.TUESDAY -> Res.string.weekday_abbrev_tue
    DayOfWeek.WEDNESDAY -> Res.string.weekday_abbrev_wed
    DayOfWeek.THURSDAY -> Res.string.weekday_abbrev_thu
    DayOfWeek.FRIDAY -> Res.string.weekday_abbrev_fri
    DayOfWeek.SATURDAY -> Res.string.weekday_abbrev_sat
    DayOfWeek.SUNDAY -> Res.string.weekday_abbrev_sun
}

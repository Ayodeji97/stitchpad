package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danzucker.stitchpad.ui.theme.DesignTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.common_ok
import stitchpad.composeapp.generated.resources.date_picker_eyebrow
import stitchpad.composeapp.generated.resources.date_picker_next_month_cd
import stitchpad.composeapp.generated.resources.date_picker_placeholder
import stitchpad.composeapp.generated.resources.date_picker_prev_month_cd
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun CustomDatePickerDialog(
    initial: LocalDate?,
    timeZone: TimeZone,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null
) {
    val today = remember(timeZone) {
        val millis = Clock.System.now().toEpochMilliseconds()
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
    }
    var selected by remember { mutableStateOf(initial) }
    var displayed by remember {
        val seed = initial ?: today
        mutableStateOf(YearMonth(seed.year, seed.monthNumber))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column {
                PickerHeader(selected = selected)
                val canGoBackward = monthAtLeast(displayed.minusMonth(), minDate)
                val canGoForward = monthAtMost(displayed.plusMonth(), maxDate)
                MonthNavigator(
                    yearMonth = displayed,
                    canGoBackward = canGoBackward,
                    canGoForward = canGoForward,
                    onPrev = { if (canGoBackward) displayed = displayed.minusMonth() },
                    onNext = { if (canGoForward) displayed = displayed.plusMonth() }
                )
                WeekdayHeader()
                MonthGrid(
                    yearMonth = displayed,
                    today = today,
                    selected = selected,
                    minDate = minDate,
                    maxDate = maxDate,
                    onDayClick = { day ->
                        if (isOutOfRange(day, minDate, maxDate)) return@MonthGrid
                        selected = day
                    }
                )
                FooterButtons(
                    canConfirm = selected != null,
                    onCancel = onDismiss,
                    onConfirm = { selected?.let(onConfirm) }
                )
            }
        }
    }
}

// ----- Header -----

@Composable
private fun PickerHeader(selected: LocalDate?) {
    val eyebrow = stringResource(Res.string.date_picker_eyebrow)
    val placeholder = stringResource(Res.string.date_picker_placeholder)
    val headline = selected?.let { formatLong(it) } ?: placeholder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignTokens.primary500)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = DesignTokens.primary900,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = headline,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            lineHeight = 30.sp
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DesignTokens.neutral900.copy(alpha = 0.35f))
        )
    }
}

// ----- Month nav -----

@Composable
private fun MonthNavigator(
    yearMonth: YearMonth,
    canGoBackward: Boolean,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavChevron(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            cd = stringResource(Res.string.date_picker_prev_month_cd),
            enabled = canGoBackward,
            onClick = onPrev
        )
        Text(
            modifier = Modifier.weight(1f),
            text = "${MONTH_NAMES[yearMonth.month - 1]} ${yearMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        NavChevron(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            cd = stringResource(Res.string.date_picker_next_month_cd),
            enabled = canGoForward,
            onClick = onNext
        )
    }
}

@Composable
private fun NavChevron(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cd: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    }
    val base = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(container)
    val tappable = if (enabled) base.clickable(onClick = onClick) else base
    Box(modifier = tappable, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = cd,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ----- Weekday row -----

@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        WEEKDAYS.forEach { letter ->
            Text(
                modifier = Modifier.weight(1f),
                text = letter,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ----- Month grid -----

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit
) {
    // Sun=0, Mon=1, ..., Sat=6 — kotlin DayOfWeek has Mon=0..Sun=6, so shift by +1.
    val monday0 = LocalDate(yearMonth.year, yearMonth.month, 1).dayOfWeek.ordinal
    val firstColumn = (monday0 + 1) % DAYS_PER_WEEK
    val days = lengthOfMonth(yearMonth.year, yearMonth.month)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        repeat(WEEKS_PER_GRID) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(DAYS_PER_WEEK) { col ->
                    val dayNumber = row * DAYS_PER_WEEK + col - firstColumn + 1
                    val day = if (dayNumber in 1..days) {
                        LocalDate(yearMonth.year, yearMonth.month, dayNumber)
                    } else {
                        null
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DayCell(
                            day = day,
                            today = today,
                            selected = selected,
                            minDate = minDate,
                            maxDate = maxDate,
                            onClick = onDayClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    today: LocalDate,
    selected: LocalDate?,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    onClick: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            val isSelected = day == selected
            val isToday = day == today
            val isDisabled = isOutOfRange(day, minDate, maxDate)
            DayBadge(
                day = day,
                isSelected = isSelected,
                isToday = isToday,
                isDisabled = isDisabled,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun DayBadge(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isDisabled: Boolean,
    onClick: (LocalDate) -> Unit
) {
    val base = Modifier.size(40.dp).clip(CircleShape)
    val ring = when {
        isSelected -> base.background(DesignTokens.primary500)
        isToday -> base.border(1.5.dp, DesignTokens.primary500, CircleShape)
        else -> base
    }
    val tappable = if (isDisabled) ring else ring.clickable { onClick(day) }
    Box(
        modifier = tappable,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            color = dayTextColor(isSelected, isToday, isDisabled),
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun dayTextColor(
    isSelected: Boolean,
    isToday: Boolean,
    isDisabled: Boolean
): Color = when {
    isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    isSelected -> MaterialTheme.colorScheme.onPrimary
    isToday -> DesignTokens.primary600
    else -> MaterialTheme.colorScheme.onSurface
}

// ----- Footer -----

// OK on the right (primary action), Cancel on the left — opposite of the
// range picker's end-aligned row, so the saffron CTA lands under the thumb.
@Composable
private fun FooterButtons(
    canConfirm: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.radiusFull))
                .clickable(onClick = onCancel)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            text = stringResource(Res.string.common_cancel),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val applyBg = if (canConfirm) DesignTokens.primary500 else DesignTokens.primary100
        val applyFg = if (canConfirm) DesignTokens.neutral900 else DesignTokens.neutral400
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.radiusFull))
                .background(applyBg)
                .clickable(enabled = canConfirm, onClick = onConfirm)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            text = stringResource(Res.string.common_ok),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = applyFg
        )
    }
}

// ----- Helpers -----

private data class YearMonth(val year: Int, val month: Int) {
    fun plusMonth(): YearMonth = if (month == 12) {
        YearMonth(year + 1, 1)
    } else {
        YearMonth(year, month + 1)
    }
    fun minusMonth(): YearMonth = if (month == 1) {
        YearMonth(year - 1, 12)
    } else {
        YearMonth(year, month - 1)
    }
}

private fun isOutOfRange(day: LocalDate, minDate: LocalDate?, maxDate: LocalDate?): Boolean {
    val belowMin = minDate != null && day < minDate
    val aboveMax = maxDate != null && day > maxDate
    return belowMin || aboveMax
}

// "After navigating to `target`, would the visible month still be ≥ minDate's month?"
private fun monthAtLeast(target: YearMonth, minDate: LocalDate?): Boolean =
    minDate == null ||
        target.year > minDate.year ||
        (target.year == minDate.year && target.month >= minDate.monthNumber)

// "After navigating to `target`, would the visible month still be ≤ maxDate's month?"
private fun monthAtMost(target: YearMonth, maxDate: LocalDate?): Boolean =
    maxDate == null ||
        target.year < maxDate.year ||
        (target.year == maxDate.year && target.month <= maxDate.monthNumber)

private fun formatLong(date: LocalDate): String =
    "${MONTH_NAMES[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"

private const val DAYS_PER_WEEK = 7
private const val WEEKS_PER_GRID = 6

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

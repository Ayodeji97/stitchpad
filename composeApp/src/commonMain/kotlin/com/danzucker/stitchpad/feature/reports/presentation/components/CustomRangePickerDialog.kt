package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.ui.theme.DesignTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_picker_cancel
import stitchpad.composeapp.generated.resources.reports_picker_confirm
import stitchpad.composeapp.generated.resources.reports_picker_eyebrow
import stitchpad.composeapp.generated.resources.reports_picker_next_month_cd
import stitchpad.composeapp.generated.resources.reports_picker_prev_month_cd
import stitchpad.composeapp.generated.resources.reports_picker_select_end
import stitchpad.composeapp.generated.resources.reports_picker_select_start
import stitchpad.composeapp.generated.resources.reports_range_chip_separator
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun CustomRangePickerDialog(
    initial: CustomRange?,
    timeZone: TimeZone,
    onConfirm: (CustomRange) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember(timeZone) {
        val millis = Clock.System.now().toEpochMilliseconds()
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
    }
    var selectedStart by remember { mutableStateOf(initial?.start) }
    var selectedEnd by remember { mutableStateOf(initial?.end) }
    var displayed by remember {
        val seed = initial?.start ?: today
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
                PickerHeader(start = selectedStart, end = selectedEnd)
                val canGoForward = displayed.year < today.year ||
                    (displayed.year == today.year && displayed.month < today.monthNumber)
                MonthNavigator(
                    yearMonth = displayed,
                    canGoForward = canGoForward,
                    onPrev = { displayed = displayed.minusMonth() },
                    onNext = { if (canGoForward) displayed = displayed.plusMonth() }
                )
                WeekdayHeader()
                MonthGrid(
                    yearMonth = displayed,
                    today = today,
                    selectedStart = selectedStart,
                    selectedEnd = selectedEnd,
                    onDayClick = { day ->
                        if (day > today) return@MonthGrid
                        val (newStart, newEnd) = pickRange(selectedStart, selectedEnd, day)
                        selectedStart = newStart
                        selectedEnd = newEnd
                    }
                )
                FooterButtons(
                    canConfirm = selectedStart != null && selectedEnd != null,
                    onCancel = onDismiss,
                    onConfirm = {
                        val s = selectedStart
                        val e = selectedEnd
                        if (s != null && e != null) {
                            onConfirm(CustomRange(start = s, end = e))
                        }
                    }
                )
            }
        }
    }
}

// ----- Header -----

@Composable
private fun PickerHeader(start: LocalDate?, end: LocalDate?) {
    val sep = stringResource(Res.string.reports_range_chip_separator)
    val eyebrow = stringResource(Res.string.reports_picker_eyebrow)
    val placeholder = when {
        start == null -> stringResource(Res.string.reports_picker_select_start)
        end == null -> stringResource(Res.string.reports_picker_select_end)
        else -> formatRange(start, end, sep)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = placeholder,
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
            cd = stringResource(Res.string.reports_picker_prev_month_cd),
            enabled = true,
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
            cd = stringResource(Res.string.reports_picker_next_month_cd),
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
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onDayClick: (LocalDate) -> Unit
) {
    // Sun=0, Mon=1, ..., Sat=6 — kotlin enum DayOfWeek has Mon=0..Sun=6, so shift by +1.
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
                            selectedStart = selectedStart,
                            selectedEnd = selectedEnd,
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
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onClick: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            val state = dayStateOf(day, today, selectedStart, selectedEnd)
            RangeBackdrop(state)
            DayBadge(day = day, state = state, onClick = onClick)
        }
    }
}

@Composable
private fun RangeBackdrop(state: DayState) {
    if (!state.showLeftBar && !state.showRightBar) return
    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (state.showLeftBar) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (state.showRightBar) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        )
    }
}

@Composable
private fun DayBadge(
    day: LocalDate,
    state: DayState,
    onClick: (LocalDate) -> Unit
) {
    val base = Modifier.size(40.dp).clip(CircleShape)
    val ring = when {
        state.isSelected -> base.background(MaterialTheme.colorScheme.primary)
        state.isToday -> base.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        else -> base
    }
    val tappable = if (state.isFuture) ring else ring.clickable { onClick(day) }
    Box(
        modifier = tappable,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            color = dayTextColor(state),
            fontWeight = if (state.isSelected || state.isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun dayTextColor(state: DayState): Color = when {
    state.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    state.isSelected -> MaterialTheme.colorScheme.onPrimary
    state.isInRange -> MaterialTheme.colorScheme.onPrimaryContainer
    state.isToday -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

private data class DayState(
    val isStart: Boolean,
    val isEnd: Boolean,
    val isInRange: Boolean,
    val hasFullRange: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean
) {
    val isSelected: Boolean get() = isStart || isEnd

    // Range bars only render when both endpoints are picked — drawing a half-bar
    // off a lone start endpoint produced a visible rectangle artifact next to
    // the saffron circle.
    val showLeftBar: Boolean get() = hasFullRange && (isInRange || isEnd)
    val showRightBar: Boolean get() = hasFullRange && (isInRange || isStart)
}

private fun dayStateOf(
    day: LocalDate,
    today: LocalDate,
    start: LocalDate?,
    end: LocalDate?
): DayState = DayState(
    isStart = day == start,
    isEnd = day == end,
    isInRange = isStrictlyBetween(day, start, end),
    hasFullRange = start != null && end != null && start != end,
    isToday = day == today,
    isFuture = day > today
)

private fun isStrictlyBetween(day: LocalDate, start: LocalDate?, end: LocalDate?): Boolean {
    if (start == null || end == null) return false
    return day > start && day < end
}

// ----- Footer -----

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
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.radiusFull))
                .clickable(onClick = onCancel)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            text = stringResource(Res.string.reports_picker_cancel),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        val applyBg = if (canConfirm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
        val applyFg = if (canConfirm) DesignTokens.neutral900 else DesignTokens.neutral400
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.radiusFull))
                .background(applyBg)
                .clickable(enabled = canConfirm, onClick = onConfirm)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            text = stringResource(Res.string.reports_picker_confirm),
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

private fun pickRange(
    start: LocalDate?,
    end: LocalDate?,
    tapped: LocalDate
): Pair<LocalDate?, LocalDate?> = when {
    start == null -> tapped to null
    end == null -> if (tapped >= start) start to tapped else tapped to null
    else -> tapped to null
}

private fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun formatRange(start: LocalDate, end: LocalDate, sep: String): String = when {
    start.year == end.year && start.monthNumber == end.monthNumber ->
        "${MONTH_ABBREV[start.monthNumber - 1]} ${start.dayOfMonth} $sep " +
            "${end.dayOfMonth}, ${end.year}"
    start.year == end.year ->
        "${MONTH_ABBREV[start.monthNumber - 1]} ${start.dayOfMonth} $sep " +
            "${MONTH_ABBREV[end.monthNumber - 1]} ${end.dayOfMonth}, ${end.year}"
    else ->
        "${MONTH_ABBREV[start.monthNumber - 1]} ${start.dayOfMonth}, ${start.year} $sep " +
            "${MONTH_ABBREV[end.monthNumber - 1]} ${end.dayOfMonth}, ${end.year}"
}

private const val DAYS_PER_WEEK = 7
private const val WEEKS_PER_GRID = 6

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

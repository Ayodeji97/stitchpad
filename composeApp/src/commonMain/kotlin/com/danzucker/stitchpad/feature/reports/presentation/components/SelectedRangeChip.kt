package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.ui.theme.DesignTokens
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_range_chip_clear_cd
import stitchpad.composeapp.generated.resources.reports_range_chip_separator

@Composable
fun SelectedRangeChip(
    range: CustomRange,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val separator = stringResource(Res.string.reports_range_chip_separator)
    val clearCd = stringResource(Res.string.reports_range_chip_clear_cd)
    val rangeText = formatRange(range, separator)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(DesignTokens.primary50)
            .border(
                width = 1.dp,
                color = DesignTokens.primary200,
                shape = RoundedCornerShape(DesignTokens.radiusFull)
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = null,
            tint = DesignTokens.primary700,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = rangeText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = DesignTokens.primary800
        )
        ClearButton(contentDescription = clearCd, onClick = onClear)
    }
}

@Composable
private fun ClearButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(DesignTokens.primary200)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = contentDescription,
            tint = DesignTokens.primary800,
            modifier = Modifier.size(13.dp)
        )
    }
}

private fun formatRange(range: CustomRange, separator: String): String {
    val start = range.start
    val end = range.end
    return when {
        start.year == end.year && start.monthNumber == end.monthNumber ->
            "${monthAbbrev(start)} ${start.dayOfMonth} $separator ${end.dayOfMonth}, ${end.year}"
        start.year == end.year ->
            "${monthAbbrev(start)} ${start.dayOfMonth} $separator " +
                "${monthAbbrev(end)} ${end.dayOfMonth}, ${end.year}"
        else ->
            "${monthAbbrev(start)} ${start.dayOfMonth}, ${start.year} $separator " +
                "${monthAbbrev(end)} ${end.dayOfMonth}, ${end.year}"
    }
}

private fun monthAbbrev(date: LocalDate): String = MONTH_ABBREV[date.monthNumber - 1]

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

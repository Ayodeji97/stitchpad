package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_picker_cancel
import stitchpad.composeapp.generated.resources.reports_picker_confirm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangePickerDialog(
    initial: CustomRange?,
    timeZone: TimeZone,
    onConfirm: (CustomRange) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.atStartOfDayIn(timeZone)?.toEpochMilliseconds(),
        initialSelectedEndDateMillis = initial?.end?.atStartOfDayIn(timeZone)?.toEpochMilliseconds()
    )
    val canConfirm by remember {
        derivedStateOf {
            pickerState.selectedStartDateMillis != null &&
                pickerState.selectedEndDateMillis != null
        }
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val startMillis = pickerState.selectedStartDateMillis
                    val endMillis = pickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        onConfirm(
                            CustomRange(
                                start = millisToLocalDate(startMillis, timeZone),
                                end = millisToLocalDate(endMillis, timeZone)
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(Res.string.reports_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.reports_picker_cancel))
            }
        }
    ) {
        DateRangePicker(state = pickerState)
    }
}

private fun millisToLocalDate(millis: Long, tz: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date

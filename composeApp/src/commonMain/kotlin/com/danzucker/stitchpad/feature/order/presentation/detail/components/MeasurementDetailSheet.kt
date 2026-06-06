package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.measurement.presentation.filledPreviewFields
import com.danzucker.stitchpad.feature.measurement.presentation.formatMeasurementValue
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gender_female
import stitchpad.composeapp.generated.resources.gender_male
import stitchpad.composeapp.generated.resources.measurement_detail_change
import stitchpad.composeapp.generated.resources.measurement_detail_empty
import stitchpad.composeapp.generated.resources.measurement_detail_title
import stitchpad.composeapp.generated.resources.measurement_detail_view_full
import stitchpad.composeapp.generated.resources.order_detail_measured_caption

/**
 * Read-only quick-view of the measurement linked to an order. Lists every filled
 * value (template fields first, then custom). "View full measurement" opens the
 * customer's measurement screen; "Change measurement" reopens the link picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailSheet(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    onViewFull: () -> Unit,
    onChangeMeasurement: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        MeasurementDetailSheetContent(
            measurement = measurement,
            customFieldLabels = customFieldLabels,
            onViewFull = onViewFull,
            onChangeMeasurement = onChangeMeasurement,
        )
    }
}

@Composable
private fun MeasurementDetailSheetContent(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    onViewFull: () -> Unit,
    onChangeMeasurement: () -> Unit,
) {
    val unitSuffix = if (measurement.unit == MeasurementUnit.INCHES) "″" else "cm"
    val genderLabel = stringResource(
        when (measurement.gender) {
            CustomerGender.MALE -> Res.string.gender_male
            CustomerGender.FEMALE -> Res.string.gender_female
        },
    )
    val fields = measurement.filledPreviewFields(customFieldLabels)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.measurement_detail_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$genderLabel · " + stringResource(
                Res.string.order_detail_measured_caption,
                formatShortDate(measurement.dateTaken),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (fields.isEmpty()) {
            Text(
                text = stringResource(Res.string.measurement_detail_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            fields.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${formatMeasurementValue(field.value)}$unitSuffix",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.space2),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            OutlinedButton(
                onClick = onChangeMeasurement,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.measurement_detail_change))
            }
            Button(
                onClick = onViewFull,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.measurement_detail_view_full))
            }
        }
    }
}

private fun formatShortDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}

// region — Previews (content only; ModalBottomSheet needs an Activity)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailSheetLightPreview() {
    StitchPadTheme {
        Surface {
            MeasurementDetailSheetContent(
                measurement = Measurement(
                    id = "m1",
                    customerId = "c1",
                    gender = CustomerGender.FEMALE,
                    fields = mapOf("bust_circumference" to 36.0, "waist" to 28.0, "hip_circumference" to 40.0),
                    unit = MeasurementUnit.INCHES,
                    notes = null,
                    dateTaken = 1_743_638_400_000L,
                    createdAt = 0L,
                ),
                customFieldLabels = emptyMap(),
                onViewFull = {},
                onChangeMeasurement = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailSheetDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            MeasurementDetailSheetContent(
                measurement = Measurement(
                    id = "m1",
                    customerId = "c1",
                    gender = CustomerGender.MALE,
                    fields = mapOf("chest" to 42.0, "trouser_waist" to 34.0),
                    unit = MeasurementUnit.INCHES,
                    notes = null,
                    dateTaken = 1_743_638_400_000L,
                    createdAt = 0L,
                ),
                customFieldLabels = emptyMap(),
                onViewFull = {},
                onChangeMeasurement = {},
            )
        }
    }
}

// endregion

package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gender_female
import stitchpad.composeapp.generated.resources.gender_male
import stitchpad.composeapp.generated.resources.measurement_picker_create_new
import stitchpad.composeapp.generated.resources.measurement_picker_empty
import stitchpad.composeapp.generated.resources.measurement_picker_taken_caption
import stitchpad.composeapp.generated.resources.measurement_picker_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementPickerSheet(
    measurements: List<Measurement>,
    selectedMeasurementId: String?,
    onSelectMeasurement: (measurementId: String) -> Unit,
    onCreateNewClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        MeasurementPickerSheetContent(
            measurements = measurements,
            selectedMeasurementId = selectedMeasurementId,
            onSelectMeasurement = onSelectMeasurement,
            onCreateNewClick = onCreateNewClick,
        )
    }
}

@Composable
private fun MeasurementPickerSheetContent(
    measurements: List<Measurement>,
    selectedMeasurementId: String?,
    onSelectMeasurement: (measurementId: String) -> Unit,
    onCreateNewClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2)) {
        Text(
            text = stringResource(Res.string.measurement_picker_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(DesignTokens.space3))

        if (measurements.isEmpty()) {
            Text(
                text = stringResource(Res.string.measurement_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            measurements.forEach { measurement ->
                MeasurementRow(
                    measurement = measurement,
                    isSelected = measurement.id == selectedMeasurementId,
                    onClick = { onSelectMeasurement(measurement.id) },
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space3))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(DesignTokens.space2))

        TextButton(
            onClick = onCreateNewClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
            Spacer(Modifier.size(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.measurement_picker_create_new),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(DesignTokens.space2))
    }
}

@Composable
private fun MeasurementRow(
    measurement: Measurement,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = DesignTokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(genderLabelRes(measurement.gender)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    Res.string.measurement_picker_taken_caption,
                    formatShortDate(measurement.dateTaken),
                    measurement.fields.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
        }
    }
}

private fun genderLabelRes(gender: CustomerGender) = when (gender) {
    CustomerGender.MALE -> Res.string.gender_male
    CustomerGender.FEMALE -> Res.string.gender_female
}

private fun formatShortDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementPickerSheetPopulatedLightPreview() {
    StitchPadTheme {
        MeasurementPickerSheetContent(
            measurements = listOf(
                Measurement(
                    id = "m1",
                    customerId = "c1",
                    gender = CustomerGender.MALE,
                    fields = mapOf("Chest" to 42.0, "Waist" to 34.0, "Length" to 38.0),
                    unit = MeasurementUnit.INCHES,
                    notes = null,
                    dateTaken = 1_746_316_800_000L,
                    createdAt = 1_746_316_800_000L,
                ),
                Measurement(
                    id = "m2",
                    customerId = "c1",
                    gender = CustomerGender.FEMALE,
                    fields = mapOf("Bust" to 38.0, "Waist" to 28.0),
                    unit = MeasurementUnit.INCHES,
                    notes = null,
                    dateTaken = 1_743_638_400_000L,
                    createdAt = 1_743_638_400_000L,
                ),
            ),
            selectedMeasurementId = "m1",
            onSelectMeasurement = {},
            onCreateNewClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementPickerSheetEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        MeasurementPickerSheetContent(
            measurements = emptyList(),
            selectedMeasurementId = null,
            onSelectMeasurement = {},
            onCreateNewClick = {},
        )
    }
}

package com.danzucker.stitchpad.feature.customer.presentation.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.measurement.presentation.measurementDisplayName
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.measurement_add_sheet_create_new
import stitchpad.composeapp.generated.resources.measurement_add_sheet_existing
import stitchpad.composeapp.generated.resources.measurement_add_sheet_title
import stitchpad.composeapp.generated.resources.measurement_gender_men
import stitchpad.composeapp.generated.resources.measurement_gender_women
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches

/**
 * "Add a measurement" sheet shown when the customer already has measurements. Lets the
 * tailor edit an existing measurement (so they stop creating duplicates) or create a new
 * one. Only opened from [CustomerDetailAction.OnAddMeasurementClick] when the list is
 * non-empty — an empty list goes straight to a blank new form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementSheet(
    measurements: List<Measurement>,
    onEditMeasurement: (Measurement) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space4)
                .padding(bottom = DesignTokens.space6),
        ) {
            Text(
                text = stringResource(Res.string.measurement_add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.measurement_add_sheet_existing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = DesignTokens.space3, bottom = DesignTokens.space2),
            )
            measurements.forEachIndexed { index, measurement ->
                val genderWord = stringResource(
                    if (measurement.gender == CustomerGender.FEMALE) {
                        Res.string.measurement_gender_women
                    } else {
                        Res.string.measurement_gender_men
                    },
                )
                val unitLabel = stringResource(
                    if (measurement.unit == MeasurementUnit.INCHES) {
                        Res.string.measurement_unit_inches
                    } else {
                        Res.string.measurement_unit_cm
                    },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditMeasurement(measurement) }
                        .padding(vertical = DesignTokens.space3),
                ) {
                    Text(
                        text = measurementDisplayName(measurement, index + 1),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$genderWord · $unitLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onCreateNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.space4),
            ) {
                Text(stringResource(Res.string.measurement_add_sheet_create_new))
            }
        }
    }
}

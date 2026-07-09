package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.MeasurementsPickerRow
import com.danzucker.stitchpad.feature.dashboard.presentation.model.MeasurementsPickerUi
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_search_hint
import stitchpad.composeapp.generated.resources.measurements_picker_add
import stitchpad.composeapp.generated.resources.measurements_picker_count_many
import stitchpad.composeapp.generated.resources.measurements_picker_count_one
import stitchpad.composeapp.generated.resources.measurements_picker_no_results
import stitchpad.composeapp.generated.resources.measurements_picker_none
import stitchpad.composeapp.generated.resources.measurements_picker_subtitle
import stitchpad.composeapp.generated.resources.measurements_picker_title

/**
 * Dashboard "Measurements" shortcut sheet — a searchable customer picker.
 * Each row shows a measurement count; tapping a row with exactly one saved
 * measurement routes straight to its detail view, a row with zero routes to
 * "Add measurement" for that customer, and a row with 2+ routes to the
 * customer's own measurements list (handled by the caller via
 * [MeasurementsPickerRow]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsPickerSheet(
    picker: MeasurementsPickerUi,
    onQueryChange: (String) -> Unit,
    onRowClick: (MeasurementsPickerRow) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MeasurementsPickerContent(picker, onQueryChange, onRowClick)
    }
}

@Composable
private fun MeasurementsPickerContent(
    picker: MeasurementsPickerUi,
    onQueryChange: (String) -> Unit,
    onRowClick: (MeasurementsPickerRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space6),
    ) {
        Text(
            text = stringResource(Res.string.measurements_picker_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.measurements_picker_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space3))
        OutlinedTextField(
            value = picker.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.customer_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(DesignTokens.space2))
        when {
            picker.isLoading -> Box(
                Modifier.fillMaxWidth().padding(vertical = DesignTokens.space6),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            picker.filteredRows.isEmpty() -> Text(
                text = stringResource(Res.string.measurements_picker_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = DesignTokens.space4),
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(picker.filteredRows, key = { it.customerId }) { row ->
                    PickerRow(row = row, onClick = { onRowClick(row) })
                }
            }
        }
    }
}

@Composable
private fun PickerRow(row: MeasurementsPickerRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DesignTokens.space3),
    ) {
        CustomerAvatar(name = row.name, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    row.measurementCount == 0 -> stringResource(Res.string.measurements_picker_none)
                    row.measurementCount == 1 -> stringResource(Res.string.measurements_picker_count_one)
                    else -> stringResource(Res.string.measurements_picker_count_many, row.measurementCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.measurementCount == 0) {
            Text(
                text = stringResource(Res.string.measurements_picker_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun previewRows() = listOf(
    MeasurementsPickerRow(
        customerId = "c1",
        name = "Fola Sunday",
        measurementCount = 0,
        singleMeasurementId = null,
    ),
    MeasurementsPickerRow(
        customerId = "c2",
        name = "Bimbo Dann",
        measurementCount = 1,
        singleMeasurementId = "m1",
    ),
    MeasurementsPickerRow(
        customerId = "c3",
        name = "Mr Tunde",
        measurementCount = 3,
        singleMeasurementId = null,
    ),
)

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementsPickerContentLightPreview() {
    StitchPadTheme(darkTheme = false) {
        MeasurementsPickerContent(
            picker = MeasurementsPickerUi(isLoading = false, rows = previewRows()),
            onQueryChange = {},
            onRowClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementsPickerContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        MeasurementsPickerContent(
            picker = MeasurementsPickerUi(isLoading = false, rows = previewRows()),
            onQueryChange = {},
            onRowClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementsPickerContentLoadingPreview() {
    StitchPadTheme {
        MeasurementsPickerContent(
            picker = MeasurementsPickerUi(isLoading = true),
            onQueryChange = {},
            onRowClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementsPickerContentEmptyPreview() {
    StitchPadTheme {
        MeasurementsPickerContent(
            picker = MeasurementsPickerUi(isLoading = false, query = "zzz", rows = previewRows()),
            onQueryChange = {},
            onRowClick = {},
        )
    }
}

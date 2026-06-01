package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_archive_dialog_body
import stitchpad.composeapp.generated.resources.custom_field_archive_dialog_confirm
import stitchpad.composeapp.generated.resources.custom_field_archive_dialog_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_cancel

@Composable
fun ConfirmArchiveDialog(
    field: CustomMeasurementField,
    onDismiss: () -> Unit,
    onConfirm: (fieldId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.custom_field_archive_dialog_title)) },
        text = {
            Text(stringResource(Res.string.custom_field_archive_dialog_body, field.label))
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(field.id) },
            ) {
                Text(
                    text = stringResource(Res.string.custom_field_archive_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.custom_field_sheet_cancel))
            }
        },
    )
}

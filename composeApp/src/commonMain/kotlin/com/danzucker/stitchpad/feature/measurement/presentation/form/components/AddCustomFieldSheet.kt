package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_sheet_add_subtitle
import stitchpad.composeapp.generated.resources.custom_field_sheet_add_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_cancel
import stitchpad.composeapp.generated.resources.custom_field_sheet_edit_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_gender_both
import stitchpad.composeapp.generated.resources.custom_field_sheet_gender_female
import stitchpad.composeapp.generated.resources.custom_field_sheet_gender_male
import stitchpad.composeapp.generated.resources.custom_field_sheet_genders_label
import stitchpad.composeapp.generated.resources.custom_field_sheet_label
import stitchpad.composeapp.generated.resources.custom_field_sheet_label_placeholder
import stitchpad.composeapp.generated.resources.custom_field_sheet_save
import stitchpad.composeapp.generated.resources.custom_field_sheet_save_changes
import stitchpad.composeapp.generated.resources.custom_field_sheet_save_with_value
import stitchpad.composeapp.generated.resources.custom_field_sheet_value
import stitchpad.composeapp.generated.resources.custom_field_sheet_value_placeholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomFieldSheet(
    initial: CustomMeasurementField?,
    unitSuffix: String,
    onDismiss: () -> Unit,
    onSave: (id: String?, label: String, genders: Set<CustomerGender>, initialValue: String) -> Unit,
    modifier: Modifier = Modifier,
    bottomExtra: @Composable (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var initialValue by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(initial?.genders ?: setOf(CustomerGender.FEMALE, CustomerGender.MALE))
    }

    val titleRes = if (initial == null) {
        Res.string.custom_field_sheet_add_title
    } else {
        Res.string.custom_field_sheet_edit_title
    }
    val saveRes = if (initial == null && initialValue.isNotBlank()) {
        Res.string.custom_field_sheet_save_with_value
    } else if (initial == null) {
        Res.string.custom_field_sheet_save
    } else {
        Res.string.custom_field_sheet_save_changes
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (initial == null) {
                Text(
                    text = stringResource(Res.string.custom_field_sheet_add_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = label,
                onValueChange = { if (it.length <= MAX_LABEL_LENGTH) label = it },
                label = { Text(stringResource(Res.string.custom_field_sheet_label)) },
                placeholder = { Text(stringResource(Res.string.custom_field_sheet_label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (initial == null) {
                OutlinedTextField(
                    value = initialValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                        val dotCount = filtered.count { it == '.' }
                        if (dotCount <= 1) {
                            initialValue = filtered
                        }
                    },
                    label = { Text(stringResource(Res.string.custom_field_sheet_value)) },
                    placeholder = { Text(stringResource(Res.string.custom_field_sheet_value_placeholder)) },
                    suffix = { Text(unitSuffix) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = stringResource(Res.string.custom_field_sheet_genders_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GenderChip(
                    label = stringResource(Res.string.custom_field_sheet_gender_female),
                    selected = selected == setOf(CustomerGender.FEMALE),
                    onClick = { selected = setOf(CustomerGender.FEMALE) },
                    modifier = Modifier.weight(1f),
                )
                GenderChip(
                    label = stringResource(Res.string.custom_field_sheet_gender_male),
                    selected = selected == setOf(CustomerGender.MALE),
                    onClick = { selected = setOf(CustomerGender.MALE) },
                    modifier = Modifier.weight(1f),
                )
                GenderChip(
                    label = stringResource(Res.string.custom_field_sheet_gender_both),
                    selected = selected == setOf(CustomerGender.FEMALE, CustomerGender.MALE),
                    onClick = { selected = setOf(CustomerGender.FEMALE, CustomerGender.MALE) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(DesignTokens.space2))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.custom_field_sheet_cancel)) }
                Button(
                    onClick = { onSave(initial?.id, label, selected, initialValue) },
                    enabled = label.isNotBlank() && selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ) { Text(stringResource(saveRes)) }
            }

            bottomExtra?.let {
                Spacer(Modifier.height(DesignTokens.space2))
                it()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        },
        modifier = modifier,
    )
}

private const val MAX_LABEL_LENGTH = 30

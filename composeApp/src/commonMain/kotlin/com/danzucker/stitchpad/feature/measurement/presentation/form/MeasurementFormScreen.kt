package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.garment_type_agbada
import stitchpad.composeapp.generated.resources.garment_type_blouse
import stitchpad.composeapp.generated.resources.garment_type_buba_and_skirt
import stitchpad.composeapp.generated.resources.garment_type_dress
import stitchpad.composeapp.generated.resources.garment_type_senator_kaftan
import stitchpad.composeapp.generated.resources.garment_type_shirt
import stitchpad.composeapp.generated.resources.garment_type_suit
import stitchpad.composeapp.generated.resources.garment_type_trouser
import stitchpad.composeapp.generated.resources.measurement_add_title
import stitchpad.composeapp.generated.resources.measurement_edit_title
import stitchpad.composeapp.generated.resources.measurement_garment_type_label
import stitchpad.composeapp.generated.resources.measurement_notes_label
import stitchpad.composeapp.generated.resources.measurement_notes_placeholder
import stitchpad.composeapp.generated.resources.measurement_save_button
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches

@Composable
fun MeasurementFormRoot(onNavigateBack: () -> Unit) {
    val viewModel: MeasurementFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementFormEvent.NavigateBack -> onNavigateBack()
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(MeasurementFormAction.OnErrorDismiss)
        }
    }

    MeasurementFormScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementFormScreen(
    state: MeasurementFormState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (MeasurementFormAction) -> Unit
) {
    val title = if (state.isEditMode) {
        stringResource(Res.string.measurement_edit_title)
    } else {
        stringResource(Res.string.measurement_add_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(MeasurementFormAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space4,
                bottom = DesignTokens.space8
            ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                GarmentTypePicker(
                    selected = state.garmentType,
                    label = stringResource(Res.string.measurement_garment_type_label),
                    onSelected = { onAction(MeasurementFormAction.OnGarmentTypeChange(it)) }
                )
            }

            item {
                UnitBadge(unit = state.unit)
            }

            items(state.garmentType.fieldLabels) { label ->
                MeasurementField(
                    label = label,
                    value = state.fields[label] ?: "",
                    onValueChange = { onAction(MeasurementFormAction.OnFieldChange(label, it)) }
                )
            }

            item {
                MeasurementTextField(
                    value = state.notes,
                    onValueChange = { onAction(MeasurementFormAction.OnNotesChange(it)) },
                    label = stringResource(Res.string.measurement_notes_label),
                    placeholder = stringResource(Res.string.measurement_notes_placeholder),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }

            item {
                Spacer(Modifier.height(DesignTokens.space2))
                Button(
                    onClick = { onAction(MeasurementFormAction.OnSaveClick) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(Res.string.measurement_save_button),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarmentTypePicker(
    selected: GarmentType,
    label: String,
    onSelected: (GarmentType) -> Unit
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DesignTokens.space2)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(GarmentType.entries) { type ->
                val isSelected = selected == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(type) },
                    label = {
                        Text(
                            text = garmentTypeLabel(type),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = DesignTokens.primary600,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (isSelected) {
                        BorderStroke(1.dp, DesignTokens.primary500)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    }
                )
            }
        }
    }
}

@Composable
private fun UnitBadge(unit: MeasurementUnit) {
    val label = if (unit == MeasurementUnit.INCHES) {
        stringResource(Res.string.measurement_unit_inches)
    } else {
        stringResource(Res.string.measurement_unit_cm)
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space1)
        )
    }
}

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    MeasurementTextField(
        value = value,
        onValueChange = { newVal ->
            val filtered = newVal.filter { it.isDigit() || it == '.' }
            val dotCount = filtered.count { it == '.' }
            if (dotCount <= 1) onValueChange(filtered)
        },
        label = label,
        placeholder = "0",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DesignTokens.space1)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.ContainerBox(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = RoundedCornerShape(DesignTokens.radiusMd),
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp
                        )
                    }
                )
            }
        )
    }
}

@Composable
private fun garmentTypeLabel(garmentType: GarmentType): String = when (garmentType) {
    GarmentType.AGBADA -> stringResource(Res.string.garment_type_agbada)
    GarmentType.SENATOR_KAFTAN -> stringResource(Res.string.garment_type_senator_kaftan)
    GarmentType.BUBA_AND_SKIRT -> stringResource(Res.string.garment_type_buba_and_skirt)
    GarmentType.DRESS -> stringResource(Res.string.garment_type_dress)
    GarmentType.TROUSER -> stringResource(Res.string.garment_type_trouser)
    GarmentType.SHIRT -> stringResource(Res.string.garment_type_shirt)
    GarmentType.BLOUSE -> stringResource(Res.string.garment_type_blouse)
    GarmentType.SUIT -> stringResource(Res.string.garment_type_suit)
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenAddPreview() {
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                garmentType = GarmentType.DRESS,
                fields = GarmentType.DRESS.fieldLabels.associateWith { "" }
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenEditPreview() {
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                isEditMode = true,
                garmentType = GarmentType.DRESS,
                fields = mapOf(
                    "Bust" to "36",
                    "Waist" to "28",
                    "Hip" to "38",
                    "Shoulder Width" to "14",
                    "Arm Length" to "22",
                    "Dress Length" to "48"
                ),
                unit = MeasurementUnit.INCHES,
                notes = "Prefers loose fit"
            ),
            onAction = {}
        )
    }
}

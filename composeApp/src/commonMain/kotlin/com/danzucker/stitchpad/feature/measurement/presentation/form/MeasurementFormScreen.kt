package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.model.MeasurementSection
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.AddCustomFieldSheet
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.ConfirmArchiveDialog
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_add_button
import stitchpad.composeapp.generated.resources.custom_field_delete_content_description
import stitchpad.composeapp.generated.resources.custom_field_empty_caption
import stitchpad.composeapp.generated.resources.custom_field_locked_caption
import stitchpad.composeapp.generated.resources.custom_field_section_pill_first_month
import stitchpad.composeapp.generated.resources.custom_field_section_pill_locked
import stitchpad.composeapp.generated.resources.custom_field_section_pill_pro
import stitchpad.composeapp.generated.resources.custom_field_section_subtitle
import stitchpad.composeapp.generated.resources.custom_field_section_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_archive
import stitchpad.composeapp.generated.resources.gender_female
import stitchpad.composeapp.generated.resources.gender_male
import stitchpad.composeapp.generated.resources.measurement_add_note
import stitchpad.composeapp.generated.resources.measurement_custom_step
import stitchpad.composeapp.generated.resources.measurement_add_title
import stitchpad.composeapp.generated.resources.measurement_create_flow_save_button
import stitchpad.composeapp.generated.resources.measurement_edit_title
import stitchpad.composeapp.generated.resources.measurement_gender_label
import stitchpad.composeapp.generated.resources.measurement_next
import stitchpad.composeapp.generated.resources.measurement_notes_label
import stitchpad.composeapp.generated.resources.measurement_notes_placeholder
import stitchpad.composeapp.generated.resources.measurement_previous
import stitchpad.composeapp.generated.resources.measurement_save_button
import stitchpad.composeapp.generated.resources.measurement_section_of
import stitchpad.composeapp.generated.resources.measurement_show_less
import stitchpad.composeapp.generated.resources.measurement_show_more_count
import stitchpad.composeapp.generated.resources.measurement_skip_for_now
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches

@Composable
fun MeasurementFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: MeasurementFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementFormEvent.NavigateBack -> onNavigateBack()
            // Skip lands on the same destination as a successful save: the
            // customer (already persisted) detail. No measurement is written.
            MeasurementFormEvent.SkipMeasurements -> onNavigateBack()
            MeasurementFormEvent.NavigateToUpgrade -> onNavigateToUpgrade()
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

@Suppress("CyclomaticComplexMethod")
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
    val unitSuffix = if (state.unit == MeasurementUnit.INCHES) "in" else "cm"
    val canSave = state.canSave
    val focusManager = LocalFocusManager.current

    // +1 trailing page for the custom-measurements step (index == sections.size).
    // When sections is empty the pager block below isn't rendered, so the lone
    // page is harmless.
    val pagerState = rememberPagerState(pageCount = { state.sections.size + 1 })

    // Swipe → notify ViewModel
    LaunchedEffect(pagerState.currentPage) {
        onAction(MeasurementFormAction.OnSectionChange(pagerState.currentPage))
    }

    // Tab / button → animate pager
    LaunchedEffect(state.currentSectionIndex) {
        if (pagerState.currentPage != state.currentSectionIndex) {
            pagerState.animateScrollToPage(state.currentSectionIndex)
        }
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
                actions = {
                    UnitBadge(unit = state.unit)
                    Spacer(Modifier.width(DesignTokens.space3))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // ── Fixed header ─────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = DesignTokens.space4)) {
                Spacer(Modifier.height(DesignTokens.space4))
                GenderSelector(
                    selected = state.gender,
                    label = stringResource(Res.string.measurement_gender_label),
                    onSelected = { onAction(MeasurementFormAction.OnGenderChange(it)) }
                )
                if (state.sections.isNotEmpty()) {
                    Spacer(Modifier.height(DesignTokens.space4))
                    SectionProgressRow(
                        sections = state.sections,
                        currentIndex = state.currentSectionIndex,
                        fields = state.fields,
                        customLocked = !state.canUseCustomMeasurements,
                        // Mirror the dot "has data" rule: light the pill when any
                        // custom field holds a value that will actually persist.
                        customHasData = state.customFields.any { f ->
                            (state.fields[f.id]?.toDoubleOrNull() ?: 0.0) > 0.0
                        },
                        onJumpToSection = { index ->
                            onAction(MeasurementFormAction.OnSectionChange(index))
                        }
                    )
                    Spacer(Modifier.height(DesignTokens.space2))
                }
            }
            if (state.sections.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Swipeable section fields ──────────────────────────────────
            if (state.sections.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = DesignTokens.space4,
                                vertical = DesignTokens.space3
                            )
                    ) {
                        if (pageIndex < state.sections.size) {
                            val section = state.sections[pageIndex]
                            val essentialFields = section.fields.filter { it.isEssential }
                            val extraFields = section.fields.filter { !it.isEssential }
                            val isExpanded =
                                pageIndex != state.currentSectionIndex || state.isCurrentSectionExpanded

                            essentialFields.forEach { field ->
                                MeasurementFieldInput(
                                    field = field,
                                    value = state.fields[field.key] ?: "",
                                    unitSuffix = unitSuffix,
                                    onValueChange = {
                                        onAction(MeasurementFormAction.OnFieldChange(field.key, it))
                                    }
                                )
                            }

                            if (isExpanded) {
                                extraFields.forEach { field ->
                                    MeasurementFieldInput(
                                        field = field,
                                        value = state.fields[field.key] ?: "",
                                        unitSuffix = unitSuffix,
                                        onValueChange = {
                                            onAction(MeasurementFormAction.OnFieldChange(field.key, it))
                                        }
                                    )
                                }
                            }

                            if (extraFields.isNotEmpty() && pageIndex == state.currentSectionIndex) {
                                ShowMoreToggle(
                                    isExpanded = state.isCurrentSectionExpanded,
                                    extraCount = extraFields.size,
                                    onClick = { onAction(MeasurementFormAction.OnToggleShowMore) }
                                )
                            }
                        } else {
                            // Trailing custom-measurements step. Filtering by gender
                            // is handled in the ViewModel; this renders the result.
                            CustomFieldsSection(
                                fields = state.customFields,
                                fieldValues = state.fields,
                                unitSuffix = unitSuffix,
                                canUseCustomMeasurements = state.canUseCustomMeasurements,
                                isEditMode = state.isEditMode,
                                isInWelcomeWindow = state.isInWelcomeWindow,
                                tier = state.tier,
                                onFieldValueChange = { key, value ->
                                    onAction(MeasurementFormAction.OnFieldChange(key, value))
                                },
                                onAddClick = { onAction(MeasurementFormAction.OnAddCustomFieldClick) },
                                onLockedAddClick = { onAction(MeasurementFormAction.OnLockedCustomFieldClick) },
                                onEditField = { id ->
                                    onAction(MeasurementFormAction.OnEditCustomFieldClick(id))
                                },
                                onDeleteField = { id ->
                                    onAction(MeasurementFormAction.OnArchiveCustomFieldRequest(id))
                                },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // ── Fixed footer ──────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = DesignTokens.space4)) {
                if (state.sections.isNotEmpty()) {
                    SectionNavigation(
                        currentIndex = state.currentSectionIndex,
                        totalSections = state.sections.size + 1,
                        onPrevious = { onAction(MeasurementFormAction.OnPreviousSection) },
                        onNext = { onAction(MeasurementFormAction.OnNextSection) }
                    )
                }
                NotesSection(
                    isExpanded = state.isNotesExpanded,
                    notes = state.notes,
                    onToggle = { onAction(MeasurementFormAction.OnToggleNotes) },
                    onNotesChange = { onAction(MeasurementFormAction.OnNotesChange(it)) }
                )
                Spacer(Modifier.height(DesignTokens.space2))
                Button(
                    onClick = { onAction(MeasurementFormAction.OnSaveClick) },
                    enabled = canSave,
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
                                text = if (state.fromCustomerCreation) {
                                    stringResource(Res.string.measurement_create_flow_save_button)
                                } else {
                                    stringResource(Res.string.measurement_save_button)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (state.fromCustomerCreation) {
                    Spacer(Modifier.height(DesignTokens.space1))
                    TextButton(
                        onClick = { onAction(MeasurementFormAction.OnSkipClick) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(Res.string.measurement_skip_for_now),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(DesignTokens.space4))
            }
        }
    }

    when (val sheet = state.customFieldSheet) {
        is CustomFieldSheet.Adding -> AddCustomFieldSheet(
            initial = null,
            draft = sheet.draft,
            unitSuffix = unitSuffix,
            onDismiss = { onAction(MeasurementFormAction.OnCustomFieldSheetDismiss) },
            onLabelChange = { onAction(MeasurementFormAction.OnCustomFieldDraftLabelChange(it)) },
            onInitialValueChange = {
                onAction(MeasurementFormAction.OnCustomFieldDraftInitialValueChange(it))
            },
            onGendersChange = { onAction(MeasurementFormAction.OnCustomFieldDraftGendersChange(it)) },
            onSave = {
                onAction(
                    MeasurementFormAction.OnSaveCustomField(
                        id = null,
                        label = sheet.draft.label,
                        genders = sheet.draft.genders,
                        initialValue = sheet.draft.initialValue,
                    )
                )
            },
        )
        is CustomFieldSheet.Editing -> AddCustomFieldSheet(
            initial = sheet.field,
            draft = sheet.draft,
            unitSuffix = unitSuffix,
            onDismiss = { onAction(MeasurementFormAction.OnCustomFieldSheetDismiss) },
            onLabelChange = { onAction(MeasurementFormAction.OnCustomFieldDraftLabelChange(it)) },
            onInitialValueChange = {
                onAction(MeasurementFormAction.OnCustomFieldDraftInitialValueChange(it))
            },
            onGendersChange = { onAction(MeasurementFormAction.OnCustomFieldDraftGendersChange(it)) },
            onSave = {
                onAction(
                    MeasurementFormAction.OnSaveCustomField(
                        id = sheet.field.id,
                        label = sheet.draft.label,
                        genders = sheet.draft.genders,
                        initialValue = sheet.draft.initialValue,
                    )
                )
            },
            bottomExtra = {
                OutlinedButton(
                    onClick = { onAction(MeasurementFormAction.OnArchiveCustomFieldRequest(sheet.field.id)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.custom_field_sheet_archive))
                }
            },
        )
        is CustomFieldSheet.ConfirmArchive -> ConfirmArchiveDialog(
            field = sheet.field,
            onDismiss = { onAction(MeasurementFormAction.OnCustomFieldSheetDismiss) },
            onConfirm = { id -> onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm(id)) },
        )
        null -> Unit
    }
}

@Composable
private fun GenderSelector(
    selected: CustomerGender?,
    label: String,
    onSelected: (CustomerGender) -> Unit
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DesignTokens.space2)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
            CustomerGender.entries.forEach { gender ->
                val isSelected = selected == gender
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(gender) },
                    label = {
                        Text(
                            text = genderLabel(gender),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (isSelected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionProgressRow(
    sections: List<MeasurementSection>,
    currentIndex: Int,
    fields: Map<String, String>,
    customLocked: Boolean,
    customHasData: Boolean,
    onJumpToSection: (Int) -> Unit,
) {
    val customPageIndex = sections.size
    val isCustomActive = currentIndex >= customPageIndex
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEachIndexed { index, section ->
                val color = when {
                    index == currentIndex -> MaterialTheme.colorScheme.primary
                    // Same parsable-positive predicate as MeasurementFormState.canSave
                    // so a dot only lights for values that will actually persist.
                    section.fields.any { f ->
                        (fields[f.key]?.toDoubleOrNull() ?: 0.0) > 0.0
                    } -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = color, shape = CircleShape)
                        .clickable { onJumpToSection(index) }
                )
            }
            CustomStepPill(
                isActive = isCustomActive,
                isLocked = customLocked,
                hasData = customHasData,
                onClick = { onJumpToSection(customPageIndex) },
            )
        }
        Text(
            text = if (isCustomActive) {
                stringResource(Res.string.measurement_custom_step)
            } else {
                stringResource(Res.string.measurement_section_of, currentIndex + 1, sections.size)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomStepPill(
    isActive: Boolean,
    isLocked: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
) {
    // Filled when the step is open or holds data; outlined otherwise.
    val filled = isActive || hasData
    val borderColor = when {
        isLocked -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = if (filled && !isLocked) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = when {
        isLocked -> MaterialTheme.colorScheme.onSurfaceVariant
        filled -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(color = containerColor, shape = RoundedCornerShape(DesignTokens.radiusFull))
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(DesignTokens.radiusFull),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Add,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(Res.string.measurement_custom_step),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
private fun UnitBadge(unit: MeasurementUnit) {
    val label = if (unit == MeasurementUnit.INCHES) {
        stringResource(Res.string.measurement_unit_inches)
    } else {
        stringResource(Res.string.measurement_unit_cm)
    }
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = MaterialTheme.colorScheme.secondaryContainer
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
private fun MeasurementFieldInput(
    field: MeasurementField,
    value: String,
    unitSuffix: String,
    onValueChange: (String) -> Unit
) {
    MeasurementTextField(
        value = value,
        onValueChange = { newVal ->
            val filtered = newVal.filter { it.isDigit() || it == '.' }
            val dotCount = filtered.count { it == '.' }
            if (dotCount <= 1) onValueChange(filtered)
        },
        label = field.label,
        placeholder = "0",
        suffix = unitSuffix,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun ShowMoreToggle(
    isExpanded: Boolean,
    extraCount: Int,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = DesignTokens.space1)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isExpanded) {
                    stringResource(Res.string.measurement_show_less)
                } else {
                    stringResource(Res.string.measurement_show_more_count, extraCount)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SectionNavigation(
    currentIndex: Int,
    totalSections: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            onClick = onPrevious,
            enabled = currentIndex > 0,
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(DesignTokens.space1))
            Text(
                text = stringResource(Res.string.measurement_previous),
                style = MaterialTheme.typography.labelMedium
            )
        }
        TextButton(
            onClick = onNext,
            enabled = currentIndex < totalSections - 1,
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Text(
                text = stringResource(Res.string.measurement_next),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.width(DesignTokens.space1))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun NotesSection(
    isExpanded: Boolean,
    notes: String,
    onToggle: () -> Unit,
    onNotesChange: (String) -> Unit
) {
    Column {
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(DesignTokens.space1))
            Text(
                text = if (isExpanded) {
                    stringResource(Res.string.measurement_notes_label)
                } else {
                    stringResource(Res.string.measurement_add_note)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (isExpanded) {
            MeasurementTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = stringResource(Res.string.measurement_notes_label),
                placeholder = stringResource(Res.string.measurement_notes_placeholder),
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }
    }
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
    suffix: String? = null,
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
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = DesignTokens.space1)
            )
        }
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
                    suffix = suffix?.let {
                        {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = RoundedCornerShape(DesignTokens.radiusMd),
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp,
                        )
                    }
                )
            }
        )
    }
}

@Composable
private fun genderLabel(gender: CustomerGender): String = when (gender) {
    CustomerGender.FEMALE -> stringResource(Res.string.gender_female)
    CustomerGender.MALE -> stringResource(Res.string.gender_male)
}

@Suppress("CyclomaticComplexMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomFieldsSection(
    fields: List<CustomMeasurementField>,
    fieldValues: Map<String, String>,
    unitSuffix: String,
    canUseCustomMeasurements: Boolean,
    isEditMode: Boolean,
    isInWelcomeWindow: Boolean,
    tier: SubscriptionTier,
    onFieldValueChange: (String, String) -> Unit,
    onAddClick: () -> Unit,
    onLockedAddClick: () -> Unit,
    onEditField: (String) -> Unit,
    onDeleteField: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                Text(
                    text = stringResource(Res.string.custom_field_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // "First Month" pill is only correct for trial-only access — i.e.,
                // FREE tier inside the welcome window. Pro/Atelier users inside
                // their first 30 days have permanent access; showing them
                // "FIRST MONTH" implies their access is temporary when it isn't.
                val isTrialAccess = canUseCustomMeasurements &&
                    isInWelcomeWindow &&
                    tier == SubscriptionTier.FREE
                val pillRes = when {
                    isTrialAccess -> Res.string.custom_field_section_pill_first_month
                    canUseCustomMeasurements -> Res.string.custom_field_section_pill_pro
                    else -> Res.string.custom_field_section_pill_locked
                }
                Text(
                    text = stringResource(pillRes).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canUseCustomMeasurements) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(DesignTokens.radiusFull),
                        )
                        .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
                )
            }
            Text(
                text = stringResource(Res.string.custom_field_section_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AddCustomFieldButton(
            enabled = canUseCustomMeasurements,
            onClick = if (canUseCustomMeasurements) onAddClick else onLockedAddClick,
        )

        // When not entitled, still show rows whose value is recorded (non-blank)
        // so a FREE-post-welcome tailor editing a past measurement keeps seeing
        // previously recorded custom-field values. Spec: "Past measurements
        // with recorded custom-field values: always visible, on every tier,
        // forever." Creation of NEW fields stays gated by the Add button.
        val visibleFields = if (canUseCustomMeasurements) {
            fields
        } else if (isEditMode) {
            fields.filter { (fieldValues[it.id] ?: "").isNotBlank() }
        } else {
            emptyList()
        }

        if (visibleFields.isEmpty()) {
            val captionRes = if (canUseCustomMeasurements) {
                Res.string.custom_field_empty_caption
            } else {
                Res.string.custom_field_locked_caption
            }
            Text(
                text = stringResource(captionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visibleFields.forEach { field ->
                // Long-press on the label row (NOT the text field) opens the
                // manage sheet. The text field's own gesture detector would
                // otherwise swallow the long-press and break text selection.
                Column(
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = field.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = { onEditField(field.id) },
                                    onLongClick = { onEditField(field.id) },
                                ),
                        )
                        IconButton(
                            onClick = { onDeleteField(field.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(
                                    Res.string.custom_field_delete_content_description,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    MeasurementTextField(
                        value = fieldValues[field.id] ?: "",
                        // Mirror MeasurementFieldInput's numeric-only filter so custom
                        // fields can't accept paste / hardware-keyboard input that
                        // silently parses to 0.0 and disappears on save.
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() || it == '.' }
                            val dotCount = filtered.count { it == '.' }
                            if (dotCount <= 1) onFieldValueChange(field.id, filtered)
                        },
                        label = "", // label rendered above by the long-pressable Text
                        placeholder = "0",
                        suffix = unitSuffix,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCustomFieldButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
    ) {
        if (!enabled) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(DesignTokens.space1))
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(DesignTokens.space1))
        }
        Text(
            text = stringResource(Res.string.custom_field_add_button),
            fontWeight = FontWeight.SemiBold,
        )
        if (!enabled) {
            Spacer(Modifier.width(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.custom_field_section_pill_locked).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusFull),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenEmptyPreview() {
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenFemalePreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    val allKeys = sections.flatMap { it.fields }.map { it.key }
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = 1,
                fields = allKeys.associateWith { "" } + mapOf(
                    "bust_circumference" to "36",
                    "waist" to "28",
                    "hip_circumference" to "38"
                ),
                unit = MeasurementUnit.INCHES
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenMalePreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.MALE)
    val allKeys = sections.flatMap { it.fields }.map { it.key }
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                isEditMode = true,
                gender = CustomerGender.MALE,
                sections = sections,
                currentSectionIndex = 0,
                fields = allKeys.associateWith { "" } + mapOf(
                    "chest" to "40",
                    "trouser_waist" to "32",
                    "shirt_length" to "28"
                ),
                unit = MeasurementUnit.INCHES
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenCreateFlowPreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    val allKeys = sections.flatMap { it.fields }.map { it.key }
    StitchPadTheme {
        MeasurementFormScreen(
            state = MeasurementFormState(
                fromCustomerCreation = true,
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = 0,
                fields = allKeys.associateWith { "" } + mapOf("bust_circumference" to "36"),
                unit = MeasurementUnit.INCHES,
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun MeasurementFormScreenCreateFlowDarkPreview() {
    val sections = BodyProfileTemplate.sectionsFor(CustomerGender.FEMALE)
    val allKeys = sections.flatMap { it.fields }.map { it.key }
    StitchPadTheme(darkTheme = true) {
        MeasurementFormScreen(
            state = MeasurementFormState(
                fromCustomerCreation = true,
                gender = CustomerGender.FEMALE,
                sections = sections,
                currentSectionIndex = 0,
                fields = allKeys.associateWith { "" } + mapOf("bust_circumference" to "36"),
                unit = MeasurementUnit.INCHES,
            ),
            onAction = {}
        )
    }
}

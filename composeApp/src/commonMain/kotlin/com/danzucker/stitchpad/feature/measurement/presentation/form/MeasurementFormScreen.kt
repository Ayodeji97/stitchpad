package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.model.MeasurementSection
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gender_female
import stitchpad.composeapp.generated.resources.gender_male
import stitchpad.composeapp.generated.resources.measurement_add_note
import stitchpad.composeapp.generated.resources.measurement_add_title
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
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches
import stitchpad.composeapp.generated.resources.section_body_lengths
import stitchpad.composeapp.generated.resources.section_trouser
import stitchpad.composeapp.generated.resources.section_upper_body

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
    val canSave = state.gender != null && !state.isLoading

    val pagerState = rememberPagerState(pageCount = { state.sections.size })

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
                    SectionTabRow(
                        sections = state.sections,
                        currentIndex = state.currentSectionIndex,
                        onTabSelected = { onAction(MeasurementFormAction.OnSectionChange(it)) }
                    )
                    Spacer(Modifier.height(DesignTokens.space3))
                    SectionProgressRow(
                        sections = state.sections,
                        currentIndex = state.currentSectionIndex,
                        fields = state.fields
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
                    val section = state.sections[pageIndex]
                    val essentialFields = section.fields.filter { it.isEssential }
                    val extraFields = section.fields.filter { !it.isEssential }
                    val isExpanded = pageIndex != state.currentSectionIndex || state.isCurrentSectionExpanded

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
                        essentialFields.forEach { field ->
                            MeasurementFieldInput(
                                field = field,
                                value = state.fields[field.key] ?: "",
                                unitSuffix = unitSuffix,
                                onValueChange = { onAction(MeasurementFormAction.OnFieldChange(field.key, it)) }
                            )
                        }

                        if (isExpanded) {
                            extraFields.forEach { field ->
                                MeasurementFieldInput(
                                    field = field,
                                    value = state.fields[field.key] ?: "",
                                    unitSuffix = unitSuffix,
                                    onValueChange = { onAction(MeasurementFormAction.OnFieldChange(field.key, it)) }
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
                        totalSections = state.sections.size,
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
                                text = stringResource(Res.string.measurement_save_button),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(DesignTokens.space4))
            }
        }
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
private fun SectionTabRow(
    sections: List<MeasurementSection>,
    currentIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        itemsIndexed(sections) { index, section ->
            val isSelected = index == currentIndex
            FilterChip(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                label = {
                    Text(
                        text = sectionTitle(section.titleKey),
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

@Composable
private fun SectionProgressRow(
    sections: List<MeasurementSection>,
    currentIndex: Int,
    fields: Map<String, String>
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.forEachIndexed { index, section ->
                val color = when {
                    index == currentIndex -> MaterialTheme.colorScheme.primary
                    section.fields.any { f -> fields[f.key]?.isNotBlank() == true } -> DesignTokens.primary400
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = color, shape = CircleShape)
                )
            }
        }
        Text(
            text = stringResource(Res.string.measurement_section_of, currentIndex + 1, sections.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Spacer(Modifier.width(4.dp))
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
            Spacer(Modifier.width(4.dp))
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

@Composable
private fun sectionTitle(titleKey: String): String = when (titleKey) {
    "section_upper_body" -> stringResource(Res.string.section_upper_body)
    "section_body_lengths" -> stringResource(Res.string.section_body_lengths)
    "section_trouser" -> stringResource(Res.string.section_trouser)
    else -> titleKey
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

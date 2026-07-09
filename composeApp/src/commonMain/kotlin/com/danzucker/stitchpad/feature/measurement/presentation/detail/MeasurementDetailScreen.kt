// V1 "sectioned note" read-only layout: app bar + overflow, meta chips, one card
// per section, notes card, sticky Edit bar, plus the rename/delete dialogs and
// their previews. Splitting this cohesive screen would scatter tightly-coupled UI.
@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.measurement.presentation.formatMeasurementValue
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_measurement_detail_back
import stitchpad.composeapp.generated.resources.cd_measurement_detail_overflow
import stitchpad.composeapp.generated.resources.cd_measurement_share
import stitchpad.composeapp.generated.resources.custom_field_section_title
import stitchpad.composeapp.generated.resources.customer_delete_cancel
import stitchpad.composeapp.generated.resources.customer_delete_confirm
import stitchpad.composeapp.generated.resources.measurement_delete_message
import stitchpad.composeapp.generated.resources.measurement_delete_title
import stitchpad.composeapp.generated.resources.measurement_detail_edit_button
import stitchpad.composeapp.generated.resources.measurement_detail_notes_title
import stitchpad.composeapp.generated.resources.measurement_detail_saved_snackbar
import stitchpad.composeapp.generated.resources.measurement_detail_taken
import stitchpad.composeapp.generated.resources.measurement_detail_title
import stitchpad.composeapp.generated.resources.measurement_gender_men
import stitchpad.composeapp.generated.resources.measurement_gender_women
import stitchpad.composeapp.generated.resources.measurement_menu_delete
import stitchpad.composeapp.generated.resources.measurement_menu_rename
import stitchpad.composeapp.generated.resources.measurement_name_label
import stitchpad.composeapp.generated.resources.measurement_name_placeholder
import stitchpad.composeapp.generated.resources.measurement_rename_cancel
import stitchpad.composeapp.generated.resources.measurement_rename_dialog_title
import stitchpad.composeapp.generated.resources.measurement_rename_save
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches
import stitchpad.composeapp.generated.resources.section_arms
import stitchpad.composeapp.generated.resources.section_body_lengths
import stitchpad.composeapp.generated.resources.section_bust
import stitchpad.composeapp.generated.resources.section_neck_shoulders
import stitchpad.composeapp.generated.resources.section_trouser
import stitchpad.composeapp.generated.resources.section_upper_body
import stitchpad.composeapp.generated.resources.section_waist_hip
import stitchpad.composeapp.generated.resources.whatsapp_launch_failed

@Composable
fun MeasurementDetailRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String, String) -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: MeasurementDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
    val whatsAppFailed = stringResource(Res.string.whatsapp_launch_failed)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementDetailEvent.NavigateBack -> onNavigateBack()
            is MeasurementDetailEvent.NavigateToEdit ->
                onNavigateToEdit(event.customerId, event.measurementId)
            MeasurementDetailEvent.NavigateToUpgrade -> onNavigateToUpgrade()
            is MeasurementDetailEvent.LaunchWhatsApp -> scope.launch {
                if (!whatsAppLauncher.launch(event.phone, event.message)) {
                    snackbarHostState.showSnackbar(whatsAppFailed)
                }
            }
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(MeasurementDetailAction.OnErrorDismiss)
        }
    }

    val savedMessage = stringResource(Res.string.measurement_detail_saved_snackbar)
    LaunchedEffect(state.showSavedMessage) {
        if (state.showSavedMessage) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.onAction(MeasurementDetailAction.OnSavedMessageShown)
        }
    }

    MeasurementDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    state: MeasurementDetailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (MeasurementDetailAction) -> Unit,
) {
    val measurement = state.measurement
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = measurement?.name?.ifBlank { null }
                            ?: stringResource(Res.string.measurement_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(MeasurementDetailAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_measurement_detail_back),
                        )
                    }
                },
                actions = { DetailOverflowMenu(onAction) },
            )
        },
        bottomBar = {
            if (measurement != null) {
                Surface {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = DesignTokens.space4,
                                end = DesignTokens.space4,
                                top = DesignTokens.space3,
                                bottom = DesignTokens.space4,
                            ),
                    ) {
                        StitchPadButton(
                            text = stringResource(Res.string.measurement_detail_edit_button),
                            onClick = { onAction(MeasurementDetailAction.OnEditClick) },
                            leadingIcon = Icons.Default.Edit,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedIconButton(
                            onClick = { onAction(MeasurementDetailAction.OnShareClick) },
                            shape = RoundedCornerShape(DesignTokens.radiusLg),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(Res.string.cd_measurement_share),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            measurement != null -> MeasurementDetailContent(
                measurement = measurement,
                customFieldLabels = state.customFieldLabels,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onAction(MeasurementDetailAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.measurement_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.measurement_delete_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(MeasurementDetailAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ) {
                    Text(
                        text = stringResource(Res.string.customer_delete_confirm),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MeasurementDetailAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.customer_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    val renameDraft = state.renameDraft
    if (renameDraft != null) {
        AlertDialog(
            onDismissRequest = { onAction(MeasurementDetailAction.OnDismissRenameDialog) },
            title = { Text(stringResource(Res.string.measurement_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { onAction(MeasurementDetailAction.OnRenameDraftChange(it)) },
                    label = { Text(stringResource(Res.string.measurement_name_label)) },
                    placeholder = { Text(stringResource(Res.string.measurement_name_placeholder)) },
                    singleLine = true,
                    isError = renameDraft.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(MeasurementDetailAction.OnConfirmRename) },
                    enabled = renameDraft.isNotBlank(),
                ) { Text(stringResource(Res.string.measurement_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MeasurementDetailAction.OnDismissRenameDialog) }) {
                    Text(stringResource(Res.string.measurement_rename_cancel))
                }
            },
        )
    }

    if (state.showShareSheet && measurement != null) {
        ShareMeasurementSheet(
            measurementName = measurement.name.ifBlank { stringResource(Res.string.measurement_detail_title) },
            customerName = state.customer?.name.orEmpty(),
            onShareAsImage = { onAction(MeasurementDetailAction.OnShareAsImageClick) },
            onShareAsPdf = { onAction(MeasurementDetailAction.OnShareAsPdfClick) },
            onShareWhatsApp = { onAction(MeasurementDetailAction.OnShareWhatsAppClick) },
            onDismiss = { onAction(MeasurementDetailAction.OnDismissShareSheet) },
        )
    }
}

@Composable
private fun MeasurementDetailContent(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val sections = remember(measurement, customFieldLabels) {
        measurementDetailSections(measurement, customFieldLabels)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            bottom = DesignTokens.space4,
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item { MetaChipsRow(measurement) }
        items(sections) { section -> SectionCard(section, measurement.unit) }
        val notes = measurement.notes
        if (!notes.isNullOrBlank()) {
            item { NotesCard(notes) }
        }
    }
}

@Composable
private fun MetaChipsRow(measurement: Measurement) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.padding(top = DesignTokens.space3),
    ) {
        MetaChip(
            text = stringResource(
                if (measurement.gender == CustomerGender.FEMALE) {
                    Res.string.measurement_gender_women
                } else {
                    Res.string.measurement_gender_men
                },
            ),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        MetaChip(
            text = stringResource(
                if (measurement.unit == MeasurementUnit.INCHES) {
                    Res.string.measurement_unit_inches
                } else {
                    Res.string.measurement_unit_cm
                },
            ),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Legacy docs can carry dateTaken == 0L (pre-mapper-backfill) — hide the
        // chip rather than showing a 1970 date, matching the list's blank-date rule.
        if (measurement.dateTaken > 0L) {
            MetaChip(
                text = stringResource(
                    Res.string.measurement_detail_taken,
                    formatTakenDate(measurement.dateTaken),
                ),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space1),
        )
    }
}

private fun formatTakenDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}

@Composable
private fun SectionCard(section: MeasurementDetailSection, unit: MeasurementUnit) {
    val title = sectionTitle(section.titleKey)
    val unitSuffix = if (unit == MeasurementUnit.INCHES) "″" else "cm"
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            section.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.space2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatMeasurementValue(row.value)}$unitSuffix",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFamily(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(titleKey: String?): String = when (titleKey) {
    "section_upper_body" -> stringResource(Res.string.section_upper_body)
    "section_body_lengths" -> stringResource(Res.string.section_body_lengths)
    "section_trouser" -> stringResource(Res.string.section_trouser)
    "section_neck_shoulders" -> stringResource(Res.string.section_neck_shoulders)
    "section_bust" -> stringResource(Res.string.section_bust)
    "section_waist_hip" -> stringResource(Res.string.section_waist_hip)
    "section_arms" -> stringResource(Res.string.section_arms)
    null -> stringResource(Res.string.custom_field_section_title)
    else -> titleKey // future template keys degrade to the raw key rather than crash
}

@Composable
private fun NotesCard(notes: String) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)) {
            Text(
                text = stringResource(Res.string.measurement_detail_notes_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = DesignTokens.space1),
            )
        }
    }
}

@Composable
private fun DetailOverflowMenu(onAction: (MeasurementDetailAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.cd_measurement_detail_overflow),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.measurement_menu_rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onAction(MeasurementDetailAction.OnRenameClick)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Res.string.measurement_menu_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onAction(MeasurementDetailAction.OnDeleteClick)
                },
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailScreenPreview() {
    StitchPadTheme {
        MeasurementDetailScreen(
            state = MeasurementDetailState(
                measurement = Measurement(
                    id = "m1",
                    customerId = "c1",
                    gender = CustomerGender.FEMALE,
                    name = "Wedding guest gown",
                    fields = mapOf(
                        "shoulder_width" to 15.0,
                        "bust_circumference" to 38.0,
                        "waist" to 31.0,
                        "trouser_waist" to 31.0,
                    ),
                    unit = MeasurementUnit.INCHES,
                    notes = "Prefers the gown loose at the hip.",
                    dateTaken = 1_750_000_000_000L,
                    createdAt = 1_750_000_000_000L,
                ),
                isLoading = false,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MeasurementDetailScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        MeasurementDetailScreen(
            state = MeasurementDetailState(
                measurement = Measurement(
                    id = "m1",
                    customerId = "c1",
                    gender = CustomerGender.FEMALE,
                    name = "Wedding guest gown",
                    fields = mapOf(
                        "shoulder_width" to 15.0,
                        "bust_circumference" to 38.0,
                        "waist" to 31.0,
                        "trouser_waist" to 31.0,
                    ),
                    unit = MeasurementUnit.INCHES,
                    notes = "Prefers the gown loose at the hip.",
                    dateTaken = 1_750_000_000_000L,
                    createdAt = 1_750_000_000_000L,
                ),
                isLoading = false,
            ),
            onAction = {},
        )
    }
}

@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.freemium.presentation.cap.CustomerCapReachedSheet
import com.danzucker.stitchpad.feature.measurement.presentation.measurementFieldLabel
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.PageBox
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.presentation.NotebookTouchTarget
import com.danzucker.stitchpad.feature.notebookimport.presentation.cardValueLabel
import com.danzucker.stitchpad.feature.notebookimport.presentation.warningColors
import com.danzucker.stitchpad.ui.components.CustomDatePickerDialog
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadButtonVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.gender_female
import stitchpad.composeapp.generated.resources.gender_male
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches
import stitchpad.composeapp.generated.resources.notebook_card_add_to
import stitchpad.composeapp.generated.resources.notebook_card_already_customer
import stitchpad.composeapp.generated.resources.notebook_card_back_cd
import stitchpad.composeapp.generated.resources.notebook_card_change_field_cd
import stitchpad.composeapp.generated.resources.notebook_card_create_new
import stitchpad.composeapp.generated.resources.notebook_card_date
import stitchpad.composeapp.generated.resources.notebook_card_date_change
import stitchpad.composeapp.generated.resources.notebook_card_date_clear
import stitchpad.composeapp.generated.resources.notebook_card_date_none
import stitchpad.composeapp.generated.resources.notebook_card_field_note
import stitchpad.composeapp.generated.resources.notebook_card_from_page
import stitchpad.composeapp.generated.resources.notebook_card_gender_question
import stitchpad.composeapp.generated.resources.notebook_card_looks_right
import stitchpad.composeapp.generated.resources.notebook_card_match_hint
import stitchpad.composeapp.generated.resources.notebook_card_measurements
import stitchpad.composeapp.generated.resources.notebook_card_name
import stitchpad.composeapp.generated.resources.notebook_card_not_found
import stitchpad.composeapp.generated.resources.notebook_card_notes
import stitchpad.composeapp.generated.resources.notebook_card_phone
import stitchpad.composeapp.generated.resources.notebook_card_photo_cd
import stitchpad.composeapp.generated.resources.notebook_card_photo_gone
import stitchpad.composeapp.generated.resources.notebook_card_pick_field
import stitchpad.composeapp.generated.resources.notebook_card_remove_value_cd
import stitchpad.composeapp.generated.resources.notebook_card_save_now
import stitchpad.composeapp.generated.resources.notebook_card_saved
import stitchpad.composeapp.generated.resources.notebook_card_skip
import stitchpad.composeapp.generated.resources.notebook_card_title
import stitchpad.composeapp.generated.resources.notebook_card_unclear
import stitchpad.composeapp.generated.resources.notebook_card_unit
import stitchpad.composeapp.generated.resources.notebook_card_value_right_cd
import stitchpad.composeapp.generated.resources.notebook_review_unit_cm
import stitchpad.composeapp.generated.resources.notebook_review_unit_inches

/** Longest edge the page photo is decoded at for the crop: enough to read handwriting, small in memory. */
private const val PHOTO_DECODE_PX = 1600
private const val MIN_CROP_ASPECT = 0.6f
private const val MAX_CROP_ASPECT = 4f
private val FULL_PAGE_HEIGHT = 320.dp
private val PHOTO_PLACEHOLDER_HEIGHT = 140.dp

/**
 * Root of the card editor. [onNavigateToUpgrade] and [onNavigateToCustomers] are the cap sheet's
 * upgrade and swap paths, wired by the nav graph.
 */
@Composable
fun NotebookCardRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToCustomers: () -> Unit,
) {
    val viewModel: NotebookCardViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Same transient pattern as CustomerFormScreen: the sheet only lives while it is on screen.
    var capSheet by remember { mutableStateOf<NotebookCardEvent.ShowCapReachedSheet?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotebookCardEvent.NavigateBack -> onNavigateBack()
            is NotebookCardEvent.ShowCapReachedSheet -> capSheet = event
        }
    }

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            viewModel.onAction(NotebookCardAction.OnMessageShown)
            snackbarHostState.showSnackbar(message)
        }
    }

    NotebookCardScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)

    capSheet?.let { pending ->
        CustomerCapReachedSheet(
            activeCount = pending.activeCount,
            customerCap = pending.customerCap,
            proMonthlyPrice = state.upgradeCtaPrice,
            onUpgradeClick = {
                capSheet = null
                onNavigateToUpgrade()
            },
            onSwapClick = {
                capSheet = null
                onNavigateToCustomers()
            },
            onDismiss = { capSheet = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookCardScreen(
    state: NotebookCardState,
    onAction: (NotebookCardAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.notebook_card_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(NotebookCardAction.OnNavigateBack) },
                        modifier = Modifier.size(NotebookTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.notebook_card_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { if (state.draft != null) CardBottomBar(state, onAction) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.isLoading -> Box(modifier, contentAlignment = Alignment.Center) { LoadingDots() }
            state.draft == null -> Box(modifier.padding(DesignTokens.space5), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.notebook_card_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            else -> CardForm(state = state, onAction = onAction, modifier = modifier)
        }
    }

    state.fieldPickerIndex?.let { index ->
        FieldPickerDialog(
            options = state.fieldOptions,
            onSelect = { key -> onAction(NotebookCardAction.OnFieldSelect(index, key)) },
            onDismiss = { onAction(NotebookCardAction.OnFieldPickerDismiss) },
        )
    }
    if (state.showDatePicker) {
        CustomDatePickerDialog(
            initial = state.dateWritten,
            timeZone = TimeZone.currentSystemDefault(),
            onConfirm = { onAction(NotebookCardAction.OnDatePicked(it)) },
            onDismiss = { onAction(NotebookCardAction.OnDateDismiss) },
        )
    }
}

@Composable
private fun CardForm(state: NotebookCardState, onAction: (NotebookCardAction) -> Unit, modifier: Modifier) {
    val readOnly = state.isReadOnly
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.space5, vertical = DesignTokens.space2),
    ) {
        PagePhoto(state)
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(NotebookCardAction.OnNameChange(it)) },
            label = { Text(stringResource(Res.string.notebook_card_name)) },
            singleLine = true,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.phone,
            onValueChange = { onAction(NotebookCardAction.OnPhoneChange(it)) },
            label = { Text(stringResource(Res.string.notebook_card_phone)) },
            singleLine = true,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        LabeledChoice(
            title = stringResource(Res.string.notebook_card_gender_question),
            first = stringResource(Res.string.gender_female),
            second = stringResource(Res.string.gender_male),
            selected = when (state.gender) {
                CustomerGender.FEMALE -> 0
                CustomerGender.MALE -> 1
                null -> null
            },
            enabled = !readOnly,
            onSelect = { i ->
                onAction(NotebookCardAction.OnGenderSelect(if (i == 0) CustomerGender.FEMALE else CustomerGender.MALE))
            },
        )
        LabeledChoice(
            title = stringResource(Res.string.notebook_card_unit),
            first = stringResource(Res.string.measurement_unit_inches),
            second = stringResource(Res.string.measurement_unit_cm),
            selected = if (state.unit == MeasurementUnit.INCHES) 0 else 1,
            enabled = !readOnly,
            onSelect = { i ->
                onAction(NotebookCardAction.OnUnitSelect(if (i == 0) MeasurementUnit.INCHES else MeasurementUnit.CM))
            },
        )
        DateRow(state.dateWritten, enabled = !readOnly, onAction = onAction)
        state.matchCandidate?.let { candidate ->
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
                LabeledChoice(
                    title = stringResource(Res.string.notebook_card_already_customer),
                    first = stringResource(Res.string.notebook_card_add_to, candidate.name),
                    second = stringResource(Res.string.notebook_card_create_new),
                    selected = if (state.addToExisting) 0 else 1,
                    enabled = !readOnly,
                    onSelect = { i -> onAction(NotebookCardAction.OnMatchToggle(addToExisting = i == 0)) },
                )
                if (state.addToExisting) {
                    Text(
                        text = stringResource(Res.string.notebook_card_match_hint, candidate.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ValuesSection(state, readOnly, onAction)
    }
}

@Composable
private fun PagePhoto(state: NotebookCardState) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape),
        ) {
            val url = state.imageUrl
            when {
                state.photoUnavailable -> PhotoPlaceholder(showGone = true)
                url == null -> PhotoPlaceholder(showGone = false)
                state.crop == null -> SubcomposeAsyncImage(
                    model = url,
                    contentDescription = stringResource(Res.string.notebook_card_photo_cd),
                    contentScale = ContentScale.Fit,
                    loading = { Box(contentAlignment = Alignment.Center) { LoadingDots() } },
                    modifier = Modifier.fillMaxWidth().height(FULL_PAGE_HEIGHT),
                )
                else -> CroppedPhoto(url, state.crop)
            }
        }
        if (state.pageNumber > 0) {
            Text(
                text = stringResource(Res.string.notebook_card_from_page, state.pageNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhotoPlaceholder(showGone: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().height(PHOTO_PLACEHOLDER_HEIGHT),
    ) {
        if (showGone) {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.notebook_card_photo_gone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LoadingDots()
        }
    }
}

/**
 * Draws [crop] of the page photo. [crop] is already clamped to the page (see [cropRegion]), and it is
 * normalised, so it works whatever size the photo is decoded at.
 */
@Composable
private fun CroppedPhoto(url: String, crop: CropRegion) {
    val context = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).size(PHOTO_DECODE_PX).build() }
    val painter = rememberAsyncImagePainter(model = request)
    val painterState by painter.state.collectAsState()
    val imageSize = painter.intrinsicSize
    val ready = painterState is AsyncImagePainter.State.Success &&
        imageSize.isSpecifiedPositive()
    if (!ready) {
        PhotoPlaceholder(showGone = painterState is AsyncImagePainter.State.Error)
        return
    }
    val sourceWidth = imageSize.width * crop.width
    val sourceHeight = imageSize.height * crop.height
    val aspect = (sourceWidth / sourceHeight).coerceIn(MIN_CROP_ASPECT, MAX_CROP_ASPECT)
    val description = stringResource(Res.string.notebook_card_photo_cd)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clipToBounds()
            .semantics { contentDescription = description },
    ) {
        val scale = size.width / sourceWidth
        translate(left = -crop.left * imageSize.width * scale, top = -crop.top * imageSize.height * scale) {
            with(painter) { draw(Size(imageSize.width * scale, imageSize.height * scale)) }
        }
    }
}

private fun Size.isSpecifiedPositive(): Boolean =
    width.isFinite() && height.isFinite() && width > 0f && height > 0f

@Composable
private fun LabeledChoice(
    title: String,
    first: String,
    second: String,
    selected: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
            listOf(first, second).forEachIndexed { index, label ->
                StitchPadButton(
                    text = label,
                    onClick = { onSelect(index) },
                    enabled = enabled,
                    variant = if (selected == index) {
                        StitchPadButtonVariant.Primary
                    } else {
                        StitchPadButtonVariant.Secondary
                    },
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier.weight(1f).heightIn(min = NotebookTouchTarget),
                )
            }
        }
    }
}

@Composable
private fun DateRow(date: LocalDate?, enabled: Boolean, onAction: (NotebookCardAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.notebook_card_date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = date?.let { formatDayFirst(it) } ?: stringResource(Res.string.notebook_card_date_none),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = JetBrainsMonoFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            if (date != null) {
                TextButton(
                    onClick = { onAction(NotebookCardAction.OnDateClear) },
                    modifier = Modifier.heightIn(min = NotebookTouchTarget),
                ) { Text(stringResource(Res.string.notebook_card_date_clear)) }
            }
            TextButton(
                onClick = { onAction(NotebookCardAction.OnDateClick) },
                modifier = Modifier.heightIn(min = NotebookTouchTarget),
            ) { Text(stringResource(Res.string.notebook_card_date_change), fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun formatDayFirst(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.ordinal.plus(1).toString().padStart(2, '0')}/${date.year}"

@Composable
private fun ValuesSection(state: NotebookCardState, readOnly: Boolean, onAction: (NotebookCardAction) -> Unit) {
    val indexed = state.values.withIndex().toList()
    val (fields, notes) = indexed.partition { it.value.fieldKey != null }
    val unitLabel = if (state.unit == MeasurementUnit.INCHES) {
        stringResource(Res.string.notebook_review_unit_inches)
    } else {
        stringResource(Res.string.notebook_review_unit_cm)
    }
    if (fields.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.notebook_card_measurements, unitLabel),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        fields.forEach { (index, value) -> ValueEditor(index, value, state.gender, readOnly, onAction) }
    }
    if (notes.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.notebook_card_notes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        notes.forEach { (index, value) -> ValueEditor(index, value, state.gender, readOnly, onAction) }
    }
}

@Composable
private fun ValueEditor(
    index: Int,
    value: CardValue,
    gender: CustomerGender?,
    readOnly: Boolean,
    onAction: (NotebookCardAction) -> Unit,
) {
    val warning = warningColors()
    val label = cardValueLabel(value, gender)
    val unclear = value.flagged
    val changeFieldCd = stringResource(Res.string.notebook_card_change_field_cd, label)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = NotebookTouchTarget)
                .clickable(enabled = !readOnly, role = Role.Button, onClickLabel = changeFieldCd) {
                    onAction(NotebookCardAction.OnFieldPickerOpen(index))
                },
        ) {
            Text(
                text = if (unclear) stringResource(Res.string.notebook_card_unclear, label) else label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (unclear) warning.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!readOnly) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value.value,
                onValueChange = { onAction(NotebookCardAction.OnValueChange(index, it)) },
                singleLine = true,
                readOnly = readOnly,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily()),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = if (unclear) {
                    OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = warning.background,
                        focusedContainerColor = warning.background,
                        unfocusedBorderColor = warning.accent,
                        focusedBorderColor = warning.accent,
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
                modifier = Modifier.weight(1f),
            )
            if (!readOnly) {
                if (unclear) {
                    IconButton(
                        onClick = { onAction(NotebookCardAction.OnValueConfirm(index)) },
                        modifier = Modifier.size(NotebookTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.notebook_card_value_right_cd, label),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(
                    onClick = { onAction(NotebookCardAction.OnValueRemove(index)) },
                    modifier = Modifier.size(NotebookTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.notebook_card_remove_value_cd, label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldPickerDialog(
    options: List<MeasurementField>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.notebook_card_pick_field),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerOption(stringResource(Res.string.notebook_card_field_note)) { onSelect(null) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                options.forEach { field ->
                    PickerOption(measurementFieldLabel(field)) { onSelect(field.key) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun PickerOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NotebookTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DesignTokens.space3),
    )
}

@Composable
private fun CardBottomBar(state: NotebookCardState, onAction: (NotebookCardAction) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (state.isReadOnly) {
                Text(
                    text = stringResource(Res.string.notebook_card_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(DesignTokens.space5),
                )
                return@Column
            }
            state.checkBlocker?.let { blocker ->
                Text(
                    text = stringResource(blocker.hintResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColors().accent,
                    modifier = Modifier.padding(
                        start = DesignTokens.space5,
                        end = DesignTokens.space5,
                        top = DesignTokens.space2
                    ),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.padding(horizontal = DesignTokens.space5, vertical = DesignTokens.space3),
            ) {
                TextButton(
                    onClick = { onAction(NotebookCardAction.OnSkipClick) },
                    enabled = !state.isSaving,
                    modifier = Modifier.heightIn(min = NotebookTouchTarget),
                ) { Text(stringResource(Res.string.notebook_card_skip), fontWeight = FontWeight.SemiBold) }
                StitchPadButton(
                    text = stringResource(Res.string.notebook_card_save_now),
                    onClick = { onAction(NotebookCardAction.OnSaveNowClick) },
                    enabled = state.canMarkChecked,
                    isLoading = state.isSaving,
                    variant = StitchPadButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
                StitchPadButton(
                    text = stringResource(Res.string.notebook_card_looks_right),
                    onClick = { onAction(NotebookCardAction.OnLooksRightClick) },
                    enabled = state.canMarkChecked,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val previewCard = ReviewCard(
    id = "c1",
    jobId = "job",
    pageId = "p4",
    name = "Fatima Bello",
    phone = "0812 554 0091",
    gender = CustomerGender.FEMALE,
    unit = MeasurementUnit.INCHES,
    dateWritten = LocalDate(2026, 3, 14),
    values = listOf(
        CardValue("Bust", "38", "bust_circumference", flagged = false),
        CardValue("Hip", "44", "hip_circumference", flagged = false),
        CardValue("Waist", "3?", "waist", flagged = true),
        CardValue("Gown", "57½", "full_length_gown", flagged = false),
        CardValue("Rd arm", "13", null, flagged = false),
    ),
    box = PageBox(0.1f, 0.2f, 0.8f, 0.2f),
    needsReview = true,
    status = CardStatus.NEW,
    matchedCustomerId = null,
    savedCustomerId = null,
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCardScreenUnclearPreview() {
    StitchPadTheme {
        NotebookCardScreen(
            state = NotebookCardState(
                isLoading = false,
                draft = previewCard,
                pageNumber = 4,
                photoUnavailable = true,
                matchCandidate = ExistingCustomer("cust-1", "Fatima Bello"),
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCardScreenNoGenderPreview() {
    StitchPadTheme(darkTheme = true) {
        NotebookCardScreen(
            state = NotebookCardState(
                isLoading = false,
                draft = previewCard.copy(gender = null, values = previewCard.values.map { it.copy(flagged = false) }),
                pageNumber = 2,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCardScreenSavedPreview() {
    StitchPadTheme {
        NotebookCardScreen(
            state = NotebookCardState(
                isLoading = false,
                draft = previewCard.copy(status = CardStatus.SAVED, values = previewCard.values.take(2)),
                pageNumber = 4,
                photoUnavailable = true,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCardScreenNotFoundPreview() {
    StitchPadTheme {
        NotebookCardScreen(state = NotebookCardState(isLoading = false, notFound = true), onAction = {})
    }
}

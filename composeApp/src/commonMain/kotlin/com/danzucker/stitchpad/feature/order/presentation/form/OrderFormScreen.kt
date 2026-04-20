package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.GarmentGender
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.media.rememberImageCaptureLauncher
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.common_ok
import stitchpad.composeapp.generated.resources.garment_gender_female
import stitchpad.composeapp.generated.resources.garment_gender_male
import stitchpad.composeapp.generated.resources.garment_gender_unisex
import stitchpad.composeapp.generated.resources.order_form_add_item
import stitchpad.composeapp.generated.resources.order_form_create_button
import stitchpad.composeapp.generated.resources.order_form_deadline_label
import stitchpad.composeapp.generated.resources.order_form_deposit_label
import stitchpad.composeapp.generated.resources.order_form_deposit_placeholder
import stitchpad.composeapp.generated.resources.order_form_description_label
import stitchpad.composeapp.generated.resources.order_form_description_placeholder
import stitchpad.composeapp.generated.resources.order_form_fabric_photo_label
import stitchpad.composeapp.generated.resources.order_form_garment_type_label
import stitchpad.composeapp.generated.resources.order_form_item_number
import stitchpad.composeapp.generated.resources.order_form_measurement_label
import stitchpad.composeapp.generated.resources.order_form_next
import stitchpad.composeapp.generated.resources.order_form_no_measurement
import stitchpad.composeapp.generated.resources.order_form_no_style
import stitchpad.composeapp.generated.resources.order_form_notes_label
import stitchpad.composeapp.generated.resources.order_form_notes_placeholder
import stitchpad.composeapp.generated.resources.order_form_photo_cancel
import stitchpad.composeapp.generated.resources.order_form_photo_pick
import stitchpad.composeapp.generated.resources.order_form_photo_sheet_title
import stitchpad.composeapp.generated.resources.order_form_photo_take
import stitchpad.composeapp.generated.resources.order_form_pick_date
import stitchpad.composeapp.generated.resources.order_form_previous
import stitchpad.composeapp.generated.resources.order_form_price_label
import stitchpad.composeapp.generated.resources.order_form_priority_label
import stitchpad.composeapp.generated.resources.order_form_remove_item
import stitchpad.composeapp.generated.resources.order_form_save_button
import stitchpad.composeapp.generated.resources.order_form_search_customers
import stitchpad.composeapp.generated.resources.order_form_select_customer
import stitchpad.composeapp.generated.resources.order_form_snap_fabric
import stitchpad.composeapp.generated.resources.order_form_step_customer
import stitchpad.composeapp.generated.resources.order_form_step_details
import stitchpad.composeapp.generated.resources.order_form_step_indicator
import stitchpad.composeapp.generated.resources.order_form_step_items
import stitchpad.composeapp.generated.resources.order_form_style_label
import stitchpad.composeapp.generated.resources.order_form_title_add
import stitchpad.composeapp.generated.resources.order_form_title_edit
import stitchpad.composeapp.generated.resources.order_priority_normal
import stitchpad.composeapp.generated.resources.order_priority_rush
import stitchpad.composeapp.generated.resources.order_priority_urgent

@Composable
fun OrderFormRoot(
    onNavigateBack: () -> Unit
) {
    val viewModel: OrderFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OrderFormEvent.NavigateBack -> onNavigateBack()
            OrderFormEvent.OrderSaved -> onNavigateBack()
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(OrderFormAction.OnErrorDismiss)
        }
    }

    OrderFormScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreen(
    state: OrderFormState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (OrderFormAction) -> Unit
) {
    val stepLabels = listOf(
        stringResource(Res.string.order_form_step_customer),
        stringResource(Res.string.order_form_step_items),
        stringResource(Res.string.order_form_step_details)
    )
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEditMode) {
                            stringResource(Res.string.order_form_title_edit)
                        } else {
                            stringResource(Res.string.order_form_title_add)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(OrderFormAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // Step indicator
            Column(
                modifier = Modifier.padding(horizontal = DesignTokens.space4)
            ) {
                LinearProgressIndicator(
                    progress = { state.currentStep / 3f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(DesignTokens.space2))
                Text(
                    text = stringResource(
                        Res.string.order_form_step_indicator,
                        state.currentStep,
                        3
                    ) + " \u2014 " + stepLabels[state.currentStep - 1],
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(DesignTokens.space4))

            // Step content
            AnimatedContent(
                targetState = state.currentStep,
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    1 -> CustomerSelectionStep(state = state, onAction = onAction)
                    2 -> ItemsStep(state = state, onAction = onAction)
                    3 -> DetailsStep(state = state, onAction = onAction)
                }
            }

            // Bottom navigation buttons
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space4)
            ) {
                if (state.currentStep > 1) {
                    TextButton(onClick = { onAction(OrderFormAction.OnPreviousStep) }) {
                        Text(stringResource(Res.string.order_form_previous))
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (state.currentStep < 3) {
                    val canAdvance = when (state.currentStep) {
                        1 -> state.selectedCustomer != null
                        2 -> state.items.any { it.garmentType != null && it.price.toDoubleOrNull() != null }
                        else -> true
                    }
                    Button(
                        onClick = { onAction(OrderFormAction.OnNextStep) },
                        enabled = canAdvance,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(DesignTokens.radiusMd)
                    ) {
                        Text(
                            text = stringResource(Res.string.order_form_next),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = { onAction(OrderFormAction.OnSave) },
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(DesignTokens.radiusMd)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = if (state.isEditMode) {
                                    stringResource(Res.string.order_form_save_button)
                                } else {
                                    stringResource(Res.string.order_form_create_button)
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Step 1: Customer Selection ──────────────────────────────────────────

@Composable
private fun CustomerSelectionStep(
    state: OrderFormState,
    onAction: (OrderFormAction) -> Unit
) {
    val query = state.customerSearchQuery.lowercase().trim()
    val filteredCustomers = if (query.isBlank()) {
        state.customers
    } else {
        state.customers.filter {
            it.name.lowercase().contains(query) || it.phone.contains(query)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selected customer indicator
        if (state.selectedCustomer != null) {
            Surface(
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.space4)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(DesignTokens.space3)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(DesignTokens.space2))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selectedCustomer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.selectedCustomer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(DesignTokens.space3))
        }

        // Search field
        OutlinedTextField(
            value = state.customerSearchQuery,
            onValueChange = { onAction(OrderFormAction.OnCustomerSearchChange(it)) },
            placeholder = {
                Text(
                    text = stringResource(Res.string.order_form_search_customers),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space4)
        )

        Spacer(Modifier.height(DesignTokens.space2))

        if (state.selectedCustomer == null) {
            Text(
                text = stringResource(Res.string.order_form_select_customer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DesignTokens.space4)
            )
        }

        Spacer(Modifier.height(DesignTokens.space2))

        LazyColumn(
            contentPadding = PaddingValues(bottom = DesignTokens.space4)
        ) {
            items(items = filteredCustomers, key = { it.id }) { customer ->
                val isSelected = state.selectedCustomer?.id == customer.id
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(OrderFormAction.OnSelectCustomer(customer)) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = DesignTokens.space4,
                            vertical = DesignTokens.space3
                        )
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = customer.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ── Step 2: Items ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemsStep(
    state: OrderFormState,
    onAction: (OrderFormAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.space4)
    ) {
        state.items.forEachIndexed { index, item ->
            // Keyed by item.id so remembered state (e.g. selectedGenderFilter) stays
            // tied to the item across add/remove/reorder instead of sliding by position.
            key(item.id) {
                OrderItemCard(
                    item = item,
                    index = index,
                    showRemove = state.items.size > 1,
                    availableStyles = state.availableStyles,
                    availableMeasurements = state.availableMeasurements,
                    onAction = onAction
                )
                Spacer(Modifier.height(DesignTokens.space3))
            }
        }

        TextButton(
            onClick = { onAction(OrderFormAction.OnAddItem) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DesignTokens.space1))
            Text(stringResource(Res.string.order_form_add_item))
        }

        Spacer(Modifier.height(DesignTokens.space4))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
private fun OrderItemCard(
    item: OrderItemFormState,
    index: Int,
    showRemove: Boolean,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    availableMeasurements: List<com.danzucker.stitchpad.core.domain.model.Measurement>,
    onAction: (OrderFormAction) -> Unit
) {
    Card(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.order_form_item_number, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (showRemove) {
                    IconButton(
                        onClick = { onAction(OrderFormAction.OnRemoveItem(item.id)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.order_form_remove_item),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(DesignTokens.space2))

            // Gender filter chips. Keyed by item.id via the outer key(item.id) so remember
            // stays tied to this item on add/remove. Initial value follows the item's existing
            // selection so editing a saved Female garment shows the Female chip preselected.
            var selectedGenderFilter by remember {
                mutableStateOf(item.garmentType?.gender ?: GarmentGender.MALE)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                GarmentGender.entries.forEach { gender ->
                    val isSelected = selectedGenderFilter == gender
                    val label = garmentGenderLabel(gender)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGenderFilter = gender },
                        label = {
                            Text(
                                text = label,
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

            Spacer(Modifier.height(DesignTokens.space2))

            // Garment type dropdown (filtered by gender)
            val filteredGarmentTypes = GarmentType.entries.filter { it.gender == selectedGenderFilter }
            var garmentExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = garmentExpanded,
                onExpandedChange = { garmentExpanded = it }
            ) {
                OutlinedTextField(
                    value = item.garmentType?.let { garmentDisplayName(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.order_form_garment_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = garmentExpanded) },
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = garmentExpanded,
                    onDismissRequest = { garmentExpanded = false }
                ) {
                    filteredGarmentTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(garmentDisplayName(type)) },
                            onClick = {
                                onAction(OrderFormAction.OnItemGarmentTypeChange(item.id, type))
                                garmentExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(DesignTokens.space2))

            // Description
            OutlinedTextField(
                value = item.description,
                onValueChange = { onAction(OrderFormAction.OnItemDescriptionChange(item.id, it)) },
                label = { Text(stringResource(Res.string.order_form_description_label)) },
                placeholder = { Text(stringResource(Res.string.order_form_description_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(DesignTokens.space2))

            // Price
            OutlinedTextField(
                value = item.price,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    onAction(OrderFormAction.OnItemPriceChange(item.id, digits))
                },
                label = { Text(stringResource(Res.string.order_form_price_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ThousandsSeparatorTransformation,
                singleLine = true,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                modifier = Modifier.fillMaxWidth()
            )

            // Style picker (optional)
            if (availableStyles.isNotEmpty()) {
                Spacer(Modifier.height(DesignTokens.space2))
                var styleExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = styleExpanded,
                    onExpandedChange = { styleExpanded = it }
                ) {
                    val selectedStyle = availableStyles.find { it.id == item.styleId }
                    OutlinedTextField(
                        value = selectedStyle?.description ?: stringResource(Res.string.order_form_no_style),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.order_form_style_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = styleExpanded,
                        onDismissRequest = { styleExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.order_form_no_style)) },
                            onClick = {
                                onAction(OrderFormAction.OnItemStyleChange(item.id, null))
                                styleExpanded = false
                            }
                        )
                        availableStyles.forEach { style ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = style.description,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    onAction(OrderFormAction.OnItemStyleChange(item.id, style.id))
                                    styleExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Measurement picker (optional)
            if (availableMeasurements.isNotEmpty()) {
                Spacer(Modifier.height(DesignTokens.space2))
                var measurementExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = measurementExpanded,
                    onExpandedChange = { measurementExpanded = it }
                ) {
                    val selectedMeasurement = availableMeasurements.find { it.id == item.measurementId }
                    val measurementLabel = if (selectedMeasurement != null) {
                        "${selectedMeasurement.gender.name} \u2014 ${selectedMeasurement.fields.size} fields"
                    } else {
                        stringResource(Res.string.order_form_no_measurement)
                    }
                    OutlinedTextField(
                        value = measurementLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.order_form_measurement_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = measurementExpanded) },
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = measurementExpanded,
                        onDismissRequest = { measurementExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.order_form_no_measurement)) },
                            onClick = {
                                onAction(OrderFormAction.OnItemMeasurementChange(item.id, null))
                                measurementExpanded = false
                            }
                        )
                        availableMeasurements.forEach { measurement ->
                            DropdownMenuItem(
                                text = {
                                    Text("${measurement.gender.name} \u2014 ${measurement.fields.size} fields")
                                },
                                onClick = {
                                    onAction(OrderFormAction.OnItemMeasurementChange(item.id, measurement.id))
                                    measurementExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Fabric photo
            Spacer(Modifier.height(DesignTokens.space3))
            Text(
                text = stringResource(Res.string.order_form_fabric_photo_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(DesignTokens.space1))

            val hasFabricPhoto = item.fabricPhotoBytes != null || item.fabricPhotoUrl != null

            if (hasFabricPhoto) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (item.fabricPhotoBytes != null) {
                        AsyncImage(
                            model = item.fabricPhotoBytes,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SubcomposeAsyncImage(
                            model = item.fabricPhotoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LoadingDots()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Remove button
                    IconButton(
                        onClick = { onAction(OrderFormAction.OnItemFabricPhotoRemoved(item.id)) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                FabricPhotoPickerButton(
                    itemId = item.id,
                    onAction = onAction
                )
            }
        }
    }
}

private enum class PhotoSource { Camera, Gallery }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabricPhotoPickerButton(
    itemId: String,
    onAction: (OrderFormAction) -> Unit
) {
    val pickerScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showSheet by remember { mutableStateOf(false) }
    var pendingSource by remember { mutableStateOf<PhotoSource?>(null) }

    val galleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                onAction(OrderFormAction.OnItemFabricPhotoPicked(itemId, it))
            }
        }
    )
    val cameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) {
            onAction(OrderFormAction.OnItemFabricPhotoPicked(itemId, bytes))
        }
    }

    // Launch AFTER the sheet has been removed from composition. Launching while the
    // ModalBottomSheet's popup window is still present causes the launch intent to
    // dispatch into a disappearing window and the camera/gallery never opens.
    LaunchedEffect(showSheet, pendingSource) {
        if (!showSheet && pendingSource != null) {
            when (pendingSource) {
                PhotoSource.Camera -> cameraLauncher.launch()
                PhotoSource.Gallery -> galleryPicker.launch()
                null -> {}
            }
            pendingSource = null
        }
    }

    Surface(
        onClick = {
            focusManager.clearFocus()
            showSheet = true
        },
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DesignTokens.space3)
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.order_form_snap_fabric),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PhotoSourceSheetContent(
                onTakePhoto = {
                    pendingSource = PhotoSource.Camera
                    showSheet = false
                },
                onPickGallery = {
                    pendingSource = PhotoSource.Gallery
                    showSheet = false
                },
                onCancel = { showSheet = false }
            )
        }
    }
}

@Composable
private fun PhotoSourceSheetContent(
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                bottom = DesignTokens.space6
            )
    ) {
        Text(
            text = stringResource(Res.string.order_form_photo_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = DesignTokens.space2,
                bottom = DesignTokens.space3
            )
        )
        PhotoSourceRow(
            icon = Icons.Default.CameraAlt,
            label = stringResource(Res.string.order_form_photo_take),
            onClick = onTakePhoto
        )
        PhotoSourceRow(
            icon = Icons.Default.PhotoLibrary,
            label = stringResource(Res.string.order_form_photo_pick),
            onClick = onPickGallery
        )
        Spacer(Modifier.height(DesignTokens.space2))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.order_form_photo_cancel))
        }
    }
}

@Composable
private fun PhotoSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = DesignTokens.space2,
                vertical = DesignTokens.space3
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(DesignTokens.space3))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Step 3: Details ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsStep(
    state: OrderFormState,
    onAction: (OrderFormAction) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.space4)
    ) {
        // Deadline
        Text(
            text = stringResource(Res.string.order_form_deadline_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(DesignTokens.space1))

        val deadlineText = if (state.deadline != null) {
            val instant = Instant.fromEpochMilliseconds(state.deadline)
            val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
            "${date.dayOfMonth}/${date.monthNumber}/${date.year}"
        } else {
            stringResource(Res.string.order_form_pick_date)
        }

        Surface(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space3)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(DesignTokens.space2))
                Text(
                    text = deadlineText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.deadline != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (state.deadline != null) {
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onAction(OrderFormAction.OnDeadlineChange(null)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(DesignTokens.space4))

        // Priority
        Text(
            text = stringResource(Res.string.order_form_priority_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(DesignTokens.space1))

        val priorityOptions = listOf(
            OrderPriority.NORMAL to stringResource(Res.string.order_priority_normal),
            OrderPriority.URGENT to stringResource(Res.string.order_priority_urgent),
            OrderPriority.RUSH to stringResource(Res.string.order_priority_rush)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
            priorityOptions.forEach { (priority, label) ->
                val isSelected = state.priority == priority
                val chipColor = when (priority) {
                    OrderPriority.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                    OrderPriority.URGENT -> DesignTokens.warning500
                    OrderPriority.RUSH -> DesignTokens.error500
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onAction(OrderFormAction.OnPriorityChange(priority)) },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.15f),
                        selectedLabelColor = chipColor,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (isSelected) {
                        BorderStroke(1.dp, chipColor)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    }
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space4))

        // Deposit
        OutlinedTextField(
            value = state.depositPaid,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                onAction(OrderFormAction.OnDepositChange(digits))
            },
            visualTransformation = ThousandsSeparatorTransformation,
            label = { Text(stringResource(Res.string.order_form_deposit_label)) },
            placeholder = { Text(stringResource(Res.string.order_form_deposit_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(DesignTokens.space4))

        // Notes
        OutlinedTextField(
            value = state.notes,
            onValueChange = { onAction(OrderFormAction.OnNotesChange(it)) },
            label = { Text(stringResource(Res.string.order_form_notes_label)) },
            placeholder = { Text(stringResource(Res.string.order_form_notes_placeholder)) },
            minLines = 3,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(DesignTokens.space4))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.deadline)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onAction(OrderFormAction.OnDeadlineChange(datePickerState.selectedDateMillis))
                    showDatePicker = false
                }) {
                    Text(stringResource(Res.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun garmentGenderLabel(gender: GarmentGender): String = when (gender) {
    GarmentGender.MALE -> stringResource(Res.string.garment_gender_male)
    GarmentGender.FEMALE -> stringResource(Res.string.garment_gender_female)
    GarmentGender.UNISEX -> stringResource(Res.string.garment_gender_unisex)
}

private object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formatted = buildString {
            original.reversed().forEachIndexed { i, c ->
                if (i > 0 && i % 3 == 0) append(',')
                append(c)
            }
        }.reversed()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                val totalLength = original.length
                var commasBeforePos = 0
                for (i in 0 until offset) {
                    val distFromRight = totalLength - 1 - i
                    if (distFromRight > 0 && distFromRight % 3 == 0) commasBeforePos++
                }
                return offset + commasBeforePos
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = 0
                var transformedOffset = 0
                for (i in formatted.indices) {
                    if (transformedOffset >= offset) break
                    transformedOffset++
                    if (formatted[i] != ',') originalOffset++
                }
                return originalOffset.coerceAtMost(original.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

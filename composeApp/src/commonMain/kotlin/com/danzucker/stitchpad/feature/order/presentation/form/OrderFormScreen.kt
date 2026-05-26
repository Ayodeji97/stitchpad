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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.GarmentGender
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.media.rememberImageCaptureLauncher
import com.danzucker.stitchpad.feature.order.presentation.form.components.StylePickerSheet
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.components.CustomDatePickerDialog
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.ThousandsSeparatorTransformation
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.garment_gender_female
import stitchpad.composeapp.generated.resources.garment_gender_male
import stitchpad.composeapp.generated.resources.garment_gender_unisex
import stitchpad.composeapp.generated.resources.order_form_add_item
import stitchpad.composeapp.generated.resources.order_form_create_button
import stitchpad.composeapp.generated.resources.order_form_deadline_label
import stitchpad.composeapp.generated.resources.order_form_deposit_edit_locked_hint
import stitchpad.composeapp.generated.resources.order_form_deposit_label
import stitchpad.composeapp.generated.resources.order_form_deposit_placeholder
import stitchpad.composeapp.generated.resources.order_form_description_label
import stitchpad.composeapp.generated.resources.order_form_description_placeholder
import stitchpad.composeapp.generated.resources.order_form_fabric_name_label
import stitchpad.composeapp.generated.resources.order_form_fabric_name_placeholder
import stitchpad.composeapp.generated.resources.order_form_fabric_photo_label
import stitchpad.composeapp.generated.resources.order_form_garment_type_label
import stitchpad.composeapp.generated.resources.order_form_item_number
import stitchpad.composeapp.generated.resources.order_form_measurement_label
import stitchpad.composeapp.generated.resources.order_form_next
import stitchpad.composeapp.generated.resources.order_form_no_measurement
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
import stitchpad.composeapp.generated.resources.order_form_style_change
import stitchpad.composeapp.generated.resources.order_form_style_description_label
import stitchpad.composeapp.generated.resources.order_form_style_description_placeholder
import stitchpad.composeapp.generated.resources.order_form_style_from_gallery_caption
import stitchpad.composeapp.generated.resources.order_form_style_pick_from_gallery
import stitchpad.composeapp.generated.resources.order_form_style_remove
import stitchpad.composeapp.generated.resources.order_form_style_save_to_gallery
import stitchpad.composeapp.generated.resources.order_form_style_section_title
import stitchpad.composeapp.generated.resources.order_form_style_upload_new
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
@Suppress("CyclomaticComplexMethod")
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

            state.stylePickerSheetForItemId?.let { itemId ->
                StylePickerSheet(
                    styles = state.availableStyles,
                    onSelect = { style ->
                        onAction(OrderFormAction.OnItemStyleChange(itemId, style.id))
                        onAction(OrderFormAction.OnDismissStylePickerSheet)
                    },
                    onDismiss = { onAction(OrderFormAction.OnDismissStylePickerSheet) },
                )
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
                        2 -> {
                            // Match save() validation: at least one typed item AND every typed
                            // item must have a positive price. Otherwise Next leads to a
                            // guaranteed save failure at step 3.
                            val typed = state.items.filter { it.garmentType != null }
                            typed.isNotEmpty() &&
                                typed.all { (it.price.toDoubleOrNull() ?: 0.0) > 0.0 }
                        }
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
    // Locked customers are read-only by design (PR #59 spec decision #2 — "lock
    // gates new work, not viewing"). Filter them out of the order picker so a
    // new order can't be created against a locked customer; the locked customer
    // detail screen's "Unlock with Pro" CTA is the conversion path.
    // state.customers stays the full list for resolvePendingCustomer to still
    // resolve an edit-existing-order-whose-customer-was-since-locked case.
    val activeOnly = state.customers.filter { it.slotState == CustomerSlotState.ACTIVE }
    val filteredCustomers = if (query.isBlank()) {
        activeOnly
    } else {
        activeOnly.filter {
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
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun OrderItemCard(
    item: OrderItemFormState,
    index: Int,
    showRemove: Boolean,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    availableMeasurements: List<com.danzucker.stitchpad.core.domain.model.Measurement>,
    onAction: (OrderFormAction) -> Unit
) {
    var fullScreenImage: Any? by remember { mutableStateOf<Any?>(null) }
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
                        onClick = {
                            selectedGenderFilter = gender
                            // If the previously selected garment doesn't belong to the new
                            // gender, clear it so the dropdown label matches what's in-list.
                            val current = item.garmentType
                            if (current != null && current.gender != gender) {
                                onAction(OrderFormAction.OnItemGarmentTypeChange(item.id, null))
                            }
                        },
                        label = {
                            Text(
                                text = label,
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

            // PTSP-9 Style section — replaces the prior dropdown with a unified
            // pick-or-upload UI. Always rendered; the "Pick from gallery" button
            // is disabled when the customer has no gallery styles.
            Spacer(Modifier.height(DesignTokens.space3))
            StyleSection(
                item = item,
                availableStyles = availableStyles,
                onAction = onAction,
                onPreview = { fullScreenImage = it },
            )

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

            // Fabric name
            Spacer(Modifier.height(DesignTokens.space3))
            OutlinedTextField(
                value = item.fabricName,
                onValueChange = { onAction(OrderFormAction.OnItemFabricNameChange(item.id, it)) },
                label = { Text(stringResource(Res.string.order_form_fabric_name_label)) },
                placeholder = { Text(stringResource(Res.string.order_form_fabric_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                modifier = Modifier.fillMaxWidth(),
            )

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
                        .clickable {
                            fullScreenImage = item.fabricPhotoBytes ?: item.fabricPhotoUrl
                        }
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
    FullScreenImageViewer(
        model = fullScreenImage,
        contentDescription = null,
        onDismiss = { fullScreenImage = null },
    )
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
            readOnly = state.isEditMode,
            supportingText = if (state.isEditMode) {
                { Text(stringResource(Res.string.order_form_deposit_edit_locked_hint)) }
            } else {
                null
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
        val timeZone = TimeZone.currentSystemDefault()
        val initialDate = state.deadline?.let { millis ->
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
        }
        CustomDatePickerDialog(
            initial = initialDate,
            timeZone = timeZone,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                val millis = picked.atStartOfDayIn(timeZone).toEpochMilliseconds()
                onAction(OrderFormAction.OnDeadlineChange(millis))
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun garmentGenderLabel(gender: GarmentGender): String = when (gender) {
    GarmentGender.MALE -> stringResource(Res.string.garment_gender_male)
    GarmentGender.FEMALE -> stringResource(Res.string.garment_gender_female)
    GarmentGender.UNISEX -> stringResource(Res.string.garment_gender_unisex)
}

// ── PTSP-9 Style section composables ────────────────────────────────────

@Suppress("LongMethod")
@Composable
private fun StyleSection(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (Any?) -> Unit,
) {
    Text(
        text = stringResource(Res.string.order_form_style_section_title),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(DesignTokens.space2))

    val pickedStyle = availableStyles.find { it.id == item.styleId }
    val hasUploadBytes = item.stylePhotoBytes != null
    val hasUploadUrl = item.stylePhotoUrl != null && item.styleId == null

    when {
        // State C — new image uploaded inline (bytes still in memory)
        hasUploadBytes -> {
            StyleSectionUploaded(
                item = item,
                isEditable = true,
                onAction = onAction,
                onPreview = onPreview,
            )
        }
        // State C-readonly — order being edited, image was previously uploaded one-off
        hasUploadUrl -> {
            StyleSectionUploaded(
                item = item,
                isEditable = false,
                onAction = onAction,
                onPreview = onPreview,
            )
        }
        // State B — existing gallery style picked
        pickedStyle != null -> {
            StyleSectionExisting(
                style = pickedStyle,
                onChange = { onAction(OrderFormAction.OnOpenStylePickerSheet(item.id)) },
                onRemove = { onAction(OrderFormAction.OnItemStyleChange(item.id, null)) },
                onPreview = onPreview,
            )
        }
        // State A — empty
        else -> {
            StyleSectionEmpty(
                itemId = item.id,
                hasGalleryStyles = availableStyles.isNotEmpty(),
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun StyleSectionEmpty(
    itemId: String,
    hasGalleryStyles: Boolean,
    onAction: (OrderFormAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = { onAction(OrderFormAction.OnOpenStylePickerSheet(itemId)) },
            enabled = hasGalleryStyles,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(Res.string.order_form_style_pick_from_gallery))
        }
        StyleUploadButton(
            itemId = itemId,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StyleSectionExisting(
    style: com.danzucker.stitchpad.core.domain.model.Style,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onPreview: (Any?) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .clickable { onPreview(style.photoUrl) },
                ) {
                    SubcomposeAsyncImage(
                        model = style.photoUrl,
                        contentDescription = null,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LoadingDots(dotSize = 5.dp)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = style.description.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.order_form_style_from_gallery_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.space2))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onChange) {
                    Text(stringResource(Res.string.order_form_style_change))
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(Res.string.order_form_style_remove))
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun StyleSectionUploaded(
    item: OrderItemFormState,
    isEditable: Boolean,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (Any?) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        onPreview(item.stylePhotoBytes ?: item.stylePhotoUrl)
                    },
            ) {
                val imageModel: Any? = item.stylePhotoBytes ?: item.stylePhotoUrl
                if (imageModel != null) {
                    SubcomposeAsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LoadingDots()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (isEditable) {
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = item.styleDescription,
                    onValueChange = {
                        onAction(OrderFormAction.OnItemStyleDescriptionChange(item.id, it))
                    },
                    label = { Text(stringResource(Res.string.order_form_style_description_label)) },
                    placeholder = { Text(stringResource(Res.string.order_form_style_description_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(DesignTokens.space2))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    Switch(
                        checked = item.saveStyleToGallery,
                        onCheckedChange = {
                            onAction(OrderFormAction.OnItemSaveStyleToGalleryToggle(item.id, it))
                        },
                    )
                    Text(
                        text = stringResource(Res.string.order_form_style_save_to_gallery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(DesignTokens.space2))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyleUploadButton(
                    itemId = item.id,
                    onAction = onAction,
                    label = stringResource(Res.string.order_form_style_change),
                )
                TextButton(onClick = { onAction(OrderFormAction.OnItemStylePhotoRemoved(item.id)) }) {
                    Text(stringResource(Res.string.order_form_style_remove))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleUploadButton(
    itemId: String,
    onAction: (OrderFormAction) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
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
                onAction(OrderFormAction.OnItemStylePhotoPicked(itemId, it))
            }
        }
    )
    val cameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) {
            onAction(OrderFormAction.OnItemStylePhotoPicked(itemId, bytes))
        }
    }

    LaunchedEffect(showSheet, pendingSource) {
        if (!showSheet && pendingSource != null) {
            when (pendingSource) {
                PhotoSource.Camera -> cameraLauncher.launch()
                PhotoSource.Gallery -> galleryPicker.launch()
                null -> Unit
            }
            pendingSource = null
        }
    }

    OutlinedButton(
        onClick = {
            focusManager.clearFocus()
            showSheet = true
        },
        modifier = modifier,
    ) {
        Text(label ?: stringResource(Res.string.order_form_style_upload_new))
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingSource = PhotoSource.Camera
                        showSheet = false
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingSource = PhotoSource.Gallery
                        showSheet = false
                    },
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderFormScreenStep1Preview() {
    StitchPadTheme {
        OrderFormScreen(
            state = OrderFormState(currentStep = 1),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderFormScreenStep2Preview() {
    StitchPadTheme {
        OrderFormScreen(
            state = OrderFormState(
                currentStep = 2,
                items = listOf(
                    OrderItemFormState(
                        garmentType = GarmentType.AGBADA,
                        description = "Royal blue lace",
                        price = "100000"
                    )
                )
            ),
            onAction = {}
        )
    }
}

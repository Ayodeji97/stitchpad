package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.GarmentGender
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.media.rememberImageCaptureLauncher
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.order.domain.discountBreakdown
import com.danzucker.stitchpad.feature.order.presentation.components.StylePickerSheet
import com.danzucker.stitchpad.feature.order.presentation.form.components.GarmentPickerSheet
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.feature.style.presentation.form.styleFormSelectionMode
import com.danzucker.stitchpad.ui.components.CustomDatePickerDialog
import com.danzucker.stitchpad.ui.components.FullScreenImageViewer
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.ThousandsSeparatorTransformation
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.BackHandler
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.danzucker.stitchpad.util.dismissKeyboardOnScroll
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.garment_gender_female
import stitchpad.composeapp.generated.resources.garment_gender_male
import stitchpad.composeapp.generated.resources.garment_gender_unisex
import stitchpad.composeapp.generated.resources.garment_picker_custom_pill
import stitchpad.composeapp.generated.resources.garment_picker_saved_snackbar_format
import stitchpad.composeapp.generated.resources.order_form_add_item
import stitchpad.composeapp.generated.resources.order_form_create_button
import stitchpad.composeapp.generated.resources.order_form_deadline_label
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_body
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_body_with_other_payments
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_cancel
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_confirm
import stitchpad.composeapp.generated.resources.order_form_deposit_dialog_title
import stitchpad.composeapp.generated.resources.order_form_deposit_label
import stitchpad.composeapp.generated.resources.order_form_deposit_placeholder
import stitchpad.composeapp.generated.resources.order_form_description_label
import stitchpad.composeapp.generated.resources.order_form_description_placeholder
import stitchpad.composeapp.generated.resources.order_form_discount_label
import stitchpad.composeapp.generated.resources.order_form_discount_reason_label
import stitchpad.composeapp.generated.resources.order_form_discount_reason_placeholder
import stitchpad.composeapp.generated.resources.order_form_fabric_name_label
import stitchpad.composeapp.generated.resources.order_form_fabric_name_placeholder
import stitchpad.composeapp.generated.resources.order_form_fabric_section_title
import stitchpad.composeapp.generated.resources.order_form_fabric_sheet_title
import stitchpad.composeapp.generated.resources.order_form_garment_type_label
import stitchpad.composeapp.generated.resources.order_form_image_add_tile
import stitchpad.composeapp.generated.resources.order_form_image_badge_library
import stitchpad.composeapp.generated.resources.order_form_image_badge_new
import stitchpad.composeapp.generated.resources.order_form_image_count_fmt
import stitchpad.composeapp.generated.resources.order_form_item_number
import stitchpad.composeapp.generated.resources.order_form_line_total
import stitchpad.composeapp.generated.resources.order_form_measurement_label
import stitchpad.composeapp.generated.resources.order_form_next
import stitchpad.composeapp.generated.resources.order_form_next_blocked_customer
import stitchpad.composeapp.generated.resources.order_form_next_blocked_item
import stitchpad.composeapp.generated.resources.order_form_no_measurement
import stitchpad.composeapp.generated.resources.order_form_notes_label
import stitchpad.composeapp.generated.resources.order_form_notes_placeholder
import stitchpad.composeapp.generated.resources.order_form_photo_pick
import stitchpad.composeapp.generated.resources.order_form_photo_pick_support
import stitchpad.composeapp.generated.resources.order_form_photo_take
import stitchpad.composeapp.generated.resources.order_form_photo_take_support
import stitchpad.composeapp.generated.resources.order_form_pick_date
import stitchpad.composeapp.generated.resources.order_form_previous
import stitchpad.composeapp.generated.resources.order_form_price_label
import stitchpad.composeapp.generated.resources.order_form_priority_label
import stitchpad.composeapp.generated.resources.order_form_quantity_label
import stitchpad.composeapp.generated.resources.order_form_remove_item
import stitchpad.composeapp.generated.resources.order_form_save_button
import stitchpad.composeapp.generated.resources.order_form_search_customers
import stitchpad.composeapp.generated.resources.order_form_select_customer
import stitchpad.composeapp.generated.resources.order_form_step_customer
import stitchpad.composeapp.generated.resources.order_form_step_details
import stitchpad.composeapp.generated.resources.order_form_step_indicator
import stitchpad.composeapp.generated.resources.order_form_step_items
import stitchpad.composeapp.generated.resources.order_form_style_description_label
import stitchpad.composeapp.generated.resources.order_form_style_description_placeholder
import stitchpad.composeapp.generated.resources.order_form_style_pick_from_saved
import stitchpad.composeapp.generated.resources.order_form_style_pick_from_saved_support
import stitchpad.composeapp.generated.resources.order_form_style_save_to_gallery
import stitchpad.composeapp.generated.resources.order_form_style_section_title
import stitchpad.composeapp.generated.resources.order_form_style_sheet_title
import stitchpad.composeapp.generated.resources.order_form_summary_discount
import stitchpad.composeapp.generated.resources.order_form_summary_discount_plain
import stitchpad.composeapp.generated.resources.order_form_summary_item_qty
import stitchpad.composeapp.generated.resources.order_form_summary_subtotal
import stitchpad.composeapp.generated.resources.order_form_summary_title
import stitchpad.composeapp.generated.resources.order_form_summary_total
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
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OrderFormEvent.NavigateBack -> onNavigateBack()
            OrderFormEvent.OrderSaved -> onNavigateBack()
            is OrderFormEvent.ShowCustomSavedSnackbar -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        getString(
                            Res.string.garment_picker_saved_snackbar_format,
                            event.name,
                        )
                    )
                }
            }
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

    // Android hardware/gesture back steps to the previous wizard step instead of
    // abandoning the form. On step 1 it stays disabled so back exits normally.
    // (iOS is a no-op here and relies on the top-bar arrow via OnNavigateBack.)
    BackHandler(enabled = state.currentStep > 1) {
        onAction(OrderFormAction.OnPreviousStep)
    }

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
                val targetItem = state.items.find { it.id == itemId }
                if (targetItem != null) {
                    val alreadyAddedIds = targetItem.styleImageRefs
                        .filter { it.source == StyleImageSource.LIBRARY }
                        .mapNotNull { it.styleId }
                        .toSet()
                    StylePickerSheet(
                        closetFolders = state.closetFolders,
                        inspirationFolders = state.inspirationFolders,
                        selectedSource = state.stylePickerSource,
                        onSourceChange = { onAction(OrderFormAction.OnStylePickerSourceChange(it)) },
                        pickerOpenFolderKey = state.pickerOpenFolderKey,
                        onFolderOpen = { onAction(OrderFormAction.OnPickerFolderOpen(it)) },
                        onFolderBack = { onAction(OrderFormAction.OnPickerFolderBack) },
                        alreadyAddedStyleIds = alreadyAddedIds,
                        committedSlots = targetItem.styleImageRefs.size +
                            targetItem.uploadedStyleBytesList.size,
                        pendingStyleIds = state.stylePickerPendingIds,
                        maxRefs = MAX_IMAGES_PER_CATEGORY,
                        onToggle = { onAction(OrderFormAction.OnItemTogglePendingStyle(it.id)) },
                        onDone = { onAction(OrderFormAction.OnItemCommitPendingStyles(itemId)) },
                        onDismiss = { onAction(OrderFormAction.OnDismissStylePickerSheet) },
                    )
                }
            }

            val activePickerItemId = state.activePickerItemId
            if (activePickerItemId != null) {
                // Use the lifted genderFilter from the active item's state so the picker
                // shows only garments matching the gender chip the user has selected.
                val activeItem = state.items.firstOrNull { it.id == activePickerItemId }
                val activeGenderFilter = activeItem?.genderFilter ?: GarmentGender.MALE
                GarmentPickerSheet(
                    customs = state.customGarmentTypes,
                    presets = GarmentType.entries.filter { it != GarmentType.OTHER && it.gender == activeGenderFilter },
                    searchQuery = state.pickerSearchQuery,
                    onSearchChange = { onAction(OrderFormAction.OnPickerSearchChange(it)) },
                    onPickPreset = { type ->
                        onAction(
                            OrderFormAction.OnPickGarmentType(
                                itemId = activePickerItemId,
                                garmentType = type,
                                customName = null,
                            )
                        )
                    },
                    onPickCustom = { custom ->
                        onAction(
                            OrderFormAction.OnPickGarmentType(
                                itemId = activePickerItemId,
                                garmentType = GarmentType.OTHER,
                                customName = custom.name,
                            )
                        )
                    },
                    onAddCustom = { typed ->
                        onAction(
                            OrderFormAction.OnAddCustomGarmentType(
                                itemId = activePickerItemId,
                                name = typed,
                            )
                        )
                    },
                    onDismiss = { onAction(OrderFormAction.OnDismissPicker) },
                )
            }

            val canAdvance = when (state.currentStep) {
                1 -> state.selectedCustomer != null
                2 -> {
                    val typed = state.items.filter { it.garmentType != null }
                    typed.isNotEmpty() &&
                        typed.all { (it.quantity.toIntOrNull() ?: 0) > 0 } &&
                        typed.all { (it.price.toDoubleOrNull() ?: 0.0) > 0.0 } &&
                        // Mirrors the save-side hasOrphanedOther guard — items with
                        // garmentType == OTHER must have a non-blank customGarmentName
                        // so users can't advance to step 3 and lose that work on save.
                        typed.all { item ->
                            item.garmentType != GarmentType.OTHER || !item.customGarmentName.isNullOrBlank()
                        }
                }
                else -> true
            }
            val nextBlockedMessage = when {
                state.currentStep == 1 && !canAdvance ->
                    stringResource(Res.string.order_form_next_blocked_customer)
                state.currentStep == 2 && !canAdvance ->
                    stringResource(Res.string.order_form_next_blocked_item)
                else -> null
            }
            if (nextBlockedMessage != null) {
                Text(
                    text = nextBlockedMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2),
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
                    StitchPadButton(
                        text = stringResource(Res.string.order_form_next),
                        onClick = { onAction(OrderFormAction.OnNextStep) },
                        enabled = canAdvance,
                    )
                } else {
                    StitchPadButton(
                        text = if (state.isEditMode) {
                            stringResource(Res.string.order_form_save_button)
                        } else {
                            stringResource(Res.string.order_form_create_button)
                        },
                        onClick = { onAction(OrderFormAction.OnSave) },
                        isLoading = state.isSaving,
                    )
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
            contentPadding = PaddingValues(bottom = DesignTokens.space4),
            modifier = Modifier.dismissKeyboardOnScroll()
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
            .dismissKeyboardOnScroll()
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
                    inspirationStyles = state.inspirationStyles,
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
    inspirationStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    availableMeasurements: List<com.danzucker.stitchpad.core.domain.model.Measurement>,
    onAction: (OrderFormAction) -> Unit
) {
    var previewSet: ImagePreviewSet? by remember { mutableStateOf(null) }
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

            // Gender filter chips. Selection is lifted into OrderItemFormState so the
            // picker can be opened with the correct gender filter, regardless of whether
            // a garment has already been chosen.
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                GarmentGender.entries.forEach { gender ->
                    val isSelected = item.genderFilter == gender
                    val label = garmentGenderLabel(gender)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onAction(OrderFormAction.OnItemGenderFilterChange(item.id, gender))
                            // If the previously selected garment doesn't belong to the new
                            // gender, clear it so the field label matches what's in-list.
                            // Don't clear custom-garment selections on gender chip change.
                            // OTHER.gender is UNISEX so the naive incompatibility check always
                            // returns true for MALE/FEMALE chips — but the user's custom name
                            // has no gender meaning. Leave it alone.
                            val current = item.garmentType
                            if (current != null && current != GarmentType.OTHER && current.gender != gender) {
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

            // Garment-type tap target — opens GarmentPickerSheet on tap
            val displayValue = when {
                item.garmentType == GarmentType.OTHER && !item.customGarmentName.isNullOrBlank() ->
                    item.customGarmentName
                item.garmentType != null -> garmentDisplayName(item.garmentType)
                else -> ""
            }
            Box {
                OutlinedTextField(
                    value = displayValue ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.order_form_garment_type_label)) },
                    trailingIcon = {
                        if (item.garmentType == GarmentType.OTHER && !item.customGarmentName.isNullOrBlank()) {
                            AssistChip(
                                onClick = { onAction(OrderFormAction.OnOpenGarmentPicker(item.id)) },
                                label = { Text(stringResource(Res.string.garment_picker_custom_pill)) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    },
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Transparent overlay so taps aren't swallowed by the read-only BasicTextField
                // focus handler — see M3 BasicTextField pointer-event consumption.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { onAction(OrderFormAction.OnOpenGarmentPicker(item.id)) }
                )
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

            // Quantity
            OutlinedTextField(
                value = item.quantity,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(MAX_QUANTITY_DIGITS)
                    onAction(OrderFormAction.OnItemQuantityChange(item.id, digits))
                },
                label = { Text(stringResource(Res.string.order_form_quantity_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

            // PTSP-26: the price field is per item, so multiplying by quantity
            // used to be a silent surprise at save time. When more than one is
            // ordered, show the resulting line total right here. Mirrors the
            // VM's unit-price × quantity math (OrderFormViewModel.save).
            val unitPrice = item.price.toDoubleOrNull()
            val lineQty = item.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
            if (unitPrice != null && unitPrice > 0.0 && lineQty > 1) {
                Spacer(Modifier.height(DesignTokens.space1))
                Text(
                    text = stringResource(
                        Res.string.order_form_line_total,
                        lineQty,
                        formatPrice(unitPrice),
                        formatPrice(unitPrice * lineQty),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            MeasurementPickerField(
                item = item,
                availableMeasurements = availableMeasurements,
                onAction = onAction,
            )

            // PTSP-11 Style section — Variant B+ multi-image inline design
            Spacer(Modifier.height(DesignTokens.space3))
            StyleImageSection(
                item = item,
                availableStyles = availableStyles,
                inspirationStyles = inspirationStyles,
                onAction = onAction,
                onPreview = { images, startIndex ->
                    previewSet = ImagePreviewSet(images = images, startIndex = startIndex)
                },
            )

            // PTSP-11 Fabric section — Variant B+ multi-image inline design
            Spacer(Modifier.height(DesignTokens.space4))
            FabricImageSection(
                item = item,
                onAction = onAction,
                onPreview = { images, startIndex ->
                    previewSet = ImagePreviewSet(images = images, startIndex = startIndex)
                },
            )
        }
    }
    previewSet?.let { preview ->
        FullScreenImageViewer(
            images = preview.images,
            startIndex = preview.startIndex,
            contentDescription = null,
            onDismiss = { previewSet = null },
        )
    }
}

private enum class PhotoSource { Camera, Gallery }

private data class ImagePreviewSet(
    val images: List<Any>,
    val startIndex: Int,
)

private data class ReferenceThumbnail(
    val combinedIndex: Int,
    val model: Any,
    val badge: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementPickerField(
    item: OrderItemFormState,
    availableMeasurements: List<com.danzucker.stitchpad.core.domain.model.Measurement>,
    onAction: (OrderFormAction) -> Unit,
) {
    if (availableMeasurements.isEmpty()) return

    Spacer(Modifier.height(DesignTokens.space2))
    var measurementExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = measurementExpanded,
        onExpandedChange = { measurementExpanded = it },
    ) {
        val selectedMeasurement = availableMeasurements.find { it.id == item.measurementId }
        val measurementLabel = if (selectedMeasurement != null) {
            "${selectedMeasurement.gender.name} - ${selectedMeasurement.fields.size} fields"
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
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = measurementExpanded,
            onDismissRequest = { measurementExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.order_form_no_measurement)) },
                onClick = {
                    onAction(OrderFormAction.OnItemMeasurementChange(item.id, null))
                    measurementExpanded = false
                },
            )
            availableMeasurements.forEach { measurement ->
                DropdownMenuItem(
                    text = {
                        Text("${measurement.gender.name} - ${measurement.fields.size} fields")
                    },
                    onClick = {
                        onAction(OrderFormAction.OnItemMeasurementChange(item.id, measurement.id))
                        measurementExpanded = false
                    },
                )
            }
        }
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
            .dismissKeyboardOnScroll()
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

        Spacer(Modifier.height(DesignTokens.space3))

        // Discount
        OutlinedTextField(
            value = state.discount,
            onValueChange = { raw -> onAction(OrderFormAction.OnDiscountChange(raw.filter { it.isDigit() })) },
            label = { Text(stringResource(Res.string.order_form_discount_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = ThousandsSeparatorTransformation,
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(DesignTokens.space3))
        OutlinedTextField(
            value = state.discountReason,
            onValueChange = { onAction(OrderFormAction.OnDiscountReasonChange(it)) },
            label = { Text(stringResource(Res.string.order_form_discount_reason_label)) },
            placeholder = { Text(stringResource(Res.string.order_form_discount_reason_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
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

        // PTSP-26: a per-item + grand total summary so the full order cost
        // (after unit price × quantity) is shown before the order is created.
        Spacer(Modifier.height(DesignTokens.space4))
        OrderTotalSummary(items = state.items, discount = state.discount)

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

    state.depositReconciliationPrompt?.let { prompt ->
        DepositReconciliationDialog(
            prompt = prompt,
            onConfirm = { onAction(OrderFormAction.OnConfirmDepositChange) },
            onDismiss = { onAction(OrderFormAction.OnDismissDepositPrompt) },
        )
    }
}

/**
 * PTSP-26: order cost summary shown on the Details step. Lists each priced
 * item (quantity × unit price → line total) and the grand total, so the figure
 * the order will actually be created with is explicit before saving. The line
 * math mirrors OrderFormViewModel's save path (price × quantity).
 */
@Suppress("CyclomaticComplexMethod")
@Composable
private fun OrderTotalSummary(items: List<OrderItemFormState>, discount: String) {
    data class PricedLine(val name: String, val qty: Int, val unit: Double)

    val lines = items.mapNotNull { item ->
        // Only count items the save path will actually persist, otherwise a
        // priced-but-incomplete row (no garment type) inflates the total beyond
        // the order that gets created. Mirrors OrderFormViewModel's save filter.
        val isPersisted = item.garmentType != null &&
            (item.garmentType != GarmentType.OTHER || !item.customGarmentName.isNullOrBlank())
        if (!isPersisted) return@mapNotNull null
        val unit = item.price.toDoubleOrNull()
        if (unit == null || unit <= 0.0) return@mapNotNull null
        val qty = item.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val name = when {
            item.garmentType == GarmentType.OTHER && !item.customGarmentName.isNullOrBlank() ->
                item.customGarmentName
            item.garmentType != null -> garmentDisplayName(item.garmentType)
            else -> ""
        }
        PricedLine(name = name ?: "", qty = qty, unit = unit)
    }

    if (lines.isEmpty()) return

    val subtotal = lines.sumOf { it.unit * it.qty }
    val breakdown = discountBreakdown(subtotal, discount)

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space3)) {
            Text(
                text = stringResource(Res.string.order_form_summary_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (line.name.isNotBlank()) {
                            Text(
                                text = line.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(
                                Res.string.order_form_summary_item_qty,
                                line.qty,
                                formatPrice(line.unit),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "₦${formatPrice(line.unit * line.qty)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.space2))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(DesignTokens.space2))
            if (breakdown.amount > 0.0) {
                // Show the percent only for a valid discount (≤ subtotal). An over-subtotal
                // entry can round to 100% (e.g. ₦1 over), so gate on the raw amount too,
                // otherwise an invalid discount the save path rejects would read "100%".
                val discountLabel = if (breakdown.amount <= subtotal && breakdown.percent in 1..100) {
                    // Pass the "%" as part of the arg (not a literal "%%" in the resource string):
                    // Compose's resource formatter doesn't collapse "%%" → "%", so it rendered "8%%".
                    stringResource(Res.string.order_form_summary_discount, "${breakdown.percent}%")
                } else {
                    stringResource(Res.string.order_form_summary_discount_plain)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.order_form_summary_subtotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "₦${formatPrice(subtotal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = discountLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "−₦${formatPrice(breakdown.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = DesignTokens.success500,
                    )
                }
                Spacer(Modifier.height(DesignTokens.space2))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.order_form_summary_total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "₦${formatPrice(breakdown.payable)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun garmentGenderLabel(gender: GarmentGender): String = when (gender) {
    GarmentGender.MALE -> stringResource(Res.string.garment_gender_male)
    GarmentGender.FEMALE -> stringResource(Res.string.garment_gender_female)
    GarmentGender.UNISEX -> stringResource(Res.string.garment_gender_unisex)
}

// ────────────────────────────────────────────────────────────────────────
// PTSP-11 — Style section (Variant B+ inline)
// ────────────────────────────────────────────────────────────────────────

private const val MAX_IMAGES_PER_CATEGORY = 3
private const val MAX_QUANTITY_DIGITS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
private fun StyleImageSection(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    inspirationStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (List<Any>, Int) -> Unit,
) {
    val savedCount = item.styleImageRefs.size
    val newCount = item.uploadedStyleBytesList.size
    val total = savedCount + newCount
    val capacityRemaining = MAX_IMAGES_PER_CATEGORY - total
    val hasUploaded = newCount > 0
    // The "pick from saved" entry must appear if EITHER source has styles — a new
    // customer can have an empty closet while Inspiration still has styles to pick.
    val hasGalleryStyles = availableStyles.isNotEmpty() || inspirationStyles.isNotEmpty()

    // The add tile is the single entry point. The sheet fans out into saved style,
    // camera, or device gallery depending on the customer's available data.
    val pickerScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showStyleSheet by remember { mutableStateOf(false) }
    var pendingStyleSource by remember { mutableStateOf<PhotoSource?>(null) }

    // Multi-select up to the remaining slots (re-keyed so it recomputes as slots
    // fill). The VM's add handler cap-guards each photo, so the loop is safe.
    val styleRemaining = capacityRemaining.coerceAtLeast(1)
    val styleGalleryPicker = key(styleRemaining) {
        rememberImagePickerLauncher(
            selectionMode = styleFormSelectionMode(
                allowMultiPhoto = true,
                maxPhotoSelection = styleRemaining,
            ),
            scope = pickerScope,
            onResult = { byteArrays ->
                byteArrays.forEach { onAction(OrderFormAction.OnItemAddStylePhoto(item.id, it)) }
            },
        )
    }
    val styleCameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) onAction(OrderFormAction.OnItemAddStylePhoto(item.id, bytes))
    }

    LaunchedEffect(showStyleSheet, pendingStyleSource) {
        if (!showStyleSheet && pendingStyleSource != null) {
            when (pendingStyleSource) {
                PhotoSource.Camera -> styleCameraLauncher.launch()
                PhotoSource.Gallery -> styleGalleryPicker.launch()
                null -> Unit
            }
            pendingStyleSource = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_form_style_section_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.12.em,
            )
            Text(
                text = stringResource(
                    Res.string.order_form_image_count_fmt,
                    total,
                    MAX_IMAGES_PER_CATEGORY,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(DesignTokens.space2))

        // Image strip — saved refs first, then session uploads, then +tile if room.
        StyleImageStrip(
            item = item,
            availableStyles = availableStyles + inspirationStyles,
            onRemove = { index -> onAction(OrderFormAction.OnItemRemoveStyleImage(item.id, index)) },
            onPreview = onPreview,
            onAddClick = if (capacityRemaining > 0) {
                {
                    focusManager.clearFocus()
                    showStyleSheet = true
                }
            } else {
                null
            },
        )

        // Description + Save-to-gallery toggle — only shown when there's at least
        // one session-uploaded image still pending save.
        if (hasUploaded) {
            Spacer(Modifier.height(DesignTokens.space3))
            OutlinedTextField(
                value = item.styleDescription,
                onValueChange = { onAction(OrderFormAction.OnItemStyleDescriptionChange(item.id, it)) },
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
        }
    }

    if (showStyleSheet) {
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
                Text(
                    text = stringResource(Res.string.order_form_style_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.space4,
                        vertical = DesignTokens.space3,
                    ),
                )
                if (hasGalleryStyles) {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.order_form_style_pick_from_saved)) },
                        supportingContent = {
                            Text(stringResource(Res.string.order_form_style_pick_from_saved_support))
                        },
                        leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showStyleSheet = false
                            onAction(OrderFormAction.OnOpenStylePickerSheet(item.id))
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
                    supportingContent = { Text(stringResource(Res.string.order_form_photo_take_support)) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingStyleSource = PhotoSource.Camera
                        showStyleSheet = false
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
                    supportingContent = { Text(stringResource(Res.string.order_form_photo_pick_support)) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingStyleSource = PhotoSource.Gallery
                        showStyleSheet = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StyleImageStrip(
    item: OrderItemFormState,
    availableStyles: List<com.danzucker.stitchpad.core.domain.model.Style>,
    onRemove: (Int) -> Unit,
    onPreview: (List<Any>, Int) -> Unit,
    onAddClick: (() -> Unit)?,
) {
    val thumbnails = buildList {
        item.styleImageRefs.forEachIndexed { index, ref ->
            val model = when (ref.source) {
                StyleImageSource.LIBRARY -> availableStyles.find { it.id == ref.styleId }?.let {
                    it.localPhotoPath ?: it.photoUrl
                }
                StyleImageSource.UPLOADED -> ref.localPhotoPath ?: ref.photoUrl
            }
            if (model != null) {
                add(
                    ReferenceThumbnail(
                        combinedIndex = index,
                        model = model,
                        badge = if (ref.source == StyleImageSource.LIBRARY) {
                            stringResource(Res.string.order_form_image_badge_library)
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        item.uploadedStyleBytesList.forEachIndexed { byteIndex, bytes ->
            add(
                ReferenceThumbnail(
                    combinedIndex = item.styleImageRefs.size + byteIndex,
                    model = bytes,
                    badge = stringResource(Res.string.order_form_image_badge_new),
                ),
            )
        }
    }
    val previewImages = thumbnails.map { it.model }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        thumbnails.forEachIndexed { previewIndex, thumbnail ->
            ImageThumbnail(
                model = thumbnail.model,
                badge = thumbnail.badge,
                onRemove = { onRemove(thumbnail.combinedIndex) },
                onTap = { onPreview(previewImages, previewIndex) },
            )
        }
        // Add tile if capacity
        if (onAddClick != null) {
            AddImageTile(onClick = onAddClick)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
// PTSP-11 — Fabric section (Variant B+ inline, mirrors style minus library + toggle)
// ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
private fun FabricImageSection(
    item: OrderItemFormState,
    onAction: (OrderFormAction) -> Unit,
    onPreview: (List<Any>, Int) -> Unit,
) {
    val total = item.fabricImageRefs.size + item.uploadedFabricBytesList.size
    val capacityRemaining = MAX_IMAGES_PER_CATEGORY - total
    val thumbnails = buildList {
        item.fabricImageRefs.forEachIndexed { index, ref ->
            add(
                ReferenceThumbnail(
                    combinedIndex = index,
                    model = ref.localPhotoPath ?: ref.photoUrl,
                    badge = null,
                ),
            )
        }
        item.uploadedFabricBytesList.forEachIndexed { byteIndex, bytes ->
            add(
                ReferenceThumbnail(
                    combinedIndex = item.fabricImageRefs.size + byteIndex,
                    model = bytes,
                    badge = stringResource(Res.string.order_form_image_badge_new),
                ),
            )
        }
    }
    val previewImages = thumbnails.map { it.model }

    // The add tile is the only visible upload action; the sheet contains the
    // capture/import choices.
    val pickerScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showFabricSheet by remember { mutableStateOf(false) }
    var pendingFabricSource by remember { mutableStateOf<PhotoSource?>(null) }

    val fabricGalleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                onAction(OrderFormAction.OnItemAddFabricPhoto(item.id, it))
            }
        },
    )
    val fabricCameraLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) onAction(OrderFormAction.OnItemAddFabricPhoto(item.id, bytes))
    }

    LaunchedEffect(showFabricSheet, pendingFabricSource) {
        if (!showFabricSheet && pendingFabricSource != null) {
            when (pendingFabricSource) {
                PhotoSource.Camera -> fabricCameraLauncher.launch()
                PhotoSource.Gallery -> fabricGalleryPicker.launch()
                null -> Unit
            }
            pendingFabricSource = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_form_fabric_section_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.12.em,
            )
            Text(
                text = stringResource(
                    Res.string.order_form_image_count_fmt,
                    total,
                    MAX_IMAGES_PER_CATEGORY,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(DesignTokens.space2))

        // Fabric image strip — same structure as StyleImageStrip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            thumbnails.forEachIndexed { previewIndex, thumbnail ->
                ImageThumbnail(
                    model = thumbnail.model,
                    badge = thumbnail.badge,
                    onRemove = {
                        onAction(OrderFormAction.OnItemRemoveFabricImage(item.id, thumbnail.combinedIndex))
                    },
                    onTap = { onPreview(previewImages, previewIndex) },
                )
            }
            // Add tile in the strip (mirrors AddImageTile usage in StyleImageStrip)
            if (capacityRemaining > 0) {
                AddImageTile(onClick = {
                    focusManager.clearFocus()
                    showFabricSheet = true
                })
            }
        }

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
    }

    if (showFabricSheet) {
        ModalBottomSheet(onDismissRequest = { showFabricSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
                Text(
                    text = stringResource(Res.string.order_form_fabric_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.space4,
                        vertical = DesignTokens.space3,
                    ),
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
                    supportingContent = { Text(stringResource(Res.string.order_form_photo_take_support)) },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingFabricSource = PhotoSource.Camera
                        showFabricSheet = false
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
                    supportingContent = { Text(stringResource(Res.string.order_form_photo_pick_support)) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pendingFabricSource = PhotoSource.Gallery
                        showFabricSheet = false
                    },
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
// Shared image thumbnail + tile composables
// ────────────────────────────────────────────────────────────────────────

@Composable
private fun ImageThumbnail(
    model: Any,
    badge: String?,
    onRemove: () -> Unit,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 82.dp, height = 100.dp)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap),
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) { LoadingDots() }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
        // Source badge
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AddImageTile(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 82.dp, height = 100.dp)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(Res.string.order_form_image_add_tile),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
            )
        }
    }
}

// ── PTSP-14 Deposit reconciliation dialog ────────────────────────────────

@Composable
private fun DepositReconciliationDialog(
    prompt: DepositPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.order_form_deposit_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        Res.string.order_form_deposit_dialog_body,
                        formatPrice(prompt.oldAmount),
                        formatPrice(prompt.newAmount),
                    ),
                )
                if (prompt.nonDepositTotal > 0.0) {
                    Spacer(Modifier.height(DesignTokens.space2))
                    Text(
                        text = stringResource(
                            Res.string.order_form_deposit_dialog_body_with_other_payments,
                            formatPrice(prompt.nonDepositTotal),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.order_form_deposit_dialog_confirm),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.order_form_deposit_dialog_cancel))
            }
        },
    )
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

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderTotalSummaryWithDiscountPreview() {
    StitchPadTheme {
        OrderTotalSummary(
            items = listOf(
                OrderItemFormState(
                    garmentType = GarmentType.AGBADA,
                    price = "18000",
                    quantity = "6",
                ),
            ),
            discount = "20000",
        )
    }
}

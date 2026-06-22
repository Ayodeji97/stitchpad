package com.danzucker.stitchpad.feature.customer.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.customer.presentation.detail.components.AddMeasurementSheet
import com.danzucker.stitchpad.feature.measurement.presentation.formatMeasurementValue
import com.danzucker.stitchpad.feature.measurement.presentation.measurementDisplayName
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_customer_overflow
import stitchpad.composeapp.generated.resources.cd_edit_customer
import stitchpad.composeapp.generated.resources.cd_measurement_overflow
import stitchpad.composeapp.generated.resources.customer_delete_blocked_dismiss
import stitchpad.composeapp.generated.resources.customer_delete_blocked_message
import stitchpad.composeapp.generated.resources.customer_delete_blocked_title
import stitchpad.composeapp.generated.resources.customer_delete_cancel
import stitchpad.composeapp.generated.resources.customer_delete_confirm
import stitchpad.composeapp.generated.resources.customer_delete_message
import stitchpad.composeapp.generated.resources.customer_delete_title
import stitchpad.composeapp.generated.resources.customer_detail_call_chip
import stitchpad.composeapp.generated.resources.customer_detail_delete_menu_item
import stitchpad.composeapp.generated.resources.customer_detail_measurements_section
import stitchpad.composeapp.generated.resources.customer_detail_message_chip
import stitchpad.composeapp.generated.resources.customer_detail_no_measurements
import stitchpad.composeapp.generated.resources.customer_locked_detail_banner_body
import stitchpad.composeapp.generated.resources.customer_locked_detail_banner_title
import stitchpad.composeapp.generated.resources.customer_locked_detail_unlock_cta
import stitchpad.composeapp.generated.resources.dialer_launch_failed
import stitchpad.composeapp.generated.resources.fab_add_measurement
import stitchpad.composeapp.generated.resources.measurement_delete_message
import stitchpad.composeapp.generated.resources.measurement_delete_title
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
import stitchpad.composeapp.generated.resources.style_closet_name_format
import stitchpad.composeapp.generated.resources.style_gallery_title
import stitchpad.composeapp.generated.resources.style_section_header
import stitchpad.composeapp.generated.resources.whatsapp_launch_failed

// Inert surfaces on a locked customer's detail page render at this alpha so the
// visual hierarchy matches the affordance. 0.5f tested as the sweet spot — high
// enough that the measurement values stay readable, low enough that the surfaces
// clearly look "off". Only the "Unlock with Pro" CTA renders at full opacity.
private const val LOCKED_CONTENT_ALPHA = 0.5f

@Composable
fun CustomerDetailRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEditCustomer: (String) -> Unit,
    onNavigateToAddMeasurement: (String) -> Unit,
    onNavigateToEditMeasurement: (String, String) -> Unit,
    onNavigateToStyleGallery: (String) -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: CustomerDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
    val dialerLauncher: DialerLauncher = koinInject()
    val whatsAppFailed = stringResource(Res.string.whatsapp_launch_failed)
    val dialerFailed = stringResource(Res.string.dialer_launch_failed)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerDetailEvent.NavigateBack -> onNavigateBack()
            is CustomerDetailEvent.NavigateToEditCustomer -> onNavigateToEditCustomer(event.customerId)
            is CustomerDetailEvent.NavigateToAddMeasurement -> onNavigateToAddMeasurement(event.customerId)
            is CustomerDetailEvent.NavigateToEditMeasurement -> onNavigateToEditMeasurement(
                event.customerId,
                event.measurementId,
            )
            is CustomerDetailEvent.NavigateToStyleGallery -> onNavigateToStyleGallery(event.customerId)
            CustomerDetailEvent.NavigateToUpgrade -> onNavigateToUpgrade()
            is CustomerDetailEvent.LaunchWhatsApp -> scope.launch {
                if (!whatsAppLauncher.launch(event.phone, event.message)) {
                    snackbarHostState.showSnackbar(whatsAppFailed)
                }
            }
            is CustomerDetailEvent.LaunchDialer -> scope.launch {
                if (!dialerLauncher.launch(event.phone)) {
                    snackbarHostState.showSnackbar(dialerFailed)
                }
            }
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(CustomerDetailAction.OnErrorDismiss)
        }
    }

    CustomerDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

// Inherently stateful screen (loading / locked / unlocked + measurement-delete
// and customer-delete dialogs + header contact actions). Actions, dialog bodies,
// and sub-sections are already extracted; the remaining branching is the screen's
// own state matrix.
@Suppress("CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    state: CustomerDetailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (CustomerDetailAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.customer?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(CustomerDetailAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Edit, FAB, and per-measurement swipe-to-delete are gated when the customer
                    // is in the LOCKED slot state (per V1.0 design spec decision #2 — locked
                    // surfaces are read-only). Upgrade unlocks; the Upgrade CTA inside the body
                    // is the user's path forward.
                    if (!state.isLocked) {
                        CustomerDetailTopBarActions(state = state, onAction = onAction)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (!state.isLocked) {
                StitchPadFab(
                    onClick = { onAction(CustomerDetailAction.OnAddMeasurementClick) },
                    contentDescription = stringResource(Res.string.fab_add_measurement),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (state.isLocked) {
                        item(key = "locked_banner") {
                            LockedDetailBanner(
                                modifier = Modifier.padding(
                                    horizontal = DesignTokens.space4,
                                    vertical = DesignTokens.space3,
                                ),
                            )
                        }
                    }
                    item {
                        CustomerHeaderSection(
                            customer = state.customer,
                            // Locked customers are read-only; contact chips only for active.
                            onMessageWhatsApp = if (state.isLocked) {
                                null
                            } else {
                                { onAction(CustomerDetailAction.OnMessageWhatsAppClick) }
                            },
                            onCall = if (state.isLocked) {
                                null
                            } else {
                                { onAction(CustomerDetailAction.OnCallClick) }
                            },
                        )
                    }
                    item {
                        MeasurementsSectionHeader()
                    }
                    if (state.measurements.isEmpty()) {
                        item {
                            // Locked customers are read-only (FAB hidden too), so the empty
                            // state is inert there. Otherwise tapping it is a shortcut to the
                            // same action as the FAB — testers reached for the icon first.
                            MeasurementsEmptyState(
                                onClick = if (state.isLocked) {
                                    null
                                } else {
                                    { onAction(CustomerDetailAction.OnAddMeasurementClick) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.space8)
                            )
                        }
                    } else {
                        itemsIndexed(items = state.measurements, key = { _, m -> m.id }) { index, measurement ->
                            val position = index + 1
                            if (state.isLocked) {
                                // Locked customers: row is fully inert (no tap, no swipe) and
                                // visually muted so the affordance matches the behavior. Only
                                // the "Unlock with Pro" CTA below stays interactive.
                                ReadOnlyMeasurementItem(
                                    measurement = measurement,
                                    customFieldLabels = state.customFieldLabels,
                                    position = position,
                                )
                            } else {
                                SwipeableMeasurementItem(
                                    measurement = measurement,
                                    customFieldLabels = state.customFieldLabels,
                                    position = position,
                                    onClick = { onAction(CustomerDetailAction.OnMeasurementClick(measurement)) },
                                    onDelete = { onAction(CustomerDetailAction.OnDeleteMeasurementClick(measurement)) },
                                    onRename = { onAction(CustomerDetailAction.OnRenameMeasurementClick(measurement)) },
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = DesignTokens.space4)
                            )
                        }
                    }
                    item {
                        val stylesClick: (() -> Unit)? = if (state.isLocked) {
                            null
                        } else {
                            { onAction(CustomerDetailAction.OnViewStylesClick) }
                        }
                        StylesSectionRow(
                            customerFirstName = state.customer?.name
                                ?.trim()
                                ?.substringBefore(' ')
                                ?.takeIf { it.isNotBlank() },
                            onClick = stylesClick,
                        )
                    }
                    if (state.isLocked) {
                        item(key = "locked_upgrade_cta") {
                            Spacer(Modifier.height(DesignTokens.space4))
                            Button(
                                onClick = { onAction(CustomerDetailAction.OnUpgradeClick) },
                                shape = RoundedCornerShape(DesignTokens.radiusMd),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DesignTokens.space4),
                            ) {
                                Text(
                                    text = stringResource(Res.string.customer_locked_detail_unlock_cta),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(DesignTokens.space4))
                        }
                    }
                }
            }
        }
    }

    if (state.showAddMeasurementSheet) {
        AddMeasurementSheet(
            measurements = state.measurements,
            onEditMeasurement = { onAction(CustomerDetailAction.OnMeasurementClick(it)) },
            onCreateNew = { onAction(CustomerDetailAction.OnCreateNewMeasurementClick) },
            onDismiss = { onAction(CustomerDetailAction.OnDismissAddMeasurementSheet) },
        )
    }

    if (state.showDeleteDialog && state.measurementToDelete != null) {
        AlertDialog(
            onDismissRequest = { onAction(CustomerDetailAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.measurement_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.measurement_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(CustomerDetailAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.customer_delete_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CustomerDetailAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.customer_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (state.measurementToRename != null) {
        AlertDialog(
            onDismissRequest = { onAction(CustomerDetailAction.OnDismissRenameDialog) },
            title = { Text(stringResource(Res.string.measurement_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = { onAction(CustomerDetailAction.OnRenameDraftChange(it)) },
                    label = { Text(stringResource(Res.string.measurement_name_label)) },
                    placeholder = { Text(stringResource(Res.string.measurement_name_placeholder)) },
                    singleLine = true,
                    isError = state.renameDraft.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(CustomerDetailAction.OnConfirmRename) },
                    enabled = state.renameDraft.isNotBlank(),
                ) { Text(stringResource(Res.string.measurement_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CustomerDetailAction.OnDismissRenameDialog) }) {
                    Text(stringResource(Res.string.measurement_rename_cancel))
                }
            },
        )
    }

    // PTSP-31: delete-customer dialog, two states mirroring the customer list.
    if (state.showDeleteCustomerDialog && state.customer != null) {
        DeleteCustomerDialog(
            customerName = state.customer.name,
            activeOrderCount = state.customerDeleteActiveOrderCount,
            onConfirm = { onAction(CustomerDetailAction.OnConfirmDeleteCustomer) },
            onDismiss = { onAction(CustomerDetailAction.OnDismissDeleteCustomerDialog) },
        )
    }
}

// Top-bar Edit + overflow (Delete). Extracted from CustomerDetailScreen so the
// screen composable stays within detekt's complexity budget.
@Composable
private fun CustomerDetailTopBarActions(
    state: CustomerDetailState,
    onAction: (CustomerDetailAction) -> Unit,
) {
    IconButton(onClick = { onAction(CustomerDetailAction.OnEditCustomerClick) }) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(Res.string.cd_edit_customer),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    // PTSP-31: overflow menu hosts the rare, destructive Delete action.
    Box {
        IconButton(onClick = { onAction(CustomerDetailAction.OnOverflowClick) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(
                    Res.string.cd_customer_overflow,
                    state.customer?.name ?: "",
                ),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = state.showOverflowMenu,
            onDismissRequest = { onAction(CustomerDetailAction.OnDismissOverflow) },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Res.string.customer_detail_delete_menu_item),
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
                onClick = { onAction(CustomerDetailAction.OnDeleteCustomerClick) },
            )
        }
    }
}

// PTSP-31: blocked variant when the customer still has non-delivered orders,
// otherwise the destructive confirm. Mirrors the customer-list delete dialog.
@Composable
private fun DeleteCustomerDialog(
    customerName: String,
    activeOrderCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (activeOrderCount > 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(Res.string.customer_delete_blocked_title, customerName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        Res.string.customer_delete_blocked_message,
                        customerName,
                        activeOrderCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ) {
                    Text(
                        text = stringResource(Res.string.customer_delete_blocked_dismiss),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(Res.string.customer_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.customer_delete_message, customerName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
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
                TextButton(onClick = onDismiss) {
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
}

@Composable
private fun CustomerHeaderSection(
    customer: Customer?,
    onMessageWhatsApp: (() -> Unit)? = null,
    onCall: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.space6, horizontal = DesignTokens.space4)
    ) {
        CustomerAvatar(name = customer?.name ?: "", size = 72.dp)
        Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = customer?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (customer?.phone?.isNotBlank() == true) {
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = customer.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // PTSP-33: visible contact actions. Only rendered when callbacks are
            // supplied (active, non-locked customer with a usable number).
            if (onMessageWhatsApp != null && onCall != null) {
                Spacer(Modifier.height(DesignTokens.space4))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    QuickActionChip(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = stringResource(Res.string.customer_detail_message_chip),
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = onMessageWhatsApp,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionChip(
                        icon = Icons.Default.Call,
                        label = stringResource(Res.string.customer_detail_call_chip),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = onCall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DesignTokens.space3),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(DesignTokens.space2))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun MeasurementsSectionHeader() {
    Text(
        text = stringResource(Res.string.customer_detail_measurements_section).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            top = DesignTokens.space2,
            bottom = DesignTokens.space2
        )
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MeasurementsEmptyState(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val addLabel = stringResource(Res.string.fab_add_measurement)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(
                    onClickLabel = addLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(DesignTokens.radiusXl)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = stringResource(Res.string.customer_detail_no_measurements),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SwipeableMeasurementItem(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    position: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MeasurementListItem(
            measurement = measurement,
            customFieldLabels = customFieldLabels,
            position = position,
            onClick = onClick,
            onDelete = onDelete,
            onRename = onRename,
        )
    }
}

@Composable
private fun ReadOnlyMeasurementItem(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    position: Int,
) {
    // Used when the customer is locked: no swipe-to-delete wrapper AND no click handler.
    // Per V1.0 design spec decision #2, locked customers are fully visible read-only —
    // every surface on the detail page is inert except the "Unlock with Pro" CTA.
    // Muted to 50% alpha so the visual affordance matches the (lack of) behavior.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(LOCKED_CONTENT_ALPHA),
    ) {
        MeasurementListItem(
            measurement = measurement,
            customFieldLabels = customFieldLabels,
            position = position,
            onClick = null,
        )
    }
}

@Composable
private fun LockedDetailBanner(modifier: Modifier = Modifier) {
    // Calm, informative banner (not error-red) — the data is preserved and accessible,
    // only write actions are gated. Per V1.0 design spec: "locked = workflow gate, not a wall".
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space3),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.customer_locked_detail_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.customer_locked_detail_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StylesSectionRow(
    customerFirstName: String?,
    onClick: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(top = DesignTokens.space6)) {
        // Generic eyebrow label; the personalised "{Name}'s Closet" lives on the
        // prominent row below where it's most visible (tester feedback).
        Text(
            text = stringResource(Res.string.style_section_header),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space2,
                bottom = DesignTokens.space2
            )
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val interactionModifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier.alpha(LOCKED_CONTENT_ALPHA)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier
                .fillMaxWidth()
                .then(interactionModifier)
                .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space4)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusMd)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (customerFirstName != null) {
                        stringResource(Res.string.style_closet_name_format, customerFirstName)
                    } else {
                        stringResource(Res.string.style_gallery_title)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MeasurementListItem(
    measurement: Measurement,
    customFieldLabels: Map<String, String>,
    position: Int,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
) {
    val title = measurementDisplayName(measurement, position)
    val genderWord = stringResource(
        if (measurement.gender == CustomerGender.FEMALE) {
            Res.string.measurement_gender_women
        } else {
            Res.string.measurement_gender_men
        }
    )
    val unitLabel = if (measurement.unit == MeasurementUnit.INCHES) {
        stringResource(Res.string.measurement_unit_inches)
    } else {
        stringResource(Res.string.measurement_unit_cm)
    }
    val dateText = remember(measurement.dateTaken) {
        epochToDateString(measurement.dateTaken)
    }
    val subtitleParts = listOfNotNull(genderWord, dateText.ifBlank { null }, unitLabel)

    val tapModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .then(tapModifier)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (measurement.fields.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                val previewKeys = if (measurement.gender == CustomerGender.FEMALE) {
                    listOf("bust_circumference" to "Bust", "waist" to "Waist", "hip_circumference" to "Hip")
                } else {
                    listOf("chest" to "Chest", "trouser_waist" to "Waist")
                }
                val templatePreview = previewKeys
                    .mapNotNull { (key, label) ->
                        measurement.fields[key]?.let { "$label: ${formatMeasurementValue(it)}" }
                    }
                    .joinToString("  ")

                // PTSP-12: also surface any custom-field values recorded on
                // this measurement (UUID keys → resolved labels via the
                // tailor's custom-field definitions). Includes archived
                // definitions so past values keep rendering after archive.
                val templateKeySet = previewKeys.map { it.first }.toSet()
                val customPreview = measurement.fields
                    .filter { (key, _) -> key !in templateKeySet && key in customFieldLabels }
                    .map { (key, value) -> "${customFieldLabels[key]}: ${formatMeasurementValue(value)}" }
                    .joinToString("  ")

                val combined = listOf(templatePreview, customPreview)
                    .filter { it.isNotBlank() }
                    .joinToString("  ")
                if (combined.isNotBlank()) {
                    Text(
                        text = combined,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (onDelete != null || onRename != null) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.cd_measurement_overflow),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.measurement_menu_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.measurement_menu_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun epochToDateString(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val totalSeconds = epochMs / 1000
    val totalMinutes = totalSeconds / 60
    val totalHours = totalMinutes / 60
    val totalDays = totalHours / 24
    var z = totalDays + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = y + (if (m <= 2) 1L else 0L)
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthName = monthNames.getOrElse((m - 1).toInt()) { "" }
    return "$d $monthName $year"
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerDetailScreenLoadingPreview() {
    StitchPadTheme {
        CustomerDetailScreen(state = CustomerDetailState(isLoading = true), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerDetailScreenEmptyPreview() {
    StitchPadTheme {
        CustomerDetailScreen(
            state = CustomerDetailState(
                isLoading = false,
                customer = Customer(
                    id = "1",
                    userId = "u1",
                    name = "Amina Bello",
                    phone = "+234 801 234 5678"
                ),
                measurements = emptyList()
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerDetailScreenFilledPreview() {
    StitchPadTheme {
        CustomerDetailScreen(
            state = CustomerDetailState(
                isLoading = false,
                customer = Customer(
                    id = "1",
                    userId = "u1",
                    name = "Amina Bello",
                    phone = "+234 801 234 5678"
                ),
                measurements = listOf(
                    Measurement(
                        id = "m1",
                        customerId = "1",
                        gender = CustomerGender.FEMALE,
                        fields = mapOf(
                            "bust_circumference" to 36.0,
                            "waist" to 28.0,
                            "hip_circumference" to 38.0
                        ),
                        unit = MeasurementUnit.INCHES,
                        notes = null,
                        dateTaken = 1700000000000L,
                        createdAt = 1700000000000L
                    ),
                    Measurement(
                        id = "m2",
                        customerId = "1",
                        gender = CustomerGender.MALE,
                        fields = mapOf(
                            "chest" to 40.0,
                            "trouser_waist" to 32.0
                        ),
                        unit = MeasurementUnit.INCHES,
                        notes = null,
                        dateTaken = 1700000000000L,
                        createdAt = 1700000000000L
                    )
                )
            ),
            onAction = {}
        )
    }
}

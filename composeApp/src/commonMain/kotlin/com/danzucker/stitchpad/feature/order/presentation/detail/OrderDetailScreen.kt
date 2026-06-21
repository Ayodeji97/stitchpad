@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.media.rememberImageCaptureLauncher
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.ReceiptDocumentType
import com.danzucker.stitchpad.core.sharing.ReceiptFormatter
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.order.presentation.detail.components.MeasurementDetailSheet
import com.danzucker.stitchpad.feature.order.presentation.detail.components.MeasurementPickerSheet
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderArchiveButton
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderCustomerCard
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderDetailOverflowMenu
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderFooterCaption
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderGarmentDetailsCard
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderHeroCard
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderMeasurementsPreviewCard
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderNotesCard
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderPaymentCard
import com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderProductionTimeline
import com.danzucker.stitchpad.feature.order.presentation.detail.components.RecordPaymentDialogV2
import com.danzucker.stitchpad.feature.order.presentation.detail.components.ReferenceImage
import com.danzucker.stitchpad.feature.order.presentation.detail.components.StatusTransitionSheet
import com.danzucker.stitchpad.feature.order.presentation.detail.components.StylePickerSheet
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.feature.style.presentation.form.styleFormSelectionMode
import com.danzucker.stitchpad.ui.components.CustomDatePickerDialog
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.balance_warning_delivered_message
import stitchpad.composeapp.generated.resources.balance_warning_delivered_title
import stitchpad.composeapp.generated.resources.balance_warning_dismiss
import stitchpad.composeapp.generated.resources.balance_warning_proceed
import stitchpad.composeapp.generated.resources.balance_warning_ready_message
import stitchpad.composeapp.generated.resources.balance_warning_ready_title
import stitchpad.composeapp.generated.resources.balance_warning_record_payment
import stitchpad.composeapp.generated.resources.cd_edit_order
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.order_archived_snackbar
import stitchpad.composeapp.generated.resources.order_delete_active_intro
import stitchpad.composeapp.generated.resources.order_delete_cancel
import stitchpad.composeapp.generated.resources.order_delete_confirm
import stitchpad.composeapp.generated.resources.order_delete_consequences
import stitchpad.composeapp.generated.resources.order_delete_message
import stitchpad.composeapp.generated.resources.order_delete_payment_warning
import stitchpad.composeapp.generated.resources.order_delete_title
import stitchpad.composeapp.generated.resources.order_detail_archive_confirm_body
import stitchpad.composeapp.generated.resources.order_detail_archive_confirm_cta
import stitchpad.composeapp.generated.resources.order_detail_archive_confirm_title
import stitchpad.composeapp.generated.resources.order_detail_back_button
import stitchpad.composeapp.generated.resources.order_detail_delivered_label
import stitchpad.composeapp.generated.resources.order_detail_dialer_launch_failed
import stitchpad.composeapp.generated.resources.order_detail_due_label
import stitchpad.composeapp.generated.resources.order_detail_fabric_name_dialog_label
import stitchpad.composeapp.generated.resources.order_detail_fabric_name_dialog_placeholder
import stitchpad.composeapp.generated.resources.order_detail_fabric_name_dialog_title
import stitchpad.composeapp.generated.resources.order_detail_fabric_name_save
import stitchpad.composeapp.generated.resources.order_detail_more
import stitchpad.composeapp.generated.resources.order_detail_not_found_message
import stitchpad.composeapp.generated.resources.order_detail_not_found_title
import stitchpad.composeapp.generated.resources.order_detail_notes_saved_toast
import stitchpad.composeapp.generated.resources.order_detail_pickup_today
import stitchpad.composeapp.generated.resources.order_detail_share
import stitchpad.composeapp.generated.resources.order_detail_title
import stitchpad.composeapp.generated.resources.order_detail_was_due_label
import stitchpad.composeapp.generated.resources.order_detail_whatsapp_launch_failed
import stitchpad.composeapp.generated.resources.order_form_fabric_sheet_title
import stitchpad.composeapp.generated.resources.order_form_photo_pick
import stitchpad.composeapp.generated.resources.order_form_photo_pick_support
import stitchpad.composeapp.generated.resources.order_form_photo_take
import stitchpad.composeapp.generated.resources.order_form_photo_take_support
import stitchpad.composeapp.generated.resources.order_form_style_pick_from_saved
import stitchpad.composeapp.generated.resources.order_form_style_pick_from_saved_support
import stitchpad.composeapp.generated.resources.order_form_style_sheet_title
import stitchpad.composeapp.generated.resources.order_record_payment_snackbar_share_action
import stitchpad.composeapp.generated.resources.order_record_payment_snackbar_success
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready
import stitchpad.composeapp.generated.resources.share_as_image_description
import stitchpad.composeapp.generated.resources.share_as_image_title
import stitchpad.composeapp.generated.resources.share_as_pdf_description
import stitchpad.composeapp.generated.resources.share_as_pdf_title
import stitchpad.composeapp.generated.resources.share_doc_type_deposit_receipt
import stitchpad.composeapp.generated.resources.share_doc_type_invoice
import stitchpad.composeapp.generated.resources.share_doc_type_picker_label
import stitchpad.composeapp.generated.resources.share_doc_type_receipt
import stitchpad.composeapp.generated.resources.share_sheet_subtitle_deposit_receipt
import stitchpad.composeapp.generated.resources.share_sheet_subtitle_invoice
import stitchpad.composeapp.generated.resources.share_sheet_subtitle_receipt
import stitchpad.composeapp.generated.resources.share_sheet_title_deposit_receipt
import stitchpad.composeapp.generated.resources.share_sheet_title_invoice
import stitchpad.composeapp.generated.resources.share_sheet_title_receipt
import stitchpad.composeapp.generated.resources.share_summary_balance_due
import stitchpad.composeapp.generated.resources.share_summary_paid
import stitchpad.composeapp.generated.resources.share_summary_total
import kotlin.time.Clock

private const val MILLIS_PER_DAY: Long = 86_400_000L

private enum class DetailPhotoSource { Camera, Gallery }

@Suppress("CyclomaticComplexMethod")
@Composable
fun OrderDetailRoot(
    onNavigateToOrderForm: (String) -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateToCustomerForm: (customerId: String) -> Unit,
    onNavigateToMeasurementForm: (customerId: String, linkToOrderId: String) -> Unit,
    onNavigateToViewMeasurement: (customerId: String, measurementId: String) -> Unit,
    onNavigateToDuplicateOrder: (sourceOrderId: String) -> Unit,
    onNavigateToStyleForm: (customerId: String, linkToOrderId: String, linkToItemId: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val viewModel: OrderDetailViewModel = koinViewModel()
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
    val dialerLauncher: DialerLauncher = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val measurementsListState = rememberLazyListState()

    val paymentRecordedMessage = stringResource(Res.string.order_record_payment_snackbar_success)
    val paymentRecordedShareAction = stringResource(Res.string.order_record_payment_snackbar_share_action)
    val orderArchivedMessage = stringResource(Res.string.order_archived_snackbar)
    val notesSavedMessage = stringResource(Res.string.order_detail_notes_saved_toast)
    val whatsAppFailedMessage = stringResource(Res.string.order_detail_whatsapp_launch_failed)
    val dialerFailedMessage = stringResource(Res.string.order_detail_dialer_launch_failed)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is OrderDetailEvent.NavigateToOrderForm -> onNavigateToOrderForm(event.orderId)
            is OrderDetailEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            is OrderDetailEvent.NavigateToCustomerForm -> onNavigateToCustomerForm(event.customerId)
            OrderDetailEvent.NavigateBack -> onNavigateBack()
            OrderDetailEvent.OrderDeleted -> onNavigateBack()
            OrderDetailEvent.OrderArchived -> {
                snackbarScope.launch { snackbarHostState.showSnackbar(orderArchivedMessage) }
                onNavigateBack()
            }
            OrderDetailEvent.PaymentRecorded -> {
                snackbarScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = paymentRecordedMessage,
                        actionLabel = paymentRecordedShareAction,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onAction(OrderDetailAction.OnShareReceiptFromSnackbar)
                    }
                }
            }
            OrderDetailEvent.NotesSaved -> {
                snackbarScope.launch { snackbarHostState.showSnackbar(notesSavedMessage) }
            }
            is OrderDetailEvent.LaunchWhatsApp -> {
                snackbarScope.launch {
                    val launched = whatsAppLauncher.launch(event.phone, event.message)
                    if (!launched) {
                        snackbarHostState.showSnackbar(whatsAppFailedMessage)
                    }
                }
            }
            is OrderDetailEvent.LaunchDialer -> {
                snackbarScope.launch {
                    val launched = dialerLauncher.launch(event.phone)
                    if (!launched) {
                        snackbarHostState.showSnackbar(dialerFailedMessage)
                    }
                }
            }
            is OrderDetailEvent.NavigateToCreateOrder ->
                onNavigateToDuplicateOrder(event.seedFromOrderId)
            is OrderDetailEvent.NavigateToMeasurementForm ->
                onNavigateToMeasurementForm(event.customerId, event.linkToOrderId)
            is OrderDetailEvent.NavigateToViewMeasurement ->
                onNavigateToViewMeasurement(event.customerId, event.measurementId)
            is OrderDetailEvent.NavigateToStyleForm ->
                onNavigateToStyleForm(event.customerId, event.linkToOrderId, event.linkToItemId)
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(OrderDetailAction.OnErrorDismiss)
        }
    }

    OrderDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        listState = measurementsListState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun OrderDetailScreen(
    state: OrderDetailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    onAction: (OrderDetailAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.order_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(OrderDetailAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (state.order != null) {
                        IconButton(onClick = { onAction(OrderDetailAction.OnShareClick) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(Res.string.order_detail_share),
                            )
                        }
                        IconButton(onClick = { onAction(OrderDetailAction.OnEditClick) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.cd_edit_order),
                            )
                        }
                        Box {
                            IconButton(onClick = { onAction(OrderDetailAction.OnOverflowMenuToggle) }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(Res.string.order_detail_more),
                                )
                            }
                            OrderDetailOverflowMenu(
                                expanded = state.showOverflowMenu,
                                showArchive = state.order.status != OrderStatus.DELIVERED,
                                onDismiss = { onAction(OrderDetailAction.OnOverflowMenuToggle) },
                                onDuplicateClick = { onAction(OrderDetailAction.OnDuplicateClick) },
                                onArchiveClick = { onAction(OrderDetailAction.OnArchiveClick) },
                                onDeleteClick = { onAction(OrderDetailAction.OnDeleteClick) },
                            )
                        }
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
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.order != null -> {
                OrderDetailContent(
                    state = state,
                    order = state.order,
                    onAction = onAction,
                    listState = listState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
            }
            else -> {
                OrderDetailNotFound(
                    onBack = { onAction(OrderDetailAction.OnBackClick) },
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
            }
        }
    }

    // Delete dialog — body adapts to whether the order is still in flight and
    // whether any payment has been recorded against it.
    if (state.showDeleteDialog) {
        val order = state.order
        val isActive = order != null && order.status != OrderStatus.DELIVERED
        val hasPayment = order != null && order.depositPaid > 0.0
        AlertDialog(
            onDismissRequest = { onAction(OrderDetailAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.order_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                    if (isActive && order != null) {
                        Text(
                            text = stringResource(
                                Res.string.order_delete_active_intro,
                                statusLabel(order.status),
                                order.customerName
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (hasPayment && order != null) {
                        Text(
                            text = stringResource(
                                Res.string.order_delete_payment_warning,
                                formatPrice(order.depositPaid)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = if (isActive) {
                            stringResource(Res.string.order_delete_consequences)
                        } else {
                            stringResource(Res.string.order_delete_message)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAction(OrderDetailAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.order_delete_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(OrderDetailAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.order_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // V2 status picker — modal bottom sheet with sub-status routing
    if (state.showStatusSheet && state.order != null) {
        StatusTransitionSheet(
            currentStatus = state.order.status,
            currentSubStatus = state.order.subStatus,
            onTransitionSelected = { onAction(OrderDetailAction.OnSelectStatusTransition(it)) },
            onDismiss = { onAction(OrderDetailAction.OnDismissStatusSheet) },
        )
    }

    // Measurement picker sheet — lets user link an existing measurement or create a new one
    if (state.showMeasurementPickerSheet && state.order != null) {
        MeasurementPickerSheet(
            measurements = state.availableMeasurements,
            selectedMeasurementId = state.order.items.firstOrNull()?.measurementId,
            customFieldLabels = state.customFieldLabels,
            onSelectMeasurement = { onAction(OrderDetailAction.OnSelectMeasurement(it)) },
            onCreateNewClick = { onAction(OrderDetailAction.OnCreateNewMeasurementClick) },
            onDismiss = { onAction(OrderDetailAction.OnDismissMeasurementPickerSheet) },
        )
    }

    // Read-only measurement quick-view — opened by tapping the populated card.
    if (state.showMeasurementDetailSheet && state.measurement != null) {
        MeasurementDetailSheet(
            measurement = state.measurement,
            customFieldLabels = state.customFieldLabels,
            onViewFull = { onAction(OrderDetailAction.OnViewFullMeasurementClick) },
            onChangeMeasurement = { onAction(OrderDetailAction.OnLinkMeasurementsClick) },
            onDismiss = { onAction(OrderDetailAction.OnDismissMeasurementDetailSheet) },
        )
    }

    // Style picker sheet — lets user link an existing style or create a new one
    if (state.showStylePickerSheet && state.order != null) {
        val pickerItemId = state.stylePickerItemId
        val pickerItemStyleImages = if (pickerItemId != null) {
            state.order.items.firstOrNull { it.id == pickerItemId }?.styleImages.orEmpty()
        } else {
            state.order.items.firstOrNull()?.styleImages.orEmpty()
        }
        val alreadySelectedStyleIds = pickerItemStyleImages
            .filter { it.source == StyleImageSource.LIBRARY }
            .mapNotNull { it.styleId }
            .toSet()
        val remainingCapacity = 3 - pickerItemStyleImages.size // MAX_IMAGES_PER_CATEGORY = 3
        StylePickerSheet(
            styles = state.availableStyles,
            alreadySelectedStyleIds = alreadySelectedStyleIds,
            remainingCapacity = remainingCapacity,
            onSelectStyle = { styleId ->
                val targetItemId = pickerItemId ?: state.order.items.firstOrNull()?.id ?: return@StylePickerSheet
                onAction(OrderDetailAction.OnSelectStyle(styleId, targetItemId))
            },
            onCreateNewClick = {
                val targetItemId = pickerItemId ?: state.order.items.firstOrNull()?.id ?: return@StylePickerSheet
                onAction(OrderDetailAction.OnCreateNewStyleClick(targetItemId))
            },
            onDismiss = { onAction(OrderDetailAction.OnDismissStylePickerSheet) },
        )
    }

    // Archive confirm dialog — non-destructive but worth confirming
    if (state.showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { onAction(OrderDetailAction.OnDismissArchiveDialog) },
            title = { Text(stringResource(Res.string.order_detail_archive_confirm_title)) },
            text = { Text(stringResource(Res.string.order_detail_archive_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(OrderDetailAction.OnConfirmArchive) }) {
                    Text(stringResource(Res.string.order_detail_archive_confirm_cta))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(OrderDetailAction.OnDismissArchiveDialog) }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    // Balance-owed warning when moving to Ready / Delivered with money still owed
    if (state.showBalanceWarningDialog &&
        state.order != null &&
        state.selectedNewStatus != null
    ) {
        BalanceOwedWarningDialog(
            customerName = state.order.customerName,
            balanceRemaining = state.order.balanceRemaining,
            targetStatus = state.selectedNewStatus,
            onRecordPayment = { onAction(OrderDetailAction.OnBalanceWarningRecordPayment) },
            onProceed = { onAction(OrderDetailAction.OnBalanceWarningProceed) },
            onDismiss = { onAction(OrderDetailAction.OnBalanceWarningDismiss) }
        )
    }

    // Share receipt bottom sheet
    if (state.showShareSheet) {
        // Share sheet uses the formatter's classifier so the chip picker can't
        // drift from what the formatter actually produces — both read from
        // ReceiptFormatter.resolveDocumentType (single source of truth).
        val naturalDocType = state.order?.let { ReceiptFormatter.resolveDocumentType(it) }
        ShareReceiptBottomSheet(
            naturalDocType = naturalDocType,
            chosenDocType = state.documentTypeChoice,
            customerName = state.order?.customerName,
            totalFormatted = state.order?.let { "₦${formatPrice(it.payableTotal)}" },
            balanceFormatted = state.order?.let { "₦${formatPrice(it.balanceRemaining)}" },
            balanceDue = (state.order?.balanceRemaining ?: 0.0) > 0.0,
            onDocTypeChoice = { onAction(OrderDetailAction.OnDocumentTypeChoice(it)) },
            onShareAsImage = { onAction(OrderDetailAction.OnShareAsImageClick) },
            onShareAsPdf = { onAction(OrderDetailAction.OnShareAsPdfClick) },
            onDismiss = { onAction(OrderDetailAction.OnDismissShareSheet) }
        )
    }

    // Record payment dialog
    if (state.showRecordPaymentDialog && state.order != null) {
        RecordPaymentDialogV2(
            balanceRemaining = state.order.balanceRemaining,
            amountInput = state.paymentAmountInput,
            method = state.paymentMethodSelection,
            type = state.paymentTypeSelection,
            wasCapped = state.wasPaymentCapped,
            onAmountChange = { onAction(OrderDetailAction.OnPaymentAmountChange(it)) },
            onMethodSelect = { onAction(OrderDetailAction.OnPaymentMethodSelect(it)) },
            onTypeSelect = { onAction(OrderDetailAction.OnPaymentTypeSelect(it)) },
            onMarkPaidInFull = { onAction(OrderDetailAction.OnMarkPaidInFull) },
            onConfirm = { onAction(OrderDetailAction.OnConfirmRecordPayment) },
            onDismiss = { onAction(OrderDetailAction.OnDismissRecordPayment) },
        )
    }

    // Set-deadline date picker
    if (state.showDatePickerDialog && state.order != null) {
        val timeZone = TimeZone.currentSystemDefault()
        val initialDate = state.order.deadline?.let { millis ->
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
        }
        CustomDatePickerDialog(
            initial = initialDate,
            timeZone = timeZone,
            onDismiss = { onAction(OrderDetailAction.OnDismissDatePickerDialog) },
            onConfirm = { picked ->
                val millis = picked.atStartOfDayIn(timeZone).toEpochMilliseconds()
                onAction(OrderDetailAction.OnDeadlineSelected(millis))
            },
        )
    }

    // Inline fabric-name editor — quick add without leaving the detail screen
    if (state.showFabricNameDialog) {
        FabricNameDialog(
            draft = state.fabricNameDraft,
            onDraftChange = { onAction(OrderDetailAction.OnFabricNameDraftChange(it)) },
            onSave = { onAction(OrderDetailAction.OnSaveFabricName) },
            onDismiss = { onAction(OrderDetailAction.OnDismissFabricNameDialog) },
        )
    }
}

@Composable
private fun FabricNameDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.order_detail_fabric_name_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text(stringResource(Res.string.order_detail_fabric_name_dialog_label)) },
                placeholder = {
                    Text(stringResource(Res.string.order_detail_fabric_name_dialog_placeholder))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = draft.isNotBlank(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ) {
                Text(
                    text = stringResource(Res.string.order_detail_fabric_name_save),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.common_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiptBottomSheet(
    naturalDocType: ReceiptDocumentType?,
    chosenDocType: ReceiptDocumentType?,
    customerName: String?,
    totalFormatted: String?,
    balanceFormatted: String?,
    balanceDue: Boolean,
    onDocTypeChoice: (ReceiptDocumentType) -> Unit,
    onShareAsImage: () -> Unit,
    onShareAsPdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Picker shows only when both Invoice and Deposit Receipt are meaningful —
    // i.e. the order has at least one payment but still has balance remaining.
    // No-payments → Invoice only; fully-paid → Receipt only.
    val showPicker = naturalDocType == ReceiptDocumentType.DEPOSIT_RECEIPT
    val effectiveChoice = chosenDocType ?: ReceiptDocumentType.DEPOSIT_RECEIPT
    // Title + badge track the document that will actually be generated (PTSP-29)
    // — read from the formatter's own resolver so they can never contradict it.
    val effectiveDocType = naturalDocType?.let {
        ReceiptFormatter.effectiveDocumentType(it, chosenDocType)
    }
    val titleRes = when (effectiveDocType) {
        ReceiptDocumentType.INVOICE -> Res.string.share_sheet_title_invoice
        ReceiptDocumentType.DEPOSIT_RECEIPT -> Res.string.share_sheet_title_deposit_receipt
        ReceiptDocumentType.RECEIPT, null -> Res.string.share_sheet_title_receipt
    }
    // Title + badge follow the chosen framing (effective), but the subtitle
    // describes payment *reality*, so it keys off the natural type. Otherwise a
    // part-paid order re-framed as "Invoice" would read "No payment recorded yet"
    // while the summary still shows a balance due (Bugbot, PR #146).
    val subtitleRes = when (naturalDocType) {
        ReceiptDocumentType.INVOICE -> Res.string.share_sheet_subtitle_invoice
        ReceiptDocumentType.DEPOSIT_RECEIPT -> Res.string.share_sheet_subtitle_deposit_receipt
        ReceiptDocumentType.RECEIPT, null -> Res.string.share_sheet_subtitle_receipt
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = DesignTokens.radiusXl, topEnd = DesignTokens.radiusXl)
    ) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                bottom = DesignTokens.space8
            )
        ) {
            // PTSP-29: bold doc-type badge so the tailor can't confuse an invoice
            // with a receipt before sharing.
            if (effectiveDocType != null) {
                ShareDocTypeBadge(docType = effectiveDocType)
                Spacer(Modifier.height(DesignTokens.space2))
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = DesignTokens.space3),
            )

            // Order summary — confirm who + how much before sending.
            if (customerName != null && totalFormatted != null && balanceFormatted != null) {
                ShareOrderSummary(
                    customerName = customerName,
                    totalFormatted = totalFormatted,
                    balanceFormatted = balanceFormatted,
                    balanceDue = balanceDue,
                )
                Spacer(Modifier.height(DesignTokens.space4))
            }

            if (showPicker) {
                Text(
                    text = stringResource(Res.string.share_doc_type_picker_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = DesignTokens.space2),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                    DocTypeChip(
                        label = stringResource(Res.string.share_doc_type_invoice),
                        selected = effectiveChoice == ReceiptDocumentType.INVOICE,
                        onClick = { onDocTypeChoice(ReceiptDocumentType.INVOICE) },
                    )
                    DocTypeChip(
                        label = stringResource(Res.string.share_doc_type_deposit_receipt),
                        selected = effectiveChoice == ReceiptDocumentType.DEPOSIT_RECEIPT,
                        onClick = { onDocTypeChoice(ReceiptDocumentType.DEPOSIT_RECEIPT) },
                    )
                }
                Spacer(Modifier.height(DesignTokens.space4))
            }

            ShareOption(
                icon = "🖼️",
                title = stringResource(Res.string.share_as_image_title),
                description = stringResource(Res.string.share_as_image_description),
                onClick = onShareAsImage
            )

            Spacer(Modifier.height(DesignTokens.space2))

            ShareOption(
                icon = "📄",
                title = stringResource(Res.string.share_as_pdf_title),
                description = stringResource(Res.string.share_as_pdf_description),
                onClick = onShareAsPdf
            )
        }
    }
}

// PTSP-29 Variant A: bold, color-coded doc-type pill. Invoice = sienna warmth
// (payment requested), Deposit receipt = indigo (partial), Receipt = green (paid).
@Composable
private fun ShareDocTypeBadge(docType: ReceiptDocumentType) {
    val dark = isSystemInDarkTheme()
    val (container, content) = when (docType) {
        ReceiptDocumentType.INVOICE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ReceiptDocumentType.DEPOSIT_RECEIPT ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ReceiptDocumentType.RECEIPT ->
            (if (dark) DesignTokens.successDarkBg else DesignTokens.success50) to
                (if (dark) DesignTokens.successDarkText else DesignTokens.success500)
    }
    val labelRes = when (docType) {
        ReceiptDocumentType.INVOICE -> Res.string.share_doc_type_invoice
        ReceiptDocumentType.DEPOSIT_RECEIPT -> Res.string.share_doc_type_deposit_receipt
        ReceiptDocumentType.RECEIPT -> Res.string.share_doc_type_receipt
    }
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = DesignTokens.space3, vertical = 6.dp),
        )
    }
}

@Composable
private fun ShareOrderSummary(
    customerName: String,
    totalFormatted: String,
    balanceFormatted: String,
    balanceDue: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = customerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ShareSummaryRow(
                label = stringResource(Res.string.share_summary_total),
                value = totalFormatted,
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
            if (balanceDue) {
                ShareSummaryRow(
                    label = stringResource(Res.string.share_summary_balance_due),
                    value = balanceFormatted,
                    valueColor = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                ShareSummaryRow(
                    label = stringResource(Res.string.share_summary_paid),
                    value = totalFormatted,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ShareSummaryRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun DocTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = container,
        contentColor = content,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space2,
            ),
        )
    }
}

@Composable
private fun ShareOption(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(DesignTokens.space3)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium,
                // Icon is purely decorative — the adjacent title/description carry the
                // meaning. Clear semantics so TalkBack/VoiceOver don't read the emoji name.
                modifier = Modifier
                    .padding(end = DesignTokens.space3)
                    .clearAndSetSemantics { }
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OrderDetailNotFound(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = DesignTokens.space6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.order_detail_not_found_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.order_detail_not_found_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(DesignTokens.space4))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(DesignTokens.radiusMd)
        ) {
            Text(
                text = stringResource(Res.string.order_detail_back_button),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** First non-null, non-blank candidate, or null. Used to skip empty image sources that would
 *  otherwise render as a stuck blank reference tile. */
private fun firstNonBlank(vararg candidates: String?): String? =
    candidates.firstOrNull { !it.isNullOrBlank() }

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun OrderDetailContent(
    state: OrderDetailState,
    order: Order,
    onAction: (OrderDetailAction) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val deadline = order.deadline
    val isOverdue = deadline != null &&
        deadline < now &&
        order.status != OrderStatus.READY &&
        order.status != OrderStatus.DELIVERED
    val overdueDaysAgo = if (isOverdue) {
        ((now - deadline!!) / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
    } else {
        0
    }
    val cta = remember(order.status, order.subStatus, isOverdue, order.balanceRemaining) {
        resolvePrimaryCta(order.status, order.subStatus, isOverdue, order.balanceRemaining)
    }

    val firstItem = order.items.firstOrNull()
    val garmentName = firstItem?.let { garmentDisplayName(it) }.orEmpty()
    val dueLabel = formatDueLabel(order, isOverdue)
    val styleImagesByItemId: Map<String, List<ReferenceImage>> = order.items.associate { item ->
        item.id to item.styleImages.mapIndexedNotNull { index, ref ->
            // Drop blanks, not just nulls: a PENDING ref can arrive with photoUrl="" and no
            // localPhotoPath, which would otherwise render a stuck blank tile (see fabricReferenceImages).
            val style = state.styles[ref.styleId]
            val url = when (ref.source) {
                StyleImageSource.LIBRARY -> firstNonBlank(style?.localPhotoPath, style?.photoUrl)
                StyleImageSource.UPLOADED -> firstNonBlank(ref.localPhotoPath, ref.photoUrl)
            }
            url?.let { ReferenceImage(url = it, sourceIndex = index) }
        }
    }
    val pickerScope = rememberCoroutineScope()
    var showStylePhotoSheetForItemId by remember { mutableStateOf<String?>(null) }
    var pendingStylePhotoItemId by remember { mutableStateOf<String?>(null) }
    var showFabricPhotoSheetForItemId by remember { mutableStateOf<String?>(null) }
    var pendingFabricPhotoItemId by remember { mutableStateOf<String?>(null) }
    var pendingStylePhotoSource by remember { mutableStateOf<DetailPhotoSource?>(null) }
    var pendingFabricPhotoSource by remember { mutableStateOf<DetailPhotoSource?>(null) }
    val styleDetailUsed =
        state.order?.items?.firstOrNull { it.id == pendingStylePhotoItemId }?.styleImages?.size ?: 0
    val styleDetailRemaining = (3 - styleDetailUsed).coerceAtLeast(1)
    val styleGalleryPicker = key(pendingStylePhotoItemId, styleDetailRemaining) {
        rememberImagePickerLauncher(
            selectionMode = styleFormSelectionMode(allowMultiPhoto = true, maxPhotoSelection = styleDetailRemaining),
            scope = pickerScope,
            onResult = { byteArrays ->
                val itemId = pendingStylePhotoItemId ?: return@rememberImagePickerLauncher
                byteArrays.forEach { onAction(OrderDetailAction.OnAddStylePhoto(itemId, it)) }
                pendingStylePhotoItemId = null
            },
        )
    }
    val styleCameraLauncher = rememberImageCaptureLauncher { bytes ->
        val itemId = pendingStylePhotoItemId ?: return@rememberImageCaptureLauncher
        if (bytes != null) onAction(OrderDetailAction.OnAddStylePhoto(itemId, bytes))
        pendingStylePhotoItemId = null
    }
    val fabricGalleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = pickerScope,
        onResult = { byteArrays ->
            val itemId = pendingFabricPhotoItemId ?: return@rememberImagePickerLauncher
            byteArrays.firstOrNull()?.let { onAction(OrderDetailAction.OnAddFabricPhoto(itemId, it)) }
            pendingFabricPhotoItemId = null
        },
    )
    val fabricCameraLauncher = rememberImageCaptureLauncher { bytes ->
        val itemId = pendingFabricPhotoItemId ?: return@rememberImageCaptureLauncher
        if (bytes != null) onAction(OrderDetailAction.OnAddFabricPhoto(itemId, bytes))
        pendingFabricPhotoItemId = null
    }

    LaunchedEffect(showStylePhotoSheetForItemId, pendingStylePhotoSource) {
        if (showStylePhotoSheetForItemId == null && pendingStylePhotoSource != null) {
            when (pendingStylePhotoSource) {
                DetailPhotoSource.Camera -> styleCameraLauncher.launch()
                DetailPhotoSource.Gallery -> styleGalleryPicker.launch()
                null -> Unit
            }
            pendingStylePhotoSource = null
        }
    }

    LaunchedEffect(showFabricPhotoSheetForItemId, pendingFabricPhotoSource) {
        if (showFabricPhotoSheetForItemId == null && pendingFabricPhotoSource != null) {
            when (pendingFabricPhotoSource) {
                DetailPhotoSource.Camera -> fabricCameraLauncher.launch()
                DetailPhotoSource.Gallery -> fabricGalleryPicker.launch()
                null -> Unit
            }
            pendingFabricPhotoSource = null
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item {
            OrderHeroCard(
                garmentTypeIcon = Icons.Default.Checkroom,
                garmentName = garmentName,
                customerName = order.customerName,
                status = order.status,
                subStatus = order.subStatus,
                priority = order.priority,
                isOverdue = isOverdue,
                overdueDaysAgo = overdueDaysAgo,
                dueLabel = dueLabel,
                totalPrice = order.totalPrice,
                balanceRemaining = order.balanceRemaining,
                discount = order.discount,
                cta = cta,
                onPrimaryCta = { handlePrimaryCta(cta.primary, onAction) },
                onSecondaryCta = { handleSecondaryCta(cta.secondary, onAction) },
                onSetDeadlineClick = { onAction(OrderDetailAction.OnSetDeadlineClick) },
            )
        }
        item {
            OrderGarmentDetailsCard(
                items = order.items,
                priority = order.priority,
                styleImagesByItemId = styleImagesByItemId,
                onAddStyleClick = { itemId -> showStylePhotoSheetForItemId = itemId },
                onRemoveStyleImage = { itemId, index ->
                    onAction(OrderDetailAction.OnRemoveStyleImage(itemId, index))
                },
                onAddFabricPhotoClick = { itemId -> showFabricPhotoSheetForItemId = itemId },
                onRemoveFabricImage = { itemId, index ->
                    onAction(OrderDetailAction.OnRemoveFabricImage(itemId, index))
                },
                onAddFabricNameClick = { onAction(OrderDetailAction.OnAddFabricNameClick) },
            )
        }
        item {
            OrderCustomerCard(
                customerName = order.customerName,
                phone = state.customer?.phone,
                customerCreatedAt = state.customer?.createdAt,
                onWhatsAppClick = { onAction(OrderDetailAction.OnWhatsAppClick) },
                onCallClick = { onAction(OrderDetailAction.OnCallClick) },
                onAddPhoneClick = { onAction(OrderDetailAction.OnAddPhoneClick) },
                onCustomerClick = { onAction(OrderDetailAction.OnCustomerClick) },
            )
        }
        item {
            OrderPaymentCard(
                totalPrice = order.totalPrice,
                discount = order.discount,
                payments = order.payments,
                isExpanded = state.isPaymentHistoryExpanded,
                onToggleExpanded = { onAction(OrderDetailAction.OnPaymentHistoryToggle) },
                onRecordPaymentClick = { onAction(OrderDetailAction.OnRecordPaymentClick) },
            )
        }
        item {
            OrderProductionTimeline(
                currentStatus = order.status,
                currentSubStatus = order.subStatus,
                isOverdue = isOverdue,
                onClick = { onAction(OrderDetailAction.OnUpdateStatusClick) },
            )
        }
        item {
            OrderMeasurementsPreviewCard(
                measurement = state.measurement,
                customFieldLabels = state.customFieldLabels,
                // Linked → open the read-only quick-view; empty → open the picker.
                onCardClick = {
                    if (state.measurement != null) {
                        onAction(OrderDetailAction.OnViewMeasurementClick)
                    } else {
                        onAction(OrderDetailAction.OnLinkMeasurementsClick)
                    }
                },
                onLinkMeasurementsClick = { onAction(OrderDetailAction.OnLinkMeasurementsClick) },
            )
        }
        item {
            OrderNotesCard(
                notes = order.notes,
                isEditing = state.isEditingNotes,
                draft = state.notesDraft,
                onCardClick = { onAction(OrderDetailAction.OnNotesEditClick) },
                onDraftChange = { onAction(OrderDetailAction.OnNotesDraftChange(it)) },
                onSaveClick = { onAction(OrderDetailAction.OnNotesSaveClick) },
                onCancelClick = { onAction(OrderDetailAction.OnNotesCancelClick) },
            )
        }
        if (order.status == OrderStatus.DELIVERED) {
            item {
                OrderArchiveButton(onClick = { onAction(OrderDetailAction.OnArchiveClick) })
            }
        }
        item {
            OrderFooterCaption(
                orderId = order.id,
                referenceTimestamp = if (order.status == OrderStatus.DELIVERED) {
                    order.statusHistory
                        .lastOrNull { it.status == OrderStatus.DELIVERED }?.changedAt
                        ?: order.updatedAt
                } else {
                    order.createdAt
                },
                isDelivered = order.status == OrderStatus.DELIVERED,
            )
        }
    }

    if (showStylePhotoSheetForItemId != null) {
        val styleSheetItemId = showStylePhotoSheetForItemId!!
        ModalBottomSheet(onDismissRequest = { showStylePhotoSheetForItemId = null }) {
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
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.order_form_style_pick_from_saved)) },
                    supportingContent = {
                        Text(stringResource(Res.string.order_form_style_pick_from_saved_support))
                    },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showStylePhotoSheetForItemId = null
                        onAction(OrderDetailAction.OnAddStyleClick(styleSheetItemId))
                    },
                )
                PhotoSourceItems(
                    onCameraClick = {
                        pendingStylePhotoItemId = styleSheetItemId
                        pendingStylePhotoSource = DetailPhotoSource.Camera
                        showStylePhotoSheetForItemId = null
                    },
                    onGalleryClick = {
                        pendingStylePhotoItemId = styleSheetItemId
                        pendingStylePhotoSource = DetailPhotoSource.Gallery
                        showStylePhotoSheetForItemId = null
                    },
                )
            }
        }
    }

    if (showFabricPhotoSheetForItemId != null) {
        ModalBottomSheet(onDismissRequest = { showFabricPhotoSheetForItemId = null }) {
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
                PhotoSourceItems(
                    onCameraClick = {
                        pendingFabricPhotoItemId = showFabricPhotoSheetForItemId
                        pendingFabricPhotoSource = DetailPhotoSource.Camera
                        showFabricPhotoSheetForItemId = null
                    },
                    onGalleryClick = {
                        pendingFabricPhotoItemId = showFabricPhotoSheetForItemId
                        pendingFabricPhotoSource = DetailPhotoSource.Gallery
                        showFabricPhotoSheetForItemId = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PhotoSourceItems(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.order_form_photo_take)) },
        supportingContent = { Text(stringResource(Res.string.order_form_photo_take_support)) },
        leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onCameraClick),
    )
    ListItem(
        headlineContent = { Text(stringResource(Res.string.order_form_photo_pick)) },
        supportingContent = { Text(stringResource(Res.string.order_form_photo_pick_support)) },
        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onGalleryClick),
    )
}

private fun handlePrimaryCta(cta: PrimaryCta, onAction: (OrderDetailAction) -> Unit) {
    when (cta) {
        PrimaryCta.StartWork,
        PrimaryCta.UpdateStatus,
        PrimaryCta.ConfirmFitting,
        PrimaryCta.MarkDelivered -> onAction(OrderDetailAction.OnUpdateStatusClick)
        PrimaryCta.ShareReceipt -> onAction(OrderDetailAction.OnShareClick)
        PrimaryCta.SendReminder -> onAction(OrderDetailAction.OnSendReminderClick)
    }
}

private fun handleSecondaryCta(cta: SecondaryCta?, onAction: (OrderDetailAction) -> Unit) {
    when (cta) {
        null -> Unit
        SecondaryCta.RecordPayment -> onAction(OrderDetailAction.OnRecordPaymentClick)
        SecondaryCta.StartWork,
        SecondaryCta.UpdateStatus,
        SecondaryCta.MarkDelivered -> onAction(OrderDetailAction.OnUpdateStatusClick)
        SecondaryCta.ShareReceipt -> onAction(OrderDetailAction.OnShareClick)
    }
}

@Composable
private fun formatDueLabel(order: Order, isOverdue: Boolean): UiText? {
    val deadlineDate = order.deadline?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val readableDeadline = deadlineDate?.let {
        val month = it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
        "${it.dayOfMonth} $month"
    }
    // Ready always shows "Pickup today" regardless of deadline; other states need
    // an actual deadline to render — without one we return null so the hero hides
    // the date row entirely instead of showing a bare "Due " label.
    return when {
        order.status == OrderStatus.READY ->
            UiText.StringResourceText(Res.string.order_detail_pickup_today)
        readableDeadline == null -> null
        order.status == OrderStatus.DELIVERED -> UiText.StringResourceText(
            Res.string.order_detail_delivered_label,
            arrayOf(readableDeadline),
        )
        isOverdue -> UiText.StringResourceText(
            Res.string.order_detail_was_due_label,
            arrayOf(readableDeadline),
        )
        else -> UiText.StringResourceText(
            Res.string.order_detail_due_label,
            arrayOf(readableDeadline),
        )
    }
}

@Composable
private fun statusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.PENDING -> stringResource(Res.string.order_status_pending)
    OrderStatus.IN_PROGRESS -> stringResource(Res.string.order_status_in_progress)
    OrderStatus.READY -> stringResource(Res.string.order_status_ready)
    OrderStatus.DELIVERED -> stringResource(Res.string.order_status_delivered)
}

@Composable
private fun BalanceOwedWarningDialog(
    customerName: String,
    balanceRemaining: Double,
    targetStatus: OrderStatus,
    onRecordPayment: () -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    val titleRes = if (targetStatus == OrderStatus.DELIVERED) {
        Res.string.balance_warning_delivered_title
    } else {
        Res.string.balance_warning_ready_title
    }
    val messageRes = if (targetStatus == OrderStatus.DELIVERED) {
        Res.string.balance_warning_delivered_message
    } else {
        Res.string.balance_warning_ready_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(messageRes, customerName, formatPrice(balanceRemaining)),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onRecordPayment,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(DesignTokens.radiusMd)
            ) {
                Text(
                    text = stringResource(Res.string.balance_warning_record_payment),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(Res.string.balance_warning_dismiss),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onProceed) {
                    Text(
                        text = stringResource(Res.string.balance_warning_proceed),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderDetailScreenFilledPreview() {
    val now = 1_745_672_400_000L
    StitchPadTheme {
        OrderDetailScreen(
            state = OrderDetailState(
                isLoading = false,
                user = User(
                    id = "u1",
                    email = "ade@example.com",
                    displayName = "Ade",
                    businessName = "Ade's Fashions",
                    phoneNumber = "+2348012345678",
                    whatsappNumber = "+2348012345678",
                    avatarColorIndex = 0
                ),
                order = Order(
                    id = "o1",
                    userId = "u1",
                    customerId = "c1",
                    customerName = "Bimbo Dann",
                    items = listOf(
                        OrderItem(
                            id = "i1",
                            garmentType = GarmentType.AGBADA,
                            description = "Royal blue lace, gold embroidery",
                            price = 100_000.0
                        )
                    ),
                    status = OrderStatus.IN_PROGRESS,
                    priority = OrderPriority.NORMAL,
                    statusHistory = listOf(StatusChange(OrderStatus.PENDING, now - 86_400_000)),
                    totalPrice = 100_000.0,
                    payments = listOf(
                        Payment(
                            id = "p1",
                            amount = 40_000.0,
                            method = PaymentMethod.OTHER,
                            type = PaymentType.DEPOSIT,
                            recordedAt = 0L,
                        )
                    ),
                    deadline = now + 86_400_000 * 3,
                    notes = "Needs to be ready by Friday for the wedding.",
                    createdAt = now - 86_400_000,
                    updatedAt = now
                )
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderDetailScreenLoadingPreview() {
    StitchPadTheme {
        OrderDetailScreen(
            state = OrderDetailState(isLoading = true),
            onAction = {}
        )
    }
}

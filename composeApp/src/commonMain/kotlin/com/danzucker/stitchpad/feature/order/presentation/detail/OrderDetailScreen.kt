package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.formatPrice
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
import com.danzucker.stitchpad.feature.order.presentation.detail.components.StatusTransitionSheet
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
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
import stitchpad.composeapp.generated.resources.order_detail_due_label
import stitchpad.composeapp.generated.resources.order_detail_more
import stitchpad.composeapp.generated.resources.order_detail_not_found_message
import stitchpad.composeapp.generated.resources.order_detail_not_found_title
import stitchpad.composeapp.generated.resources.order_detail_notes_saved_toast
import stitchpad.composeapp.generated.resources.order_detail_pickup_today
import stitchpad.composeapp.generated.resources.order_detail_share
import stitchpad.composeapp.generated.resources.order_detail_title
import stitchpad.composeapp.generated.resources.order_detail_was_due_label
import stitchpad.composeapp.generated.resources.order_record_payment_snackbar_success
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready
import stitchpad.composeapp.generated.resources.share_as_image_description
import stitchpad.composeapp.generated.resources.share_as_image_title
import stitchpad.composeapp.generated.resources.share_as_pdf_description
import stitchpad.composeapp.generated.resources.share_as_pdf_title
import stitchpad.composeapp.generated.resources.share_receipt_title
import kotlin.time.Clock

private const val MILLIS_PER_DAY: Long = 86_400_000L
private const val MEASUREMENTS_CARD_INDEX: Int = 5

@Composable
fun OrderDetailRoot(
    onNavigateToOrderForm: (String) -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: OrderDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val measurementsListState = rememberLazyListState()

    val paymentRecordedMessage = stringResource(Res.string.order_record_payment_snackbar_success)
    val orderArchivedMessage = stringResource(Res.string.order_archived_snackbar)
    val notesSavedMessage = stringResource(Res.string.order_detail_notes_saved_toast)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is OrderDetailEvent.NavigateToOrderForm -> onNavigateToOrderForm(event.orderId)
            is OrderDetailEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            OrderDetailEvent.NavigateBack -> onNavigateBack()
            OrderDetailEvent.OrderDeleted -> onNavigateBack()
            OrderDetailEvent.OrderArchived -> {
                snackbarScope.launch { snackbarHostState.showSnackbar(orderArchivedMessage) }
                onNavigateBack()
            }
            OrderDetailEvent.PaymentRecorded -> {
                snackbarScope.launch { snackbarHostState.showSnackbar(paymentRecordedMessage) }
            }
            OrderDetailEvent.NotesSaved -> {
                snackbarScope.launch { snackbarHostState.showSnackbar(notesSavedMessage) }
            }
            // Platform launchers wired in Task 6.3 — for now, no-op so the when stays exhaustive.
            is OrderDetailEvent.LaunchWhatsApp -> Unit
            is OrderDetailEvent.LaunchDialer -> Unit
            is OrderDetailEvent.NavigateToCreateOrder -> Unit
            is OrderDetailEvent.NavigateToMeasurementsList -> Unit
            OrderDetailEvent.ScrollToMeasurements -> {
                snackbarScope.launch {
                    measurementsListState.animateScrollToItem(MEASUREMENTS_CARD_INDEX)
                }
            }
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
        ShareReceiptBottomSheet(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiptBottomSheet(
    onShareAsImage: () -> Unit,
    onShareAsPdf: () -> Unit,
    onDismiss: () -> Unit
) {
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
            Text(
                text = stringResource(Res.string.share_receipt_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = DesignTokens.space4)
            )

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
    val isOverdue = order.deadline != null &&
        order.deadline < now &&
        order.status != OrderStatus.READY &&
        order.status != OrderStatus.DELIVERED
    val overdueDaysAgo = if (isOverdue && order.deadline != null) {
        ((now - order.deadline) / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
    } else {
        0
    }
    val cta = remember(order.status, order.subStatus, isOverdue, order.balanceRemaining) {
        resolvePrimaryCta(order.status, order.subStatus, isOverdue, order.balanceRemaining)
    }

    val firstItem = order.items.firstOrNull()
    val garmentName = firstItem?.let { garmentDisplayName(it.garmentType) }.orEmpty()
    val primaryFieldLabels = firstItem?.garmentType?.fieldLabels?.take(3).orEmpty()
    val dueLabel = formatDueLabel(order, isOverdue)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item {
            OrderHeroCard(
                fabricPhotoUrl = firstItem?.fabricPhotoUrl,
                garmentTypeIcon = Icons.Default.Checkroom,
                garmentName = garmentName,
                customerName = order.customerName,
                status = order.status,
                subStatus = order.subStatus,
                priority = order.priority,
                isOverdue = isOverdue,
                overdueDaysAgo = overdueDaysAgo,
                dueLabel = dueLabel,
                balanceRemaining = order.balanceRemaining,
                cta = cta,
                onPrimaryCta = { handlePrimaryCta(cta.primary, onAction) },
                onSecondaryCta = { handleSecondaryCta(cta.secondary, onAction) },
            )
        }
        item {
            OrderCustomerCard(
                customerName = order.customerName,
                phone = state.customer?.phone,
                onWhatsAppClick = { onAction(OrderDetailAction.OnWhatsAppClick) },
                onCallClick = { onAction(OrderDetailAction.OnCallClick) },
                onMeasurementsClick = { onAction(OrderDetailAction.OnMeasurementsScrollClick) },
                onCustomerClick = { onAction(OrderDetailAction.OnCustomerClick) },
            )
        }
        item {
            OrderGarmentDetailsCard(items = order.items, priority = order.priority)
        }
        item {
            OrderPaymentCard(
                totalPrice = order.totalPrice,
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
                primaryFieldLabels = primaryFieldLabels,
                onCardClick = { onAction(OrderDetailAction.OnLinkMeasurementsClick) },
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

private fun handleSecondaryCta(cta: SecondaryCta, onAction: (OrderDetailAction) -> Unit) {
    when (cta) {
        SecondaryCta.RecordPayment -> onAction(OrderDetailAction.OnRecordPaymentClick)
        SecondaryCta.MessageCustomer -> onAction(OrderDetailAction.OnWhatsAppClick)
        SecondaryCta.StartWork,
        SecondaryCta.UpdateStatus,
        SecondaryCta.MarkDelivered -> onAction(OrderDetailAction.OnUpdateStatusClick)
        SecondaryCta.DuplicateOrder -> onAction(OrderDetailAction.OnDuplicateClick)
    }
}

@Composable
private fun formatDueLabel(order: Order, isOverdue: Boolean): UiText {
    val deadlineDate = order.deadline?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val readableDeadline = deadlineDate?.let {
        val month = it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
        "${it.dayOfMonth} $month"
    } ?: ""
    return when {
        order.status == OrderStatus.DELIVERED -> UiText.StringResourceText(
            Res.string.order_detail_delivered_label,
            arrayOf(readableDeadline),
        )
        isOverdue -> UiText.StringResourceText(
            Res.string.order_detail_was_due_label,
            arrayOf(readableDeadline),
        )
        order.status == OrderStatus.READY -> UiText.StringResourceText(Res.string.order_detail_pickup_today)
        else -> UiText.StringResourceText(Res.string.order_detail_due_label, arrayOf(readableDeadline))
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

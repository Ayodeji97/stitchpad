package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_edit_order
import stitchpad.composeapp.generated.resources.order_delete_cancel
import stitchpad.composeapp.generated.resources.order_delete_confirm
import stitchpad.composeapp.generated.resources.order_delete_message
import stitchpad.composeapp.generated.resources.order_delete_title
import stitchpad.composeapp.generated.resources.order_detail_balance_remaining
import stitchpad.composeapp.generated.resources.order_detail_customer_section
import stitchpad.composeapp.generated.resources.order_detail_deadline_section
import stitchpad.composeapp.generated.resources.order_detail_delete_button
import stitchpad.composeapp.generated.resources.order_detail_deposit_paid
import stitchpad.composeapp.generated.resources.order_detail_fabric_photo
import stitchpad.composeapp.generated.resources.order_detail_financial_section
import stitchpad.composeapp.generated.resources.order_detail_items_section
import stitchpad.composeapp.generated.resources.order_detail_notes_section
import stitchpad.composeapp.generated.resources.order_detail_priority_section
import stitchpad.composeapp.generated.resources.order_detail_share
import stitchpad.composeapp.generated.resources.order_detail_status_history
import stitchpad.composeapp.generated.resources.order_detail_status_section
import stitchpad.composeapp.generated.resources.order_detail_title
import stitchpad.composeapp.generated.resources.order_detail_total_price
import stitchpad.composeapp.generated.resources.order_detail_update_status
import stitchpad.composeapp.generated.resources.order_overdue_label
import stitchpad.composeapp.generated.resources.order_priority_normal
import stitchpad.composeapp.generated.resources.order_priority_rush
import stitchpad.composeapp.generated.resources.order_priority_urgent
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready
import stitchpad.composeapp.generated.resources.order_status_update_cancel
import stitchpad.composeapp.generated.resources.order_status_update_confirm
import stitchpad.composeapp.generated.resources.order_status_update_title
import kotlin.time.Clock

@Composable
fun OrderDetailRoot(
    onNavigateToOrderForm: (String) -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel: OrderDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is OrderDetailEvent.NavigateToOrderForm -> onNavigateToOrderForm(event.orderId)
            is OrderDetailEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            OrderDetailEvent.NavigateBack -> onNavigateBack()
            OrderDetailEvent.OrderDeleted -> onNavigateBack()
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
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun OrderDetailScreen(
    state: OrderDetailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
                                contentDescription = stringResource(Res.string.order_detail_share)
                            )
                        }
                        IconButton(onClick = { onAction(OrderDetailAction.OnEditClick) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.cd_edit_order)
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
                    order = state.order,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }

    // Delete dialog
    if (state.showDeleteDialog) {
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
                Text(
                    text = stringResource(Res.string.order_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
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

    // Status update dialog — pick any status
    if (state.showStatusUpdateDialog && state.order != null) {
        val currentStatus = state.order.status
        AlertDialog(
            onDismissRequest = { onAction(OrderDetailAction.OnDismissStatusUpdate) },
            title = {
                Text(
                    text = stringResource(Res.string.order_status_update_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                    OrderStatus.entries.forEach { status ->
                        val isCurrentStatus = status == currentStatus
                        val isSelected = status == state.selectedNewStatus
                        val (statusColor, label) = statusColorAndLabel(status)

                        Surface(
                            onClick = {
                                if (!isCurrentStatus) {
                                    onAction(OrderDetailAction.OnSelectNewStatus(status))
                                }
                            },
                            shape = RoundedCornerShape(DesignTokens.radiusMd),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isCurrentStatus -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else -> Color.Transparent
                            },
                            enabled = !isCurrentStatus
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(DesignTokens.radiusSm),
                                    color = statusColor.copy(alpha = 0.15f),
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(Modifier.width(DesignTokens.space2))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrentStatus) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isCurrentStatus) {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "Current",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAction(OrderDetailAction.OnConfirmStatusUpdate) },
                    enabled = state.selectedNewStatus != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.order_status_update_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(OrderDetailAction.OnDismissStatusUpdate) }) {
                    Text(
                        text = stringResource(Res.string.order_status_update_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun OrderDetailContent(
    order: Order,
    onAction: (OrderDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val isOverdue = order.deadline != null &&
        order.deadline < now &&
        order.status != OrderStatus.DELIVERED

    Column(modifier = modifier.padding(horizontal = DesignTokens.space4)) {
        // Customer section
        SectionHeader(stringResource(Res.string.order_detail_customer_section))
        Card(
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(OrderDetailAction.OnCustomerClick) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(DesignTokens.space3)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space4))

        // Items section
        SectionHeader(stringResource(Res.string.order_detail_items_section))
        Card(
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(DesignTokens.space3)) {
                order.items.forEachIndexed { index, item ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = garmentDisplayName(item.garmentType),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (item.description.isNotBlank()) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "\u20A6${formatPrice(item.price)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!item.fabricPhotoUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(DesignTokens.space2))
                        SubcomposeAsyncImage(
                            model = item.fabricPhotoUrl,
                            contentDescription = stringResource(Res.string.order_detail_fabric_photo),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LoadingDots()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(DesignTokens.radiusSm))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    if (index < order.items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = DesignTokens.space2)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(DesignTokens.space4))

        // Financial section
        SectionHeader(stringResource(Res.string.order_detail_financial_section))
        Card(
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(DesignTokens.space3)) {
                FinancialRow(
                    label = stringResource(Res.string.order_detail_total_price),
                    value = "\u20A6${formatPrice(order.totalPrice)}",
                    isBold = true
                )
                Spacer(Modifier.height(DesignTokens.space1))
                FinancialRow(
                    label = stringResource(Res.string.order_detail_deposit_paid),
                    value = "\u20A6${formatPrice(order.depositPaid)}"
                )
                Spacer(Modifier.height(DesignTokens.space1))
                FinancialRow(
                    label = stringResource(Res.string.order_detail_balance_remaining),
                    value = "\u20A6${formatPrice(order.balanceRemaining)}",
                    isBold = true,
                    valueColor = if (order.balanceRemaining > 0) {
                        DesignTokens.warning500
                    } else {
                        DesignTokens.success500
                    }
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space4))

        // Status section
        SectionHeader(stringResource(Res.string.order_detail_status_section))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
                    ) {
                        StatusBadge(status = order.status)
                        if (isOverdue) {
                            Text(
                                text = stringResource(Res.string.order_overdue_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = DesignTokens.error500
                            )
                        }
                    }
                    if (order.status != OrderStatus.DELIVERED) {
                        Button(
                            onClick = { onAction(OrderDetailAction.OnUpdateStatusClick) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(DesignTokens.radiusMd)
                        ) {
                            Text(
                                text = stringResource(Res.string.order_detail_update_status),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Status history
                if (order.statusHistory.isNotEmpty()) {
                    Spacer(Modifier.height(DesignTokens.space3))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(DesignTokens.space2))
                    Text(
                        text = stringResource(Res.string.order_detail_status_history),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(DesignTokens.space1))
                    order.statusHistory.sortedByDescending { it.changedAt }.forEach { change ->
                        StatusHistoryItem(change = change)
                        Spacer(Modifier.height(DesignTokens.space1))
                    }
                }
            }
        }

        // Deadline
        if (order.deadline != null) {
            Spacer(Modifier.height(DesignTokens.space4))
            SectionHeader(stringResource(Res.string.order_detail_deadline_section))
            val deadlineDate = Instant.fromEpochMilliseconds(order.deadline)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            Text(
                text = "${deadlineDate.dayOfMonth}/${deadlineDate.monthNumber}/${deadlineDate.year}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isOverdue) DesignTokens.error500 else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
            )
        }

        // Priority
        Spacer(Modifier.height(DesignTokens.space4))
        SectionHeader(stringResource(Res.string.order_detail_priority_section))
        val priorityLabel = when (order.priority) {
            OrderPriority.NORMAL -> stringResource(Res.string.order_priority_normal)
            OrderPriority.URGENT -> stringResource(Res.string.order_priority_urgent)
            OrderPriority.RUSH -> stringResource(Res.string.order_priority_rush)
        }
        val priorityColor = when (order.priority) {
            OrderPriority.NORMAL -> MaterialTheme.colorScheme.onSurface
            OrderPriority.URGENT -> DesignTokens.warning500
            OrderPriority.RUSH -> DesignTokens.error500
        }
        Text(
            text = priorityLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = priorityColor
        )

        // Notes
        if (!order.notes.isNullOrBlank()) {
            Spacer(Modifier.height(DesignTokens.space4))
            SectionHeader(stringResource(Res.string.order_detail_notes_section))
            Text(
                text = order.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(DesignTokens.space6))

        // Delete button
        Button(
            onClick = { onAction(OrderDetailAction.OnDeleteClick) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.order_detail_delete_button),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(DesignTokens.space8))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(DesignTokens.space2))
}

@Composable
private fun StatusBadge(status: OrderStatus) {
    val (color, label) = when (status) {
        OrderStatus.PENDING -> DesignTokens.statusReceived to stringResource(Res.string.order_status_pending)
        OrderStatus.IN_PROGRESS -> DesignTokens.statusCutting to stringResource(Res.string.order_status_in_progress)
        OrderStatus.READY -> DesignTokens.statusReady to stringResource(Res.string.order_status_ready)
        OrderStatus.DELIVERED -> DesignTokens.statusDelivered to stringResource(Res.string.order_status_delivered)
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusSm),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatusHistoryItem(change: StatusChange) {
    val date = Instant.fromEpochMilliseconds(change.changedAt)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val dateStr = "${date.dayOfMonth}/${date.monthNumber}/${date.year} ${date.hour}:${date.minute.toString().padStart(
        2,
        '0'
    )}"
    val label = statusLabel(change.status)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FinancialRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor
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
private fun statusColorAndLabel(status: OrderStatus): Pair<Color, String> = when (status) {
    OrderStatus.PENDING -> DesignTokens.statusReceived to stringResource(Res.string.order_status_pending)
    OrderStatus.IN_PROGRESS -> DesignTokens.statusCutting to stringResource(Res.string.order_status_in_progress)
    OrderStatus.READY -> DesignTokens.statusReady to stringResource(Res.string.order_status_ready)
    OrderStatus.DELIVERED -> DesignTokens.statusDelivered to stringResource(Res.string.order_status_delivered)
}

private fun formatPrice(price: Double): String {
    val long = price.toLong()
    if (price == long.toDouble()) return addThousandsSeparator(long.toString())
    val parts = price.toString().split(".")
    val decimal = (parts.getOrElse(1) { "00" } + "00").take(2)
    return addThousandsSeparator(parts[0]) + "." + decimal
}

private fun addThousandsSeparator(intPart: String): String {
    val negative = intPart.startsWith("-")
    val digits = if (negative) intPart.drop(1) else intPart
    val result = buildString {
        digits.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) append(',')
            append(c)
        }
    }.reversed()
    return if (negative) "-$result" else result
}

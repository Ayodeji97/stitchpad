package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_delete_cancel
import stitchpad.composeapp.generated.resources.order_delete_confirm
import stitchpad.composeapp.generated.resources.order_delete_message
import stitchpad.composeapp.generated.resources.order_delete_title
import stitchpad.composeapp.generated.resources.order_empty_subtitle
import stitchpad.composeapp.generated.resources.order_empty_title
import stitchpad.composeapp.generated.resources.order_fab_cd
import stitchpad.composeapp.generated.resources.order_filter_all
import stitchpad.composeapp.generated.resources.order_filter_overdue
import stitchpad.composeapp.generated.resources.order_items_count_plural
import stitchpad.composeapp.generated.resources.order_items_count_singular
import stitchpad.composeapp.generated.resources.order_list_title
import stitchpad.composeapp.generated.resources.order_overdue_label
import stitchpad.composeapp.generated.resources.order_priority_rush
import stitchpad.composeapp.generated.resources.order_priority_urgent
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready

@Composable
fun OrderListRoot(
    onNavigateToOrderForm: () -> Unit,
    onNavigateToOrderDetail: (String) -> Unit
) {
    val viewModel: OrderListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OrderListEvent.NavigateToOrderForm -> onNavigateToOrderForm()
            is OrderListEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(OrderListAction.OnErrorDismiss)
        }
    }

    OrderListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    state: OrderListState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (OrderListAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.order_list_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(OrderListAction.OnAddOrderClick) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = DesignTokens.primary500
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.order_fab_cd)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OrderStatusFilterChips(
                selectedStatus = state.statusFilter,
                showOverdueOnly = state.showOverdueOnly,
                onStatusSelected = { onAction(OrderListAction.OnStatusFilterChange(it)) },
                onOverdueToggled = { onAction(OrderListAction.OnToggleOverdueFilter(it)) }
            )

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.orders.isEmpty() -> {
                    OrderEmptyState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = state.orders, key = { it.id }) { order ->
                            SwipeableOrderItem(
                                order = order,
                                onClick = { onAction(OrderListAction.OnOrderClick(order)) },
                                onDelete = { onAction(OrderListAction.OnDeleteOrderClick(order)) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = DesignTokens.space4)
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showDeleteDialog && state.orderToDelete != null) {
        AlertDialog(
            onDismissRequest = { onAction(OrderListAction.OnDismissDeleteDialog) },
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
                    onClick = { onAction(OrderListAction.OnConfirmDelete) },
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
                TextButton(onClick = { onAction(OrderListAction.OnDismissDeleteDialog) }) {
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
}

@Composable
private fun OrderStatusFilterChips(
    selectedStatus: OrderStatus?,
    showOverdueOnly: Boolean,
    onStatusSelected: (OrderStatus?) -> Unit,
    onOverdueToggled: (Boolean) -> Unit
) {
    val statusOptions: List<Pair<OrderStatus?, String>> = listOf(
        null to stringResource(Res.string.order_filter_all),
        OrderStatus.PENDING to stringResource(Res.string.order_status_pending),
        OrderStatus.IN_PROGRESS to stringResource(Res.string.order_status_in_progress),
        OrderStatus.READY to stringResource(Res.string.order_status_ready),
        OrderStatus.DELIVERED to stringResource(Res.string.order_status_delivered)
    )
    val overdueLabel = stringResource(Res.string.order_filter_overdue)

    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                bottom = DesignTokens.space2
            )
    ) {
        statusOptions.forEach { (status, label) ->
            val isSelected = !showOverdueOnly && selectedStatus == status
            FilterChip(
                selected = isSelected,
                onClick = { onStatusSelected(status) },
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

        FilterChip(
            selected = showOverdueOnly,
            onClick = { onOverdueToggled(!showOverdueOnly) },
            label = {
                Text(
                    text = overdueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (showOverdueOnly) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.Transparent,
                selectedLabelColor = DesignTokens.error500,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = if (showOverdueOnly) {
                BorderStroke(1.dp, DesignTokens.error500)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
        )
    }
}

@Composable
private fun OrderEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = DesignTokens.space8)
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusXl))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(DesignTokens.space4))
        Text(
            text = stringResource(Res.string.order_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.order_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(3f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableOrderItem(
    order: Order,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = DesignTokens.space5)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            OrderListItem(order = order, onClick = onClick)
        }
    }
}

@Composable
private fun OrderListItem(order: Order, onClick: () -> Unit) {
    val now = Clock.System.now().toEpochMilliseconds()
    val isOverdue = order.deadline != null &&
        order.deadline < now &&
        order.status != OrderStatus.DELIVERED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                Text(
                    text = order.customerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (order.priority != OrderPriority.NORMAL) {
                    PriorityBadge(priority = order.priority)
                }
            }

            Spacer(Modifier.height(2.dp))

            val itemCount = order.items.size
            val itemsText = if (itemCount == 1) {
                stringResource(Res.string.order_items_count_singular, itemCount)
            } else {
                stringResource(Res.string.order_items_count_plural, itemCount)
            }
            val garmentSummary = order.items.take(2).joinToString(", ") { it.garmentType.name.replace("_", " ") }
            val summaryText = if (order.items.size > 2) "$garmentSummary, ..." else garmentSummary

            Text(
                text = "$itemsText \u00B7 $summaryText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(DesignTokens.space1))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                OrderStatusBadge(status = order.status)
                if (isOverdue) {
                    Text(
                        text = stringResource(Res.string.order_overdue_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DesignTokens.error500
                    )
                }
            }
        }

        Spacer(Modifier.width(DesignTokens.space3))

        Text(
            text = "\u20A6${formatPrice(order.totalPrice)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OrderStatusBadge(status: OrderStatus) {
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
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 2.dp)
        )
    }
}

@Composable
private fun PriorityBadge(priority: OrderPriority) {
    val (color, label) = when (priority) {
        OrderPriority.NORMAL -> return
        OrderPriority.URGENT -> DesignTokens.warning500 to stringResource(Res.string.order_priority_urgent)
        OrderPriority.RUSH -> DesignTokens.error500 to stringResource(Res.string.order_priority_rush)
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusSm),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 2.dp)
        )
    }
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

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun OrderListScreenEmptyPreview() {
    StitchPadTheme {
        OrderListScreen(state = OrderListState(isLoading = false), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun OrderListScreenFilledPreview() {
    StitchPadTheme {
        OrderListScreen(
            state = OrderListState(
                isLoading = false,
                orders = listOf(
                    Order(
                        id = "1",
                        userId = "u1",
                        customerId = "c1",
                        customerName = "Amina Bello",
                        items = listOf(
                            com.danzucker.stitchpad.core.domain.model.OrderItem(
                                id = "i1",
                                garmentType = com.danzucker.stitchpad.core.domain.model.GarmentType.AGBADA,
                                description = "White agbada",
                                price = 25000.0
                            )
                        ),
                        status = OrderStatus.PENDING,
                        priority = OrderPriority.NORMAL,
                        statusHistory = emptyList(),
                        totalPrice = 25000.0,
                        depositPaid = 10000.0,
                        balanceRemaining = 15000.0,
                        deadline = null,
                        notes = null,
                        createdAt = 0L,
                        updatedAt = 0L
                    )
                )
            ),
            onAction = {}
        )
    }
}

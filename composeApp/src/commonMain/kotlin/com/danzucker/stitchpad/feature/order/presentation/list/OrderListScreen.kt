package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.order.presentation.garmentSummaryRes
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_delete_cancel
import stitchpad.composeapp.generated.resources.order_delete_confirm
import stitchpad.composeapp.generated.resources.order_delete_message
import stitchpad.composeapp.generated.resources.order_delete_title
import stitchpad.composeapp.generated.resources.order_empty_delivered_title
import stitchpad.composeapp.generated.resources.order_empty_filtered_subtitle
import stitchpad.composeapp.generated.resources.order_empty_in_progress_title
import stitchpad.composeapp.generated.resources.order_empty_pending_title
import stitchpad.composeapp.generated.resources.order_empty_ready_title
import stitchpad.composeapp.generated.resources.order_empty_subtitle
import stitchpad.composeapp.generated.resources.order_empty_title
import stitchpad.composeapp.generated.resources.order_fab_cd
import stitchpad.composeapp.generated.resources.order_filter_all
import stitchpad.composeapp.generated.resources.order_list_title
import stitchpad.composeapp.generated.resources.order_priority_rush
import stitchpad.composeapp.generated.resources.order_priority_urgent
import stitchpad.composeapp.generated.resources.order_status_delivered
import stitchpad.composeapp.generated.resources.order_status_in_progress
import stitchpad.composeapp.generated.resources.order_status_pending
import stitchpad.composeapp.generated.resources.order_status_ready
import kotlin.time.Clock

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

// Dividers indent past the avatar so the text column visually aligns with its separator.
// Derived from the actual row layout (horizontal padding + avatar + gap) so it stays in sync
// if any of those tokens change.
private val orderRowTextInset = DesignTokens.space4 + OrderRowAvatarSize + DesignTokens.space3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
                onStatusSelected = { onAction(OrderListAction.OnStatusFilterChange(it)) }
            )

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.orders.isEmpty() -> {
                    OrderEmptyState(
                        statusFilter = state.statusFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // Stable `now` per snapshot of orders — prevents rows from drifting
                    // between triage buckets on unrelated recompositions (dialogs, snackbars).
                    val now = remember(state.orders) { Clock.System.now().toEpochMilliseconds() }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.statusFilter == null) {
                            val groups = groupOrdersIntoTriage(state.orders, now)
                            groups.forEach { (group, ordersInGroup) ->
                                stickyHeader(key = "header-${group.name}") {
                                    TriageSectionHeader(group = group, count = ordersInGroup.size)
                                }
                                items(items = ordersInGroup, key = { it.id }) { order ->
                                    SwipeableOrderItem(
                                        order = order,
                                        now = now,
                                        onClick = { onAction(OrderListAction.OnOrderClick(order)) },
                                        onDelete = { onAction(OrderListAction.OnDeleteOrderClick(order)) }
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = orderRowTextInset)
                                    )
                                }
                            }
                        } else {
                            items(items = state.orders, key = { it.id }) { order ->
                                SwipeableOrderItem(
                                    order = order,
                                    now = now,
                                    onClick = { onAction(OrderListAction.OnOrderClick(order)) },
                                    onDelete = { onAction(OrderListAction.OnDeleteOrderClick(order)) }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(start = orderRowTextInset)
                                )
                            }
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
    onStatusSelected: (OrderStatus?) -> Unit
) {
    val statusOptions: List<Pair<OrderStatus?, String>> = listOf(
        null to stringResource(Res.string.order_filter_all),
        OrderStatus.PENDING to stringResource(Res.string.order_status_pending),
        OrderStatus.IN_PROGRESS to stringResource(Res.string.order_status_in_progress),
        OrderStatus.READY to stringResource(Res.string.order_status_ready),
        OrderStatus.DELIVERED to stringResource(Res.string.order_status_delivered)
    )

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
            val isSelected = selectedStatus == status
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
    }
}

@Composable
private fun OrderEmptyState(
    statusFilter: OrderStatus?,
    modifier: Modifier = Modifier
) {
    val titleRes = when (statusFilter) {
        null -> Res.string.order_empty_title
        OrderStatus.PENDING -> Res.string.order_empty_pending_title
        OrderStatus.IN_PROGRESS -> Res.string.order_empty_in_progress_title
        OrderStatus.READY -> Res.string.order_empty_ready_title
        OrderStatus.DELIVERED -> Res.string.order_empty_delivered_title
    }
    val subtitleRes = if (statusFilter == null) {
        Res.string.order_empty_subtitle
    } else {
        Res.string.order_empty_filtered_subtitle
    }

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
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(subtitleRes),
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
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val currentOnDelete by rememberUpdatedState(onDelete)

    // Watch the settled value rather than intercepting via confirmValueChange.
    // The confirmValueChange-returning-false pattern leaves the state in a
    // half-settled position on some devices, causing subsequent swipes on
    // the same row to silently no-op. Resetting explicitly after dispatch
    // guarantees a clean slate for the next swipe.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .collect { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    currentOnDelete()
                    dismissState.reset()
                }
            }
    }

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
            OrderListItem(order = order, now = now, onClick = onClick)
        }
    }
}

@Composable
private fun OrderListItem(order: Order, now: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3)
    ) {
        OrderRowAvatar(name = order.customerName, customerId = order.customerId)

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

            Text(
                text = garmentSummary(order),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            DeadlineLine(deadline = order.deadline, now = now, status = order.status)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "\u20A6${formatPrice(order.totalPrice)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            PaymentStatusText(depositPaid = order.depositPaid, totalPrice = order.totalPrice)
        }
    }
}

@Composable
private fun garmentSummary(order: Order): String {
    val firstItem = order.items.firstOrNull() ?: return ""
    val count = order.items.size
    return stringResource(garmentSummaryRes(firstItem.garmentType, count), count)
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
    val now = 1_700_000_000_000L
    val oneDay = 24L * 60 * 60 * 1000
    StitchPadTheme {
        OrderListScreen(
            state = OrderListState(
                isLoading = false,
                orders = listOf(
                    Order(
                        id = "1", userId = "u", customerId = "c1", customerName = "Fola Sunday",
                        items = listOf(OrderItem("i1", GarmentType.CORSET, "", 40_000.0)),
                        status = OrderStatus.PENDING, priority = OrderPriority.RUSH,
                        statusHistory = emptyList(),
                        totalPrice = 40_000.0, depositPaid = 0.0, balanceRemaining = 40_000.0,
                        deadline = now - 3 * oneDay, notes = null, createdAt = 0L, updatedAt = 0L
                    ),
                    Order(
                        id = "2", userId = "u", customerId = "c2", customerName = "Aina Paul",
                        items = listOf(OrderItem("i2", GarmentType.SUIT, "", 20_000.0)),
                        status = OrderStatus.PENDING, priority = OrderPriority.URGENT,
                        statusHistory = emptyList(),
                        totalPrice = 20_000.0, depositPaid = 10_000.0, balanceRemaining = 10_000.0,
                        deadline = now + 2 * oneDay, notes = null, createdAt = 0L, updatedAt = 0L
                    ),
                    Order(
                        id = "3", userId = "u", customerId = "c3", customerName = "Dayyo Au",
                        items = listOf(OrderItem("i3", GarmentType.SUIT, "", 4_000.0)),
                        status = OrderStatus.READY, priority = OrderPriority.RUSH,
                        statusHistory = emptyList(),
                        totalPrice = 4_000.0, depositPaid = 2_000.0, balanceRemaining = 2_000.0,
                        deadline = null, notes = null, createdAt = 0L, updatedAt = 0L
                    )
                )
            ),
            onAction = {}
        )
    }
}

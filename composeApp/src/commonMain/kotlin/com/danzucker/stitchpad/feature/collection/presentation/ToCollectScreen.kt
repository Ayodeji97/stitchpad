package com.danzucker.stitchpad.feature.collection.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.collection.domain.CollectibleOrder
import com.danzucker.stitchpad.feature.collection.domain.CollectionFilter
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort
import com.danzucker.stitchpad.feature.collection.domain.CollectionSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToCollectScreen(
    state: ToCollectState,
    onAction: (ToCollectAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To collect") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ToCollectAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    SortMenuButton(sort = state.sort, onAction = onAction)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SummaryHeader(state.summary)
            FilterRow(state.filter, state.customerOptions, onAction)
            HorizontalDivider()
            if (state.items.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(DesignTokens.space4),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    items(state.items, key = { it.orderId }) { row ->
                        CollectibleRow(
                            row = row,
                            onClick = { onAction(ToCollectAction.OnRowClick(row.orderId)) },
                            onChase = { onAction(ToCollectAction.OnChaseClick(row.orderId)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(summary: CollectionSummary) {
    Column(Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
        Text("You're owed", style = MaterialTheme.typography.labelMedium)
        Text(
            "₦${formatPrice(summary.totalOutstanding)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        val overdueSuffix = if (summary.overdueCount > 0) " · ${summary.overdueCount} overdue" else ""
        Text(
            "across ${summary.orderCount} orders$overdueSuffix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuButton(sort: CollectionSort, onAction: (ToCollectAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CollectionSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.sortLabel()) },
                    leadingIcon = {
                        if (option == sort) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onAction(ToCollectAction.OnSortSelected(option))
                    },
                )
            }
        }
    }
}

// TODO: localize via compose.resources — inline for now, consistent with the rest of this screen.
private fun CollectionSort.sortLabel(): String = when (this) {
    CollectionSort.OLDEST_OWED -> "Oldest owed"
    CollectionSort.BIGGEST_BALANCE -> "Biggest balance"
    CollectionSort.NEWEST -> "Newest"
    CollectionSort.CUSTOMER_NAME -> "Customer name"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    filter: CollectionFilter,
    customerOptions: List<CustomerFilterOption>,
    onAction: (ToCollectAction) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DesignTokens.space4),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        FilterChip(
            selected = filter == CollectionFilter.None,
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.None)) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == CollectionFilter.OverdueOnly,
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.OverdueOnly)) },
            label = { Text("Overdue") },
        )
        FilterChip(
            selected = filter == CollectionFilter.ByStatus(OrderStatus.DELIVERED),
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.ByStatus(OrderStatus.DELIVERED))) },
            label = { Text("Delivered") },
        )
        FilterChip(
            selected = filter == CollectionFilter.ByStatus(OrderStatus.READY),
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.ByStatus(OrderStatus.READY))) },
            label = { Text("Ready") },
        )
        if (customerOptions.isNotEmpty()) {
            CustomerFilterChip(filter, customerOptions, onAction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerFilterChip(
    filter: CollectionFilter,
    customerOptions: List<CustomerFilterOption>,
    onAction: (ToCollectAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCustomer = (filter as? CollectionFilter.ByCustomer)?.let { byCustomer ->
        customerOptions.find { it.id == byCustomer.customerId }
    }
    Box {
        FilterChip(
            selected = selectedCustomer != null,
            onClick = { expanded = true },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            label = { Text(selectedCustomer?.name ?: "Customer") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            customerOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onAction(ToCollectAction.OnFilterSelected(CollectionFilter.ByCustomer(option.id)))
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectibleRow(
    row: CollectibleOrder,
    onClick: () -> Unit,
    onChase: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.customerName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val unit = if (row.daysOwed == 1) "day" else "days"
                val owedLabel = if (row.isOverdue) {
                    "Overdue · owed ${row.daysOwed} $unit"
                } else {
                    "owed ${row.daysOwed} $unit"
                }
                val owedColor = if (row.isOverdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    owedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = owedColor,
                )
            }
            Text(
                "₦${formatPrice(row.balanceRemaining)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onChase) { Text("Chase") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(DesignTokens.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("You're all paid up", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nothing to collect right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ToCollectScreenFilledPreview() {
    StitchPadTheme {
        ToCollectScreen(
            state = ToCollectState(
                isLoading = false,
                summary = CollectionSummary(48_500.0, 3, 1),
                items = listOf(
                    CollectibleOrder("o1", "c1", "Ada Obi", "080", 20_000.0, 0L, 12, true, OrderStatus.DELIVERED),
                    CollectibleOrder("o2", "c2", "Emeka N", "080", 28_500.0, 0L, 2, false, OrderStatus.READY),
                ),
                customerOptions = listOf(
                    CustomerFilterOption("c1", "Ada Obi"),
                    CustomerFilterOption("c2", "Emeka N"),
                ),
            ),
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ToCollectScreenEmptyPreview() {
    StitchPadTheme {
        ToCollectScreen(
            state = ToCollectState(isLoading = false),
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

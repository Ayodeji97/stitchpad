package com.danzucker.stitchpad.feature.customer.presentation.list

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.customer.presentation.list.components.CustomerActionsSheet
import com.danzucker.stitchpad.feature.freemium.presentation.swap.SwapSheet
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.danzucker.stitchpad.util.clearFocusOnTap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_customer_overflow
import stitchpad.composeapp.generated.resources.customer_delete_blocked_dismiss
import stitchpad.composeapp.generated.resources.customer_delete_blocked_message
import stitchpad.composeapp.generated.resources.customer_delete_blocked_title
import stitchpad.composeapp.generated.resources.customer_delete_cancel
import stitchpad.composeapp.generated.resources.customer_delete_confirm
import stitchpad.composeapp.generated.resources.customer_delete_message
import stitchpad.composeapp.generated.resources.customer_delete_title
import stitchpad.composeapp.generated.resources.customer_empty_state_subtitle
import stitchpad.composeapp.generated.resources.customer_empty_state_title
import stitchpad.composeapp.generated.resources.customer_fab_cd
import stitchpad.composeapp.generated.resources.customer_list_title
import stitchpad.composeapp.generated.resources.customer_locked_chip
import stitchpad.composeapp.generated.resources.customer_locked_row_swap_cta
import stitchpad.composeapp.generated.resources.customer_locked_section_subtitle
import stitchpad.composeapp.generated.resources.customer_locked_section_title
import stitchpad.composeapp.generated.resources.customer_search_clear_cd
import stitchpad.composeapp.generated.resources.customer_search_hint
import stitchpad.composeapp.generated.resources.customer_search_no_results_subtitle
import stitchpad.composeapp.generated.resources.customer_search_no_results_title
import stitchpad.composeapp.generated.resources.customer_swap_failure
import stitchpad.composeapp.generated.resources.customer_swap_success
import stitchpad.composeapp.generated.resources.whatsapp_launch_failed

@Composable
fun CustomerListRoot(
    onNavigateToAddCustomer: () -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateToEditCustomer: (String) -> Unit,
    onNavigateToAddMeasurement: (String) -> Unit,
    onNavigateToOrderForm: (String) -> Unit,
) {
    val viewModel: CustomerListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val whatsAppLauncher: WhatsAppLauncher = koinInject()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CustomerListEvent.NavigateToAddCustomer -> onNavigateToAddCustomer()
            is CustomerListEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            is CustomerListEvent.NavigateToEditCustomer -> onNavigateToEditCustomer(event.customerId)
            is CustomerListEvent.NavigateToAddMeasurement -> onNavigateToAddMeasurement(event.customerId)
            is CustomerListEvent.NavigateToOrderForm -> onNavigateToOrderForm(event.customerId)
            is CustomerListEvent.SwapSucceeded -> scope.launch {
                snackbarHostState.showSnackbar(
                    getString(Res.string.customer_swap_success, event.promotedFirstName)
                )
            }
            CustomerListEvent.SwapFailed -> scope.launch {
                snackbarHostState.showSnackbar(getString(Res.string.customer_swap_failure))
            }
            is CustomerListEvent.LaunchWhatsApp -> scope.launch {
                val launched = whatsAppLauncher.launch(event.phone, event.message)
                if (!launched) {
                    snackbarHostState.showSnackbar(getString(Res.string.whatsapp_launch_failed))
                }
            }
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(CustomerListAction.OnErrorDismiss)
        }
    }

    CustomerListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    state: CustomerListState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (CustomerListAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.customer_list_title),
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
            StitchPadFab(
                onClick = { onAction(CustomerListAction.OnAddCustomerClick) },
                contentDescription = stringResource(Res.string.customer_fab_cd)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clearFocusOnTap()
        ) {
            CustomerSearchField(
                query = state.searchQuery,
                onQueryChange = { onAction(CustomerListAction.OnSearchQueryChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2)
            )

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.customers.isEmpty() && state.lockedCustomers.isEmpty() -> {
                    if (state.searchQuery.isNotBlank()) {
                        CustomerSearchNoResultsState(
                            query = state.searchQuery,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CustomerEmptyState(
                            onClick = { onAction(CustomerListAction.OnAddCustomerClick) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = state.customers, key = { it.id }) { customer ->
                            SwipeableCustomerItem(
                                customer = customer,
                                onClick = { onAction(CustomerListAction.OnCustomerClick(customer)) },
                                onDelete = { onAction(CustomerListAction.OnDeleteCustomerClick(customer)) },
                                onOverflowClick = { onAction(CustomerListAction.OnOverflowClick(customer)) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = DesignTokens.space4 + 44.dp + DesignTokens.space3)
                            )
                        }

                        if (state.lockedCustomers.isNotEmpty()) {
                            item(key = "locked_section_header") {
                                Spacer(Modifier.height(DesignTokens.space4))
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = DesignTokens.space4,
                                        vertical = DesignTokens.space2,
                                    ),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(DesignTokens.space2))
                                        Text(
                                            text = stringResource(
                                                Res.string.customer_locked_section_title,
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(DesignTokens.space2))
                                        Text(
                                            text = "· ${state.lockedCustomers.size}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(
                                            Res.string.customer_locked_section_subtitle,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(state.lockedCustomers, key = { "locked_${it.id}" }) { customer ->
                                LockedCustomerRow(
                                    customer = customer,
                                    onTap = { onAction(CustomerListAction.OnCustomerClick(customer)) },
                                    onSwapTap = { onAction(CustomerListAction.OpenSwapSheetFor(customer.id)) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(
                                        start = DesignTokens.space4 + 44.dp + DesignTokens.space3,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.swapSheetForId?.let { lockedId ->
        val lockedCustomer = state.lockedCustomers.firstOrNull { it.id == lockedId }
        if (lockedCustomer != null) {
            SwapSheet(
                lockedCustomer = lockedCustomer,
                activeCustomers = state.customers,
                onConfirm = { demoteId -> onAction(CustomerListAction.ConfirmSwap(lockedId, demoteId)) },
                onDismiss = { onAction(CustomerListAction.DismissSwapSheet) },
            )
        }
    }

    state.actionsSheetForId?.let { customerId ->
        val customer = state.customers.firstOrNull { it.id == customerId }
        if (customer != null) {
            CustomerActionsSheet(
                customer = customer,
                onView = { id -> onAction(CustomerListAction.OnViewCustomerFromSheet(id)) },
                onMessageWhatsApp = { c -> onAction(CustomerListAction.OnMessageWhatsApp(c)) },
                onEdit = { id -> onAction(CustomerListAction.OnEditCustomerFromRow(id)) },
                onNewMeasurement = { id -> onAction(CustomerListAction.OnAddMeasurementFromRow(id)) },
                onNewOrder = { id -> onAction(CustomerListAction.OnNewOrderFromRow(id)) },
                onDelete = { c -> onAction(CustomerListAction.OnDeleteCustomerClick(c)) },
                onDismiss = { onAction(CustomerListAction.DismissActionsSheet) },
            )
        }
    }

    if (state.showDeleteDialog && state.customerToDelete != null) {
        if (state.customerToDeleteActiveOrderCount > 0) {
            AlertDialog(
                onDismissRequest = { onAction(CustomerListAction.OnDismissDeleteDialog) },
                title = {
                    Text(
                        text = stringResource(
                            Res.string.customer_delete_blocked_title,
                            state.customerToDelete.name
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            Res.string.customer_delete_blocked_message,
                            state.customerToDelete.name,
                            state.customerToDeleteActiveOrderCount
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onAction(CustomerListAction.OnDismissDeleteDialog) },
                        shape = RoundedCornerShape(DesignTokens.radiusMd)
                    ) {
                        Text(
                            text = stringResource(Res.string.customer_delete_blocked_dismiss),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                shape = RoundedCornerShape(DesignTokens.radiusXl),
                containerColor = MaterialTheme.colorScheme.surface
            )
        } else {
            AlertDialog(
                onDismissRequest = { onAction(CustomerListAction.OnDismissDeleteDialog) },
                title = {
                    Text(
                        text = stringResource(Res.string.customer_delete_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(Res.string.customer_delete_message, state.customerToDelete.name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onAction(CustomerListAction.OnConfirmDelete) },
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
                    TextButton(onClick = { onAction(CustomerListAction.OnDismissDeleteDialog) }) {
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
    }
}

@Composable
private fun CustomerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(Res.string.customer_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.customer_search_clear_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}

@Composable
private fun CustomerEmptyState(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addLabel = stringResource(Res.string.customer_fab_cd)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = DesignTokens.space8)
    ) {
        Spacer(Modifier.weight(1f))
        // Tap target is the icon + text cluster, not the whole screen — testers
        // reach for the icon, and tapping blank space to add a record is surprising.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(
                onClickLabel = addLabel,
                role = Role.Button,
                onClick = onClick,
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusXl))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(DesignTokens.space4))
            Text(
                text = stringResource(Res.string.customer_empty_state_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.customer_empty_state_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(3f))
    }
}

@Composable
private fun CustomerSearchNoResultsState(
    query: String,
    modifier: Modifier = Modifier
) {
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
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(DesignTokens.space4))
        Text(
            text = stringResource(Res.string.customer_search_no_results_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.customer_search_no_results_subtitle, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(3f))
    }
}

@Composable
private fun LockedCustomerRow(
    customer: Customer,
    onTap: () -> Unit,
    onSwapTap: () -> Unit,
) {
    // The main row tap opens the customer's read-only detail page (per V1.0 spec
    // decision #2 — locked data is visible, not hidden). The Swap text button on the
    // right invokes the existing SwapSheet path directly so swapping stays one tap
    // from the list.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(start = DesignTokens.space4, top = DesignTokens.space3, bottom = DesignTokens.space3),
    ) {
        CustomerAvatar(
            name = customer.name,
            size = 44.dp,
            modifier = Modifier.alpha(0.38f),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.customer_locked_chip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        TextButton(onClick = onSwapTap) {
            Text(
                text = stringResource(Res.string.customer_locked_row_swap_cta),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCustomerItem(
    customer: Customer,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOverflowClick: () -> Unit,
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            CustomerListItem(customer = customer, onClick = onClick, onOverflowClick = onOverflowClick)
        }
    }
}

@Composable
private fun CustomerListItem(
    customer: Customer,
    onClick: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space2,
                top = DesignTokens.space3,
                bottom = DesignTokens.space3,
            )
    ) {
        CustomerAvatar(name = customer.name, size = 44.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = customer.phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onOverflowClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.cd_customer_overflow, customer.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerListScreenEmptyPreview() {
    StitchPadTheme {
        CustomerListScreen(state = CustomerListState(isLoading = false), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerListScreenSearchNoResultsPreview() {
    StitchPadTheme {
        CustomerListScreen(
            state = CustomerListState(
                isLoading = false,
                searchQuery = "ksfnskd",
                customers = emptyList(),
                lockedCustomers = emptyList()
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerListScreenFilledPreview() {
    StitchPadTheme {
        CustomerListScreen(
            state = CustomerListState(
                isLoading = false,
                customers = listOf(
                    Customer(id = "1", userId = "u1", name = "Amina Bello", phone = "+234 801 234 5678"),
                    Customer(id = "2", userId = "u1", name = "Chidi Okafor", phone = "+234 802 345 6789"),
                    Customer(
                        id = "3",
                        userId = "u1",
                        name = "Ngozi Adeyemi",
                        phone = "+234 803 456 7890",
                    )
                )
            ),
            onAction = {}
        )
    }
}

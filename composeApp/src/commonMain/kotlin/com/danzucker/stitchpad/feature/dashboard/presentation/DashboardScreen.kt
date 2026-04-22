package com.danzucker.stitchpad.feature.dashboard.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.ui.components.CustomerAvatar
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.currency_naira
import stitchpad.composeapp.generated.resources.dashboard_all_clear
import stitchpad.composeapp.generated.resources.dashboard_and_more
import stitchpad.composeapp.generated.resources.dashboard_due_today_title
import stitchpad.composeapp.generated.resources.dashboard_fab_cd
import stitchpad.composeapp.generated.resources.dashboard_greeting_afternoon
import stitchpad.composeapp.generated.resources.dashboard_greeting_evening
import stitchpad.composeapp.generated.resources.dashboard_greeting_morning
import stitchpad.composeapp.generated.resources.dashboard_outstanding_subtitle
import stitchpad.composeapp.generated.resources.dashboard_outstanding_title
import stitchpad.composeapp.generated.resources.dashboard_overdue_title
import stitchpad.composeapp.generated.resources.dashboard_ready_title
import stitchpad.composeapp.generated.resources.dashboard_see_all
import stitchpad.composeapp.generated.resources.dashboard_welcome_cta
import stitchpad.composeapp.generated.resources.dashboard_welcome_subtitle
import stitchpad.composeapp.generated.resources.dashboard_welcome_title
import kotlin.math.roundToLong

private const val MAX_ROWS_PER_CARD = 3
private const val THOUSANDS = 1_000L
private const val MILLIONS = 1_000_000L

@Composable
fun DashboardRoot(
    onNavigateToOrderDetail: (String) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToOrderForm: () -> Unit,
    onNavigateToCustomerForm: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DashboardEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
            DashboardEvent.NavigateToOrders -> onNavigateToOrders()
            DashboardEvent.NavigateToOrderForm -> onNavigateToOrderForm()
            DashboardEvent.NavigateToCustomerForm -> onNavigateToCustomerForm()
        }
    }

    val errorText = state.errorMessage?.asString()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.onAction(DashboardAction.OnErrorDismiss)
        }
    }

    DashboardScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (DashboardAction) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(DashboardAction.OnNewOrderClick) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(DesignTokens.radiusLg),
                    spotColor = DesignTokens.primary500
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.dashboard_fab_cd)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            state.isBrandNew -> WelcomeHero(
                onAddCustomerClick = { onAction(DashboardAction.OnNewCustomerClick) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            else -> DashboardContent(
                state = state,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space4,
                bottom = 96.dp
            )
    ) {
        DashboardHeader(
            businessName = state.businessName,
            greeting = state.greeting,
            todayDate = state.todayDate
        )

        if (state.isAllClear) {
            AllClearBanner()
        }

        if (state.overdue.isNotEmpty()) {
            OrdersCard(
                title = stringResource(Res.string.dashboard_overdue_title, state.overdue.size),
                accentColor = DesignTokens.error500,
                icon = Icons.Default.Warning,
                rows = state.overdue,
                totalCount = state.overdue.size,
                onRowClick = { onAction(DashboardAction.OnOrderClick(it)) },
                onSeeAllClick = { onAction(DashboardAction.OnSeeAllClick) }
            )
        }

        if (state.dueToday.isNotEmpty()) {
            OrdersCard(
                title = stringResource(Res.string.dashboard_due_today_title, state.dueToday.size),
                accentColor = DesignTokens.primary600,
                icon = Icons.Default.DateRange,
                rows = state.dueToday,
                totalCount = state.dueToday.size,
                onRowClick = { onAction(DashboardAction.OnOrderClick(it)) },
                onSeeAllClick = { onAction(DashboardAction.OnSeeAllClick) }
            )
        }

        if (state.ready.isNotEmpty()) {
            OrdersCard(
                title = stringResource(Res.string.dashboard_ready_title, state.ready.size),
                accentColor = DesignTokens.success500,
                icon = Icons.Default.CheckCircle,
                rows = state.ready,
                totalCount = state.ready.size,
                onRowClick = { onAction(DashboardAction.OnOrderClick(it)) },
                onSeeAllClick = { onAction(DashboardAction.OnSeeAllClick) }
            )
        }

        if (state.outstandingOrderCount > 0) {
            OutstandingCard(
                amount = state.outstandingAmount,
                orderCount = state.outstandingOrderCount,
                onClick = { onAction(DashboardAction.OnOutstandingClick) }
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    businessName: String?,
    greeting: Greeting,
    todayDate: LocalDate?
) {
    val name = businessName.orEmpty()
    val greetingText = when (greeting) {
        Greeting.MORNING -> stringResource(Res.string.dashboard_greeting_morning, name)
        Greeting.AFTERNOON -> stringResource(Res.string.dashboard_greeting_afternoon, name)
        Greeting.EVENING -> stringResource(Res.string.dashboard_greeting_evening, name)
    }
    Column {
        Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (todayDate != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = todayDate.formatFriendly(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OrdersCard(
    title: String,
    accentColor: Color,
    icon: ImageVector,
    rows: List<DashboardOrderRow>,
    totalCount: Int,
    onRowClick: (String) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation1,
        shadowElevation = DesignTokens.elevation1,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            val visibleRows = rows.take(MAX_ROWS_PER_CARD)
            visibleRows.forEachIndexed { index, row ->
                OrderRow(row = row, onClick = { onRowClick(row.orderId) })
                if (index < visibleRows.lastIndex) {
                    Spacer(Modifier.height(DesignTokens.space2))
                }
            }

            if (totalCount > MAX_ROWS_PER_CARD) {
                Spacer(Modifier.height(DesignTokens.space3))
                Text(
                    text = stringResource(
                        Res.string.dashboard_and_more,
                        totalCount - MAX_ROWS_PER_CARD
                    ) + " · " + stringResource(Res.string.dashboard_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DesignTokens.primary600,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSeeAllClick)
                        .padding(vertical = DesignTokens.space1)
                )
            }
        }
    }
}

@Composable
private fun OrderRow(
    row: DashboardOrderRow,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .clickable(onClick = onClick)
            .padding(vertical = DesignTokens.space1)
    ) {
        CustomerAvatar(name = row.customerName, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.customerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(
                row.primaryLabel.takeIf { it.isNotBlank() },
                row.secondaryLabel
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun OutstandingCard(
    amount: Double,
    orderCount: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation1,
        shadowElevation = DesignTokens.elevation1,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space4)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(DesignTokens.primary50)
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = DesignTokens.primary600,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        Res.string.dashboard_outstanding_title,
                        stringResource(Res.string.currency_naira, formatAbbreviated(amount))
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.dashboard_outstanding_subtitle, orderCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AllClearBanner() {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = DesignTokens.success500.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space4)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DesignTokens.success500,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(Res.string.dashboard_all_clear),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WelcomeHero(
    onAddCustomerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = DesignTokens.space6)
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusXl))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(DesignTokens.space5))
        Text(
            text = stringResource(Res.string.dashboard_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.dashboard_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(DesignTokens.space5))
        Surface(
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAddCustomerClick)
        ) {
            Text(
                text = stringResource(Res.string.dashboard_welcome_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space6,
                    vertical = DesignTokens.space3
                )
            )
        }
        Spacer(Modifier.weight(2f))
    }
}

private fun LocalDate.formatFriendly(): String {
    val day = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val mon = month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }
    return "$day, $dayOfMonth $mon"
}

private fun formatAbbreviated(amount: Double): String {
    val rounded = amount.roundToLong()
    return when {
        rounded >= MILLIONS -> "${rounded / MILLIONS}m"
        rounded >= THOUSANDS -> "${rounded / THOUSANDS}k"
        else -> rounded.toString()
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenFilledPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                isLoading = false,
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22),
                overdue = listOf(
                    DashboardOrderRow("1", "Mr. Kola", "Agbada", "2d late"),
                    DashboardOrderRow("2", "Mrs. Ibe", "Blouse", "1d late")
                ),
                dueToday = listOf(
                    DashboardOrderRow("3", "Mr. Tunde", "Suit"),
                    DashboardOrderRow("4", "Mrs. Chika", "Dress"),
                    DashboardOrderRow("5", "Mr. Femi", "Senator"),
                    DashboardOrderRow("6", "Mrs. Adaeze", "Bridal Gown")
                ),
                ready = listOf(DashboardOrderRow("7", "Mrs. Funke", "Senator")),
                outstandingAmount = 45_000.0,
                outstandingOrderCount = 7
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenBrandNewPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                isLoading = false,
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22),
                isBrandNew = true
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenAllClearPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                isLoading = false,
                businessName = "Ade's Fashions",
                greeting = Greeting.AFTERNOON,
                todayDate = LocalDate(2026, 4, 22),
                isAllClear = true
            ),
            onAction = {}
        )
    }
}

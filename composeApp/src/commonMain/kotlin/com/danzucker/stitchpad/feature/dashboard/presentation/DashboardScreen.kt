package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextAlign
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
import stitchpad.composeapp.generated.resources.dashboard_chip_ready
import stitchpad.composeapp.generated.resources.dashboard_chip_today
import stitchpad.composeapp.generated.resources.dashboard_fab_cd
import stitchpad.composeapp.generated.resources.dashboard_greeting_afternoon
import stitchpad.composeapp.generated.resources.dashboard_greeting_evening
import stitchpad.composeapp.generated.resources.dashboard_greeting_morning
import stitchpad.composeapp.generated.resources.dashboard_section_todays_work
import stitchpad.composeapp.generated.resources.dashboard_tile_due_today
import stitchpad.composeapp.generated.resources.dashboard_tile_outstanding
import stitchpad.composeapp.generated.resources.dashboard_tile_overdue
import stitchpad.composeapp.generated.resources.dashboard_tile_ready
import stitchpad.composeapp.generated.resources.dashboard_welcome_cta
import stitchpad.composeapp.generated.resources.dashboard_welcome_subtitle
import stitchpad.composeapp.generated.resources.dashboard_welcome_title
import kotlin.math.roundToLong

private const val THOUSANDS = 1_000L
private const val MILLIONS = 1_000_000L
private const val ACCENT_BAR_WIDTH_DP = 3

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
        } else {
            TileGrid(state = state, onAction = onAction)
            TodaysWorkList(state = state, onAction = onAction)
        }
    }
}

/**
 * Data bundle for a single dashboard tile. Self-hiding logic lives in [TileGrid] —
 * tiles whose bucket is empty aren't built, so a calm day doesn't render "0 Overdue" noise.
 */
private data class TileData(
    val icon: ImageVector,
    val valueText: String,
    val labelText: String,
    val accent: Color,
    val background: Color,
    val onClick: () -> Unit,
    val valueFontSize: Int = TILE_VALUE_DEFAULT_SP
)

private const val TILE_VALUE_DEFAULT_SP = 26
private const val TILE_VALUE_CURRENCY_SP = 20

@Composable
private fun TileGrid(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val errorBg = DesignTokens.error500.copy(alpha = 0.08f)

    val tiles = buildList {
        if (state.overdue.isNotEmpty()) {
            add(
                TileData(
                    icon = Icons.Default.Warning,
                    valueText = state.overdue.size.toString(),
                    labelText = stringResource(Res.string.dashboard_tile_overdue),
                    accent = DesignTokens.error500,
                    background = errorBg,
                    onClick = { onAction(DashboardAction.OnSeeAllClick) }
                )
            )
        }
        if (state.dueToday.isNotEmpty()) {
            add(
                TileData(
                    icon = Icons.Default.DateRange,
                    valueText = state.dueToday.size.toString(),
                    labelText = stringResource(Res.string.dashboard_tile_due_today),
                    accent = DesignTokens.primary600,
                    background = surface,
                    onClick = { onAction(DashboardAction.OnSeeAllClick) }
                )
            )
        }
        if (state.ready.isNotEmpty()) {
            add(
                TileData(
                    icon = Icons.Default.CheckCircle,
                    valueText = state.ready.size.toString(),
                    labelText = stringResource(Res.string.dashboard_tile_ready),
                    accent = DesignTokens.success500,
                    background = surface,
                    onClick = { onAction(DashboardAction.OnSeeAllClick) }
                )
            )
        }
        if (state.outstandingOrderCount > 0) {
            val naira = stringResource(
                Res.string.currency_naira,
                formatAbbreviated(state.outstandingAmount)
            )
            add(
                TileData(
                    icon = Icons.Default.Payments,
                    valueText = naira,
                    labelText = stringResource(Res.string.dashboard_tile_outstanding),
                    accent = DesignTokens.primary600,
                    background = surface,
                    onClick = { onAction(DashboardAction.OnOutstandingClick) },
                    valueFontSize = TILE_VALUE_CURRENCY_SP
                )
            )
        }
    }

    if (tiles.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                pair.forEach { tile ->
                    Tile(tile = tile, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(tile: TileData, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = tile.background,
        tonalElevation = DesignTokens.elevation1,
        shadowElevation = DesignTokens.elevation1,
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .clickable(onClick = tile.onClick)
    ) {
        Box(modifier = Modifier.padding(DesignTokens.space3).fillMaxWidth()) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = tile.accent.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
            )
            Column {
                Text(
                    text = tile.valueText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(
                            tile.valueFontSize.toFloat(),
                            androidx.compose.ui.unit.TextUnitType.Sp
                        )
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = tile.accent
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tile.labelText.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Tag attached to each [DashboardOrderRow] in the unified list to drive colour and chip text. */
private enum class RowAccent { Overdue, DueToday, Ready }

private data class ListItem(val row: DashboardOrderRow, val accent: RowAccent)

@Composable
private fun TodaysWorkList(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit
) {
    val items = buildList {
        state.overdue.forEach { add(ListItem(it, RowAccent.Overdue)) }
        state.dueToday.forEach { add(ListItem(it, RowAccent.DueToday)) }
        state.ready.forEach { add(ListItem(it, RowAccent.Ready)) }
    }
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Text(
            text = stringResource(Res.string.dashboard_section_todays_work).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = DesignTokens.space2)
        )
        items.forEach { item ->
            AccentedOrderRow(
                item = item,
                onClick = { onAction(DashboardAction.OnOrderClick(item.row.orderId)) }
            )
        }
    }
}

@Composable
private fun AccentedOrderRow(item: ListItem, onClick: () -> Unit) {
    val accentColor = when (item.accent) {
        RowAccent.Overdue -> DesignTokens.error500
        RowAccent.DueToday -> DesignTokens.primary600
        RowAccent.Ready -> DesignTokens.success500
    }
    val chipBackground = when (item.accent) {
        RowAccent.Overdue -> DesignTokens.error500.copy(alpha = 0.12f)
        RowAccent.DueToday -> DesignTokens.primary500.copy(alpha = 0.12f)
        RowAccent.Ready -> DesignTokens.success500.copy(alpha = 0.12f)
    }
    val chipText = when (item.accent) {
        RowAccent.Overdue -> item.row.secondaryLabel.orEmpty()
        RowAccent.DueToday -> stringResource(Res.string.dashboard_chip_today)
        RowAccent.Ready -> stringResource(Res.string.dashboard_chip_ready)
    }

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
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(ACCENT_BAR_WIDTH_DP.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space3
                    )
            ) {
                CustomerAvatar(name = item.row.customerName, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.row.customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.row.primaryLabel.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.row.primaryLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (chipText.isNotBlank()) {
                    StatusChip(
                        text = chipText,
                        textColor = accentColor,
                        background = chipBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, textColor: Color, background: Color) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = background
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space2,
                vertical = 3.dp
            )
        )
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
            textAlign = TextAlign.Center
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
                    DashboardOrderRow("1", "Fola Sunday", "Corset", "4d late")
                ),
                dueToday = listOf(
                    DashboardOrderRow("2", "Bimbo Dann", "Dress")
                ),
                ready = listOf(
                    DashboardOrderRow("3", "Bimbo Dann", "Dress")
                ),
                outstandingAmount = 480_000.0,
                outstandingOrderCount = 1
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenBusyPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                isLoading = false,
                businessName = "Ade's Fashions",
                greeting = Greeting.AFTERNOON,
                todayDate = LocalDate(2026, 4, 22),
                overdue = listOf(
                    DashboardOrderRow("1", "Mr. Kola", "Agbada", "2d late"),
                    DashboardOrderRow("2", "Mrs. Ibe", "Blouse", "1d late")
                ),
                dueToday = listOf(
                    DashboardOrderRow("3", "Mr. Tunde", "Suit"),
                    DashboardOrderRow("4", "Mrs. Chika", "Dress"),
                    DashboardOrderRow("5", "Mr. Femi", "Senator")
                ),
                ready = listOf(DashboardOrderRow("6", "Mrs. Funke", "Senator")),
                outstandingAmount = 145_000.0,
                outstandingOrderCount = 5
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

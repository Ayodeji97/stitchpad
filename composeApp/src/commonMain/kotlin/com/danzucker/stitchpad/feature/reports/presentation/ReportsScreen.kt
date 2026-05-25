package com.danzucker.stitchpad.feature.reports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.reports.domain.model.CappedList
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerBadge
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.Kpi
import com.danzucker.stitchpad.feature.reports.domain.model.KpiSummary
import com.danzucker.stitchpad.feature.reports.domain.model.ProductionCounts
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.presentation.components.CustomRangePickerDialog
import com.danzucker.stitchpad.feature.reports.presentation.components.KpiGrid
import com.danzucker.stitchpad.feature.reports.presentation.components.OutstandingBalancesCard
import com.danzucker.stitchpad.feature.reports.presentation.components.ProductionStatusCard
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsEmptyState
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsLoadingSkeleton
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsPaywallCard
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsTabRow
import com.danzucker.stitchpad.feature.reports.presentation.components.SelectedRangeChip
import com.danzucker.stitchpad.feature.reports.presentation.components.TopCustomersCard
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_custom
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_month
import stitchpad.composeapp.generated.resources.reports_delta_vs_last_week
import stitchpad.composeapp.generated.resources.reports_period_custom
import stitchpad.composeapp.generated.resources.reports_period_this_month
import stitchpad.composeapp.generated.resources.reports_period_this_week
import stitchpad.composeapp.generated.resources.reports_reminder_template
import stitchpad.composeapp.generated.resources.reports_select_date_range_cd
import stitchpad.composeapp.generated.resources.reports_title
import stitchpad.composeapp.generated.resources.reports_whatsapp_launch_failed
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun ReportsRoot(
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateToUpgrade: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
    whatsAppLauncher: WhatsAppLauncher = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val timeZone = remember { TimeZone.currentSystemDefault() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ReportsEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            ReportsEvent.NavigateToUpgrade -> onNavigateToUpgrade()
            is ReportsEvent.LaunchWhatsAppReminder -> {
                scope.launch {
                    val message = getString(
                        Res.string.reports_reminder_template,
                        event.customerName,
                        formatPrice(event.totalOwed)
                    )
                    val launched = whatsAppLauncher.launch(event.customerPhone, message)
                    if (!launched) {
                        snackbarHostState.showSnackbar(
                            getString(Res.string.reports_whatsapp_launch_failed)
                        )
                    }
                }
            }
        }
    }

    ReportsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        timeZone = timeZone,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun ReportsScreen(
    state: ReportsState,
    snackbarHostState: SnackbarHostState,
    timeZone: TimeZone,
    onAction: (ReportsAction) -> Unit
) {
    val errorString = state.errorMessage?.asString()
    LaunchedEffect(errorString) {
        if (errorString != null) {
            snackbarHostState.showSnackbar(errorString)
            onAction(ReportsAction.OnErrorDismiss)
        }
    }

    var showRangePicker by remember { mutableStateOf(false) }

    // 'today' is sourced from state (computed in the VM via nowMillis). Using
    // a remember-block here would freeze the date at first composition and
    // leave urgency labels stale across midnight. We fall back to a fresh
    // Clock read only until the first VM emission populates state.today.
    // kotlinx.datetime.Clock.System is unresolved on iOS in 0.6.x — use
    // kotlin.time + epoch-millis conversion (matches the VM's nowMillis pattern).
    val today = state.today ?: run {
        val millis = Clock.System.now().toEpochMilliseconds()
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.reports_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showRangePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = stringResource(
                                Res.string.reports_select_date_range_cd
                            ),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ReportsTabRow(
                selected = state.selectedPeriod,
                onSelect = { onAction(ReportsAction.OnPeriodSelected(it)) },
                onCustomTap = {
                    // Switch to CUSTOM up-front so the pill reflects the user's
                    // intent immediately; data falls back to Week math until a
                    // range is actually picked.
                    onAction(ReportsAction.OnPeriodSelected(ReportsPeriod.CUSTOM))
                    showRangePicker = true
                }
            )
            val activeRange = state.customRange
            if (state.selectedPeriod == ReportsPeriod.CUSTOM && activeRange != null) {
                SelectedRangeChip(
                    range = activeRange,
                    onClick = { showRangePicker = true },
                    onClear = { onAction(ReportsAction.OnClearCustomRange) },
                    modifier = Modifier.padding(
                        start = DesignTokens.space4,
                        end = DesignTokens.space4,
                        bottom = DesignTokens.space2
                    )
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> ReportsLoadingSkeleton(
                        modifier = Modifier.padding(
                            horizontal = DesignTokens.space4,
                            vertical = DesignTokens.space2
                        )
                    )
                    !state.isPremium -> ReportsPaywallCard(
                        onUpgradeClick = { onAction(ReportsAction.OnUpgradeClick) }
                    )
                    !state.hasAnyOrders -> ReportsEmptyState()
                    else -> ReportsContent(
                        kpiSummary = state.kpiSummary,
                        productionCounts = state.productionCounts,
                        topCustomers = state.topCustomers,
                        debtors = state.debtors,
                        period = state.selectedPeriod,
                        customRange = state.customRange,
                        today = today,
                        onAction = onAction
                    )
                }
            }
        }
    }

    if (showRangePicker) {
        CustomRangePickerDialog(
            initial = state.customRange,
            timeZone = timeZone,
            onConfirm = { range ->
                showRangePicker = false
                onAction(ReportsAction.OnCustomRangeSelected(range))
            },
            onDismiss = { showRangePicker = false }
        )
    }
}

@Composable
private fun ReportsContent(
    kpiSummary: KpiSummary?,
    productionCounts: ProductionCounts?,
    topCustomers: CappedList<CustomerRanking>,
    debtors: CappedList<DebtorEntry>,
    period: ReportsPeriod,
    customRange: CustomRange?,
    today: LocalDate,
    onAction: (ReportsAction) -> Unit
) {
    // When the user has tapped Custom but not yet picked a range, the
    // ViewModel's calculator falls back to WEEK math so the screen doesn't
    // blank — keep the UI labels aligned with that effective period (so the
    // tile doesn't claim 'vs prior range' while the math is actually
    // last-week-vs-this-week). Once a range is picked, switch to Custom.
    val effectivePeriod = if (period == ReportsPeriod.CUSTOM && customRange == null) {
        ReportsPeriod.WEEK
    } else {
        period
    }
    val deltaSuffix = when (effectivePeriod) {
        ReportsPeriod.WEEK -> stringResource(Res.string.reports_delta_vs_last_week)
        ReportsPeriod.MONTH -> stringResource(Res.string.reports_delta_vs_last_month)
        ReportsPeriod.CUSTOM -> stringResource(Res.string.reports_delta_vs_last_custom)
    }
    // For Custom (with a range), prefer the actual dates ('Apr 14 – 23')
    // over the generic 'Custom range' label — the chip up top already shows
    // the full range, but echoing the dates inside each tile reinforces
    // what the value represents at the point of reading.
    val customRangeLabel = customRange?.let(::formatTileRangeLabel)
    val periodLabel = when (effectivePeriod) {
        ReportsPeriod.WEEK -> stringResource(Res.string.reports_period_this_week)
        ReportsPeriod.MONTH -> stringResource(Res.string.reports_period_this_month)
        ReportsPeriod.CUSTOM ->
            customRangeLabel ?: stringResource(Res.string.reports_period_custom)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space2,
                bottom = DesignTokens.space6
            ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        if (kpiSummary != null) {
            KpiGrid(
                summary = kpiSummary,
                deltaSuffix = deltaSuffix,
                periodLabel = periodLabel
            )
        }
        if (productionCounts != null) {
            ProductionStatusCard(counts = productionCounts)
        }
        // Outstanding sits above Top Customers — collecting unpaid balances
        // is the most action-driving piece of the report for a tailor.
        OutstandingBalancesCard(
            debtors = debtors,
            today = today,
            onDebtorClick = { onAction(ReportsAction.OnDebtorClick(it)) },
            onSendReminder = { onAction(ReportsAction.OnSendReminderClick(it)) },
            onViewAllClick = { /* Stage 4: navigate to expanded list */ }
        )
        TopCustomersCard(
            rankings = topCustomers,
            onCustomerClick = { onAction(ReportsAction.OnTopCustomerClick(it)) },
            onViewAllClick = { /* Stage 4: navigate to expanded list */ }
        )
    }
}

// Compact range label for the KPI tile footer (third line). The
// SelectedRangeChip shows the full date with year up top, so each tile
// can drop the year and stay terse:
//   same month       -> "Apr 14 – 23"
//   different months -> "Apr 28 – May 5"
//   different years  -> "Dec 28, 2025 – Jan 3, 2026" (rare; keep year)
private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun formatTileRangeLabel(range: CustomRange): String {
    val start = range.start
    val end = range.end
    val startMonth = MONTH_ABBREV[start.monthNumber - 1]
    val endMonth = MONTH_ABBREV[end.monthNumber - 1]
    return when {
        start.year == end.year && start.monthNumber == end.monthNumber ->
            "$startMonth ${start.dayOfMonth} – ${end.dayOfMonth}"
        start.year == end.year ->
            "$startMonth ${start.dayOfMonth} – $endMonth ${end.dayOfMonth}"
        else ->
            "$startMonth ${start.dayOfMonth}, ${start.year} – " +
                "$endMonth ${end.dayOfMonth}, ${end.year}"
    }
}

// --------------- Previews ---------------

private val revenueSpark = listOf(
    2_100_000.0,
    3_400_000.0,
    2_800_000.0,
    4_200_000.0,
    3_900_000.0,
    4_770_000.0,
    5_100_000.0,
    5_360_000.0
)
private val collectedSpark = listOf(
    1_200_000.0,
    2_300_000.0,
    1_900_000.0,
    3_000_000.0,
    2_800_000.0,
    3_196_000.0,
    3_400_000.0,
    3_510_000.0
)
private val outstandingSpark = listOf(
    900_000.0,
    1_100_000.0,
    900_000.0,
    1_200_000.0,
    1_500_000.0,
    1_752_000.0,
    1_700_000.0,
    1_850_000.0
)
private val ordersSpark = listOf(5.0, 12.0, 8.0, 14.0, 10.0, 15.0, 16.0, 18.0)

private val previewKpis = KpiSummary(
    revenue = Kpi(
        current = 5_360_000.0,
        previous = 4_770_000.0,
        deltaPercent = 12.4,
        sparkline = revenueSpark
    ),
    collected = Kpi(
        current = 3_510_000.0,
        previous = 3_196_000.0,
        deltaPercent = 9.8,
        sparkline = collectedSpark
    ),
    outstanding = Kpi(
        current = 1_850_000.0,
        previous = 1_752_000.0,
        deltaPercent = 5.6,
        sparkline = outstandingSpark
    ),
    orders = Kpi(
        current = 18.0,
        previous = 15.0,
        deltaPercent = 20.0,
        sparkline = ordersSpark
    )
)

private val previewProduction = ProductionCounts(
    pending = 6,
    inProgress = 9,
    ready = 3,
    delivered = 12
)

// 4 shown out of 12 total → preview should render "View all (12)".
private val previewTopCustomers = CappedList(
    items = listOf(
        CustomerRanking("c1", "Ade Yinka", 512_400.0, 12, badge = CustomerBadge.VIP),
        CustomerRanking("c2", "Blessing Tosin", 328_750.0, 9, badge = CustomerBadge.REPEAT),
        CustomerRanking("c3", "Pooja Paul", 276_300.0, 7, badge = CustomerBadge.REPEAT),
        CustomerRanking("c4", "Posi John", 198_600.0, 6, badge = CustomerBadge.VIP)
    ),
    totalCount = 12
)

private val previewDebtors = CappedList(
    items = listOf(
        DebtorEntry(
            customerId = "c1",
            customerName = "Ade Yinka",
            totalOwed = 78_500.0,
            orderCount = 1,
            oldestDeadline = LocalDate(2026, 5, 10),
            canSendWhatsAppReminder = true
        ),
        DebtorEntry(
            customerId = "c2",
            customerName = "Blessing Tosin",
            totalOwed = 45_000.0,
            orderCount = 1,
            oldestDeadline = LocalDate(2026, 4, 29),
            canSendWhatsAppReminder = true
        ),
        DebtorEntry(
            customerId = "c3",
            customerName = "Pooja Paul",
            totalOwed = 32_500.0,
            orderCount = 1,
            oldestDeadline = LocalDate(2026, 5, 1),
            canSendWhatsAppReminder = false
        ),
        DebtorEntry(
            customerId = "c4",
            customerName = "Posi John",
            totalOwed = 15_000.0,
            orderCount = 1,
            oldestDeadline = LocalDate(2026, 5, 6),
            canSendWhatsAppReminder = true
        )
    ),
    totalCount = 7
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenWeekPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(
                isLoading = false,
                isPremium = true,
                selectedPeriod = ReportsPeriod.WEEK,
                hasAnyOrders = true,
                kpiSummary = previewKpis,
                productionCounts = previewProduction,
                topCustomers = previewTopCustomers,
                debtors = previewDebtors
            ),
            snackbarHostState = remember { SnackbarHostState() },
            timeZone = TimeZone.UTC,
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenPaywallPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(
                isLoading = false,
                isPremium = false,
                hasAnyOrders = true
            ),
            snackbarHostState = remember { SnackbarHostState() },
            timeZone = TimeZone.UTC,
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenLoadingPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(isLoading = true),
            snackbarHostState = remember { SnackbarHostState() },
            timeZone = TimeZone.UTC,
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenEmptyPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(isLoading = false, hasAnyOrders = false),
            snackbarHostState = remember { SnackbarHostState() },
            timeZone = TimeZone.UTC,
            onAction = {}
        )
    }
}

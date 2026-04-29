package com.danzucker.stitchpad.feature.reports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.feature.reports.domain.model.AllTimeSummary
import com.danzucker.stitchpad.feature.reports.domain.model.CustomerRanking
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import com.danzucker.stitchpad.feature.reports.domain.model.RevenueSummary
import com.danzucker.stitchpad.feature.reports.presentation.components.AllTimeSummaryCard
import com.danzucker.stitchpad.feature.reports.presentation.components.DebtorsCard
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsEmptyState
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsLoadingSkeleton
import com.danzucker.stitchpad.feature.reports.presentation.components.ReportsTabRow
import com.danzucker.stitchpad.feature.reports.presentation.components.RevenueHeroCard
import com.danzucker.stitchpad.feature.reports.presentation.components.TopCustomersCard
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reports_title

@Composable
fun ReportsRoot(
    onNavigateToCustomerDetail: (String) -> Unit,
    viewModel: ReportsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ReportsEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
            // Wired to WhatsAppLauncher in V2 stage 3 — currently a no-op.
            is ReportsEvent.LaunchWhatsAppReminder -> Unit
        }
    }

    ReportsScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsState,
    onAction: (ReportsAction) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorString = state.errorMessage?.asString()

    LaunchedEffect(errorString) {
        // Same pattern as DashboardRoot: a repeat of the *same* error UiText won't
        // re-trigger this effect (key unchanged). Acceptable for V1 — Firestore
        // offline persistence means errors are rare and the user can pull to refresh
        // by switching tabs once a real connection comes back.
        if (errorString != null) {
            snackbarHostState.showSnackbar(errorString)
            onAction(ReportsAction.OnErrorDismiss)
        }
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
                onSelect = { onAction(ReportsAction.OnPeriodSelected(it)) }
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        ReportsLoadingSkeleton(
                            modifier = Modifier.padding(
                                horizontal = DesignTokens.space4,
                                vertical = DesignTokens.space2
                            )
                        )
                    }
                    !state.hasAnyOrders -> ReportsEmptyState()
                    else -> ReportsContent(
                        summary = state.revenueSummary,
                        period = state.selectedPeriod,
                        topCustomers = state.topCustomers,
                        debtors = state.debtors,
                        allTimeSummary = state.allTimeSummary,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsContent(
    summary: RevenueSummary?,
    period: ReportsPeriod,
    topCustomers: List<CustomerRanking>,
    debtors: List<DebtorEntry>,
    allTimeSummary: AllTimeSummary?,
    onAction: (ReportsAction) -> Unit
) {
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
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
    ) {
        if (summary != null) {
            RevenueHeroCard(summary = summary, period = period)
        }
        TopCustomersCard(
            rankings = topCustomers,
            onCustomerClick = { onAction(ReportsAction.OnTopCustomerClick(it)) }
        )
        DebtorsCard(
            debtors = debtors,
            onDebtorClick = { onAction(ReportsAction.OnDebtorClick(it)) }
        )
        if (allTimeSummary != null) {
            AllTimeSummaryCard(summary = allTimeSummary)
        }
    }
}

// --------------- Previews ---------------

private val previewSummary = RevenueSummary(
    current = 142_000.0,
    previous = 118_000.0,
    deltaAmount = 24_000.0,
    deltaPercent = 20.3,
    sparkline = listOf(20_000.0, 35_000.0, 28_000.0, 64_000.0, 88_000.0, 95_000.0, 118_000.0, 142_000.0)
)

private val previewTopCustomers = listOf(
    CustomerRanking("c1", "Adaeze Okeke", 38_000.0, 2),
    CustomerRanking("c2", "Tunde Adekunle", 24_000.0, 1),
    CustomerRanking("c3", "Chiamaka Eze", 18_500.0, 1)
)

private val previewDebtors = listOf(
    DebtorEntry("c4", "Bola Ajayi", 45_000.0, orderCount = 2, oldestDeadline = LocalDate(2026, 4, 12)),
    DebtorEntry("c5", "Kemi Williams", 18_000.0, orderCount = 1, oldestDeadline = LocalDate(2026, 4, 18))
)

private val previewAllTime = AllTimeSummary(
    totalCollected = 1_840_000.0,
    orderCount = 42,
    topCustomerName = "Adaeze Okeke",
    topCustomerTotal = 380_000.0
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenWeekPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(
                isLoading = false,
                selectedPeriod = ReportsPeriod.WEEK,
                hasAnyOrders = true,
                revenueSummary = previewSummary,
                topCustomers = previewTopCustomers,
                debtors = previewDebtors,
                allTimeSummary = previewAllTime
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenMonthPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(
                isLoading = false,
                selectedPeriod = ReportsPeriod.MONTH,
                hasAnyOrders = true,
                revenueSummary = previewSummary.copy(sparkline = previewSummary.sparkline.takeLast(6)),
                topCustomers = previewTopCustomers,
                debtors = previewDebtors,
                allTimeSummary = previewAllTime
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenCustomPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(
                isLoading = false,
                selectedPeriod = ReportsPeriod.CUSTOM,
                hasAnyOrders = true,
                revenueSummary = RevenueSummary(
                    current = 320_000.0,
                    previous = 250_000.0,
                    deltaAmount = 70_000.0,
                    deltaPercent = 28.0,
                    sparkline = listOf(320_000.0)
                ),
                topCustomers = previewTopCustomers,
                debtors = previewDebtors,
                allTimeSummary = previewAllTime
            ),
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
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReportsScreenFirstWeekPreview() {
    StitchPadTheme {
        ReportsScreen(
            state = ReportsState(
                isLoading = false,
                selectedPeriod = ReportsPeriod.WEEK,
                hasAnyOrders = true,
                revenueSummary = RevenueSummary(
                    current = 5_255_000.0,
                    previous = 0.0,
                    deltaAmount = 5_255_000.0,
                    deltaPercent = null,
                    sparkline = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5_255_000.0)
                ),
                topCustomers = listOf(
                    CustomerRanking("c1", "Ade Yinka", 4_335_000.0, 6),
                    CustomerRanking("c2", "Blessing Tosin", 720_000.0, 3),
                    CustomerRanking("c3", "Posi John", 80_000.0, 2)
                ),
                debtors = listOf(
                    DebtorEntry("c2", "Blessing Tosin", 1_850_000.0, orderCount = 3, oldestDeadline = null),
                    DebtorEntry("c3", "Posi John", 460_000.0, orderCount = 2, oldestDeadline = null)
                ),
                allTimeSummary = AllTimeSummary(
                    totalCollected = 5_255_000.0,
                    orderCount = 11,
                    topCustomerName = "Ade Yinka",
                    topCustomerTotal = 4_335_000.0
                )
            ),
            onAction = {}
        )
    }
}

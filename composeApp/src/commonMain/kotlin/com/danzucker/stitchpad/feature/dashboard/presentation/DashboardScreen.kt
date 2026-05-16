@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.presentation.components.BellButton
import com.danzucker.stitchpad.feature.dashboard.presentation.components.CustomerReadyCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyCardCtaStyle
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyIllustrationCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyIllustrationSlot
import com.danzucker.stitchpad.feature.dashboard.presentation.components.IllustratedFocusCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.OnboardingStepsCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.OrderSetupActionCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.PipelineSection
import com.danzucker.stitchpad.feature.dashboard.presentation.components.ReconnectHeroSection
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupChecklistCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStep
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStepKey
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStepStatus
import com.danzucker.stitchpad.feature.dashboard.presentation.components.TodayWorkCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.UserAvatar
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FirstOrderSetupUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalPace
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.buildTodayWorkRows
import com.danzucker.stitchpad.feature.smart.presentation.SmartSectionCard
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.NextBestActionCard
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.components.WeeklyGoalsCard
import com.danzucker.stitchpad.ui.components.WeeklyGoalsCardState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.currency_naira
import stitchpad.composeapp.generated.resources.customer_ready_section_label
import stitchpad.composeapp.generated.resources.dashboard_fab_cd
import stitchpad.composeapp.generated.resources.dashboard_greeting_afternoon
import stitchpad.composeapp.generated.resources.dashboard_greeting_evening
import stitchpad.composeapp.generated.resources.dashboard_greeting_morning
import stitchpad.composeapp.generated.resources.dashboard_loading_cd
import stitchpad.composeapp.generated.resources.dashboard_nba_card_cd
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_deposit_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_deposit_title
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_overdue_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_overdue_sub_one
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_overdue_title
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_ready_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_ready_sub_one
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_ready_sub_today
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_ready_title
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_collect_deposit
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_open_order
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_send_reminder
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_view_order
import stitchpad.composeapp.generated.resources.dashboard_nba_deliver_stale_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_deliver_stale_title
import stitchpad.composeapp.generated.resources.dashboard_nba_finish_stale_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_finish_stale_title
import stitchpad.composeapp.generated.resources.dashboard_nba_quiet_cta
import stitchpad.composeapp.generated.resources.dashboard_nba_quiet_supporting
import stitchpad.composeapp.generated.resources.dashboard_nba_quiet_title
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_sub_today
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_title
import stitchpad.composeapp.generated.resources.dashboard_section_next_actions
import stitchpad.composeapp.generated.resources.dashboard_section_next_actions_subtitle
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_collect_overdue
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_collect_ready
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_launch_failed
import stitchpad.composeapp.generated.resources.goals_achieved_cta
import stitchpad.composeapp.generated.resources.goals_achieved_section_label
import stitchpad.composeapp.generated.resources.goals_days_left
import stitchpad.composeapp.generated.resources.goals_days_left_one
import stitchpad.composeapp.generated.resources.goals_earned
import stitchpad.composeapp.generated.resources.goals_motivation_ahead
import stitchpad.composeapp.generated.resources.goals_motivation_behind
import stitchpad.composeapp.generated.resources.goals_motivation_near_goal
import stitchpad.composeapp.generated.resources.goals_motivation_on_pace
import stitchpad.composeapp.generated.resources.goals_percent_of_goal
import stitchpad.composeapp.generated.resources.goals_revenue_label
import stitchpad.composeapp.generated.resources.goals_section_label
import stitchpad.composeapp.generated.resources.goals_set_first_cta
import stitchpad.composeapp.generated.resources.goals_set_first_label
import stitchpad.composeapp.generated.resources.goals_set_first_section
import stitchpad.composeapp.generated.resources.goals_set_first_supporting
import stitchpad.composeapp.generated.resources.goals_supporting
import stitchpad.composeapp.generated.resources.goals_to_go
import stitchpad.composeapp.generated.resources.reconnect_whatsapp_template
import kotlin.math.roundToLong

private const val THOUSANDS = 1_000L
private const val MILLIONS = 1_000_000L

// Scroll bottom padding. With FAB visible we need clearance so the last
// content row isn't obscured: FAB is 50dp tall + 16dp bottom margin = 66dp
// occupied at the bottom-right corner; 80dp gives a 14dp breathing gap.
// Without FAB (BrandNew, Loading) we drop to a normal page margin so the
// content doesn't leave a visible empty band on tall screens.
// 80dp left the chevron of the last visible row sitting under the FAB at
// the bottom of the scroll. 120dp gives the row enough additional travel
// to clear the FAB so its tap target is always reachable.
private val FAB_BOTTOM_PADDING = 120.dp
private val NO_FAB_BOTTOM_PADDING = 24.dp

/**
 * Dashboard states where the user opens the screen expecting *what to do
 * next* (urgency / revenue moves) rather than *how the goal is going*. On
 * these states the NBA carousel is rendered above Weekly Goal so the
 * actionable revenue work is the first thing reached after the hero.
 */
private val PROMOTED_NBA_STATES = setOf(
    DashboardUiState.BusyDay,
    DashboardUiState.ReadyForPickup,
    DashboardUiState.NbaActive,
)

/**
 * Dashboard states where the "Nothing urgent right now" empty NBA card
 * adds something the hero hasn't already said. Restricted to PipelineSteady
 * because:
 *  - ReadyForPickup hero IS the urgent action (deliver), so the empty
 *    card directly contradicts it.
 *  - QuietDay hero already nudges the user toward reconnect work, so the
 *    empty card just duplicates that message.
 *  - BrandNew / FirstCustomer have no past customers to reconnect with —
 *    the empty card's CTA would be dead UI.
 *  - BusyDay / NbaActive always have non-empty NBAs, so this branch is
 *    unreachable from those states.
 */
private val EMPTY_NBA_CARD_STATES = setOf(
    DashboardUiState.PipelineSteady,
)

/**
 * Dashboard states where an *empty* Work Pipeline section is silenced
 * because some other surface on the screen already covers the same intent.
 * On BusyDay / ReadyForPickup the active orders are routed into triage
 * buckets so the empty hero would lie ("Nothing in progress yet"); on
 * QuietDay the focus hero's "Add a new order →" CTA + the FAB already
 * surface the same affordance, and the empty hero's "Add first order"
 * copy is wrong for a user who has delivered orders before.
 */
private val PIPELINE_EMPTY_HIDDEN_STATES = setOf(
    DashboardUiState.BusyDay,
    DashboardUiState.ReadyForPickup,
    DashboardUiState.QuietDay,
)

/**
 * Static FirstCustomer checklist: customer is created (step 1 ✅), the
 * first order is the next nudge (step 2 active), due-date and deposit
 * follow (steps 3–4 pending). When the first order is created the
 * dashboard transitions out of FirstCustomer and this card stops
 * rendering — no need to recompute "done" status from inside this state.
 */
/**
 * Builds the 4-row "Order setup" checklist from the live first-order state.
 * Customer is always Done by the time this is rendered (the checklist is
 * gated on having at least one customer). The first non-done step becomes
 * Active, the rest stay Pending — only the Active row is tappable.
 */
private fun firstOrderChecklistSteps(setup: FirstOrderSetupUi): List<SetupStep> {
    val orderStatus = if (setup.hasOrder) SetupStepStatus.Done else SetupStepStatus.Active
    val dueStatus = when {
        !setup.hasOrder -> SetupStepStatus.Pending
        setup.hasDueDate -> SetupStepStatus.Done
        else -> SetupStepStatus.Active
    }
    val depositStatus = when {
        !setup.hasOrder || !setup.hasDueDate -> SetupStepStatus.Pending
        setup.hasDeposit -> SetupStepStatus.Done
        else -> SetupStepStatus.Active
    }
    return listOf(
        SetupStep(SetupStepKey.CustomerCreated, 1, SetupStepStatus.Done),
        SetupStep(SetupStepKey.AddFirstOrder, 2, orderStatus),
        SetupStep(SetupStepKey.SetDueDate, 3, dueStatus),
        SetupStep(SetupStepKey.RecordDeposit, 4, depositStatus),
    )
}

@Composable
fun DashboardRoot(
    onNavigateToOrderDetail: (String) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToOrderForm: () -> Unit,
    onNavigateToEditOrder: (String) -> Unit,
    onNavigateToCustomerForm: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToGoalSetup: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddCustomerFirst: () -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateToDraftMessage: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
    whatsAppLauncher: WhatsAppLauncher = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val signature = state.businessName ?: state.firstName

    ObserveAsEvents(viewModel.events) { event ->
        handleDashboardEvent(
            event = event,
            scope = scope,
            snackbarHostState = snackbarHostState,
            signature = signature,
            whatsAppLauncher = whatsAppLauncher,
            onNavigateToOrderDetail = onNavigateToOrderDetail,
            onNavigateToOrders = onNavigateToOrders,
            onNavigateToOrderForm = onNavigateToOrderForm,
            onNavigateToEditOrder = onNavigateToEditOrder,
            onNavigateToCustomerForm = onNavigateToCustomerForm,
            onNavigateToCustomers = onNavigateToCustomers,
            onNavigateToGoalSetup = onNavigateToGoalSetup,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAddCustomerFirst = onNavigateToAddCustomerFirst,
            onNavigateToCustomerDetail = onNavigateToCustomerDetail,
            onNavigateToDraftMessage = onNavigateToDraftMessage,
        )
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

@Suppress("LongParameterList")
private fun handleDashboardEvent(
    event: DashboardEvent,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    signature: String,
    whatsAppLauncher: WhatsAppLauncher,
    onNavigateToOrderDetail: (String) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToOrderForm: () -> Unit,
    onNavigateToEditOrder: (String) -> Unit,
    onNavigateToCustomerForm: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToGoalSetup: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddCustomerFirst: () -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onNavigateToDraftMessage: () -> Unit,
) {
    when (event) {
        is DashboardEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
        DashboardEvent.NavigateToOrders -> onNavigateToOrders()
        DashboardEvent.NavigateToOrderForm -> onNavigateToOrderForm()
        is DashboardEvent.NavigateToEditOrder -> onNavigateToEditOrder(event.orderId)
        DashboardEvent.NavigateToCustomerForm -> onNavigateToCustomerForm()
        DashboardEvent.NavigateToCustomers -> onNavigateToCustomers()
        DashboardEvent.NavigateToGoalSetup -> onNavigateToGoalSetup()
        DashboardEvent.NavigateToSettings -> onNavigateToSettings()
        DashboardEvent.NavigateToAddCustomerFirst -> onNavigateToAddCustomerFirst()
        is DashboardEvent.NavigateToCustomerDetail -> onNavigateToCustomerDetail(event.customerId)
        DashboardEvent.NavigateToDraftMessage -> onNavigateToDraftMessage()
        is DashboardEvent.LaunchWhatsApp -> launchWhatsAppForAction(
            scope,
            snackbarHostState,
            whatsAppLauncher,
            event,
            signature
        )
        is DashboardEvent.LaunchWhatsAppForReconnect -> launchWhatsAppForReconnect(
            scope,
            snackbarHostState,
            whatsAppLauncher,
            event
        )
    }
}

private fun launchWhatsAppForAction(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    whatsAppLauncher: WhatsAppLauncher,
    event: DashboardEvent.LaunchWhatsApp,
    signature: String,
) {
    scope.launch {
        val message = buildWhatsAppMessage(event.action, signature)
        val launched = whatsAppLauncher.launch(event.action.customerPhone, message)
        if (!launched) {
            snackbarHostState.showSnackbar(
                getString(Res.string.dashboard_whatsapp_launch_failed)
            )
        }
    }
}

private fun launchWhatsAppForReconnect(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    whatsAppLauncher: WhatsAppLauncher,
    event: DashboardEvent.LaunchWhatsAppForReconnect,
) {
    scope.launch {
        val message = getString(
            Res.string.reconnect_whatsapp_template,
            firstNameOf(event.candidate.customerName)
                .ifBlank { event.candidate.customerName }
        )
        val launched = whatsAppLauncher.launch(event.candidate.customerPhone, message)
        if (!launched) {
            snackbarHostState.showSnackbar(
                getString(Res.string.dashboard_whatsapp_launch_failed)
            )
        }
    }
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (DashboardAction) -> Unit
) {
    // FAB is hidden during the brand-new and loading states. Brand-new has no orders and the
    // Order form requires an existing customer; surfacing the FAB there would route the user
    // into a dead end. Loading is suppressed too so the FAB doesn't briefly flash before the
    // first state emission resolves.
    val showFab = state.uiState != DashboardUiState.BrandNew &&
        state.uiState != DashboardUiState.Loading
    Scaffold(
        floatingActionButton = {
            if (showFab) {
                StitchPadFab(
                    onClick = { onAction(DashboardAction.OnNewOrderClick) },
                    contentDescription = stringResource(Res.string.dashboard_fab_cd)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (state.uiState) {
            DashboardUiState.Loading -> LoadingState(modifier = contentModifier)
            DashboardUiState.BrandNew,
            DashboardUiState.FirstCustomer,
            DashboardUiState.QuietDay,
            DashboardUiState.PipelineSteady,
            DashboardUiState.NbaActive,
            DashboardUiState.BusyDay,
            DashboardUiState.ReadyForPickup -> DashboardContent(
                state = state,
                onAction = onAction,
                modifier = contentModifier,
                bottomPadding = if (showFab) FAB_BOTTOM_PADDING else NO_FAB_BOTTOM_PADDING,
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.dashboard_loading_cd)
    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        LoadingDots()
    }
}

// Layout orchestrator — section visibility branches read top-to-bottom in
// the same order the user sees them on screen. Extracting each into its own
// composable would scatter that ordering across the file without making any
// single branch easier to reason about.
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun DashboardContent(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = FAB_BOTTOM_PADDING,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
    ) {
        Spacer(Modifier.height(DesignTokens.space4))

        // 1. Header
        DashboardHeader(
            firstName = state.firstName,
            greeting = state.greeting,
            todayDate = state.todayDate,
            onAvatarClick = { onAction(DashboardAction.OnSettingsClick) },
        )

        // 2. Illustrated focus card (null headline means no card to show)
        val focusTitle = state.focusHeadline?.asString()
        if (focusTitle != null) {
            IllustratedFocusCard(
                variant = state.focusVariant,
                title = focusTitle,
                supporting = state.focusSupporting?.asString(),
                ctaLabel = state.focusCtaLabel?.asString(),
                ctaSubtitle = state.focusCtaSubtitle?.asString(),
                sectionLabel = state.focusSectionLabel?.asString(),
                onClick = { onAction(DashboardAction.OnFocusCtaClick) },
            )
        }

        // BrandNew adds a 3-step onboarding tile grid above the standard
        // sections. The other six states skip this insert. The remaining
        // sections (weekly goal, work pipeline, etc.) still render so the
        // operator sees the empty-state guidance for each.
        if (state.uiState == DashboardUiState.BrandNew) {
            OnboardingStepsCard(
                onAddCustomerClick = { onAction(DashboardAction.OnNewCustomerClick) },
                onSaveMeasurementsClick = { onAction(DashboardAction.OnAddMeasurementClick) },
                onCreateOrderClick = { onAction(DashboardAction.OnCreateOrderClick) },
            )
        }

        // "Order setup" persists across whatever uiState the dashboard
        // resolves to, until the first order has both a due date AND a
        // deposit. Step states reflect live data, so the user sees real
        // progress (1 of 4 → 2 of 4 → 3 of 4) as they fill things in.
        val firstOrderSetup = state.firstOrderSetup
        if (firstOrderSetup != null) {
            SetupChecklistCard(
                steps = firstOrderChecklistSteps(firstOrderSetup),
                onActiveStepClick = { key ->
                    when (key) {
                        SetupStepKey.AddFirstOrder ->
                            onAction(DashboardAction.OnSetupChecklistAdvance)
                        // Set due date + Record deposit both live on the
                        // Edit Order form, not the read-only Order Detail
                        // screen — route there instead so the field the
                        // user wants to fill is the field they land on.
                        SetupStepKey.SetDueDate, SetupStepKey.RecordDeposit ->
                            firstOrderSetup.orderId?.let { id ->
                                onAction(DashboardAction.OnSetupOrderEditClick(id))
                            }
                        SetupStepKey.CustomerCreated -> Unit
                    }
                },
            )
        }
        if (state.uiState == DashboardUiState.FirstCustomer) {
            val readyCustomer = state.customerReady
            if (readyCustomer != null) {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                    Text(
                        text = stringResource(Res.string.customer_ready_section_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CustomerReadyCard(
                        customer = readyCustomer,
                        onCardClick = {
                            onAction(DashboardAction.OnCustomerReadyClick(readyCustomer.customerId))
                        },
                        onMessageClick = {
                            onAction(
                                DashboardAction.OnCustomerReadyMessageClick(readyCustomer.customerId)
                            )
                        },
                    )
                }
            }
        }

        // Section ordering for revenue-focused / urgent states. On
        // BusyDay / ReadyForPickup / NbaActive the user opens the dashboard
        // expecting to see *what to do next*, not *how the goal is going* —
        // so the NBA carousel is promoted above the Weekly Goal card. On
        // calmer states (PipelineSteady / QuietDay / FirstCustomer / BrandNew)
        // the Weekly Goal stays at the top of the body because motivation
        // matters more than triage when nothing is on fire.
        // First-order setup overrides NBA promotion: the OrderSetupActionCard
        // below is meant to REPLACE the NBA carousel for users still in
        // onboarding, so we don't want a promoted carousel above the goal
        // card AND a setup card below it surfacing the same incomplete order.
        val promoteNbaAboveGoal = state.uiState in PROMOTED_NBA_STATES &&
            state.nextBestActions.isNotEmpty() &&
            firstOrderSetup == null
        if (promoteNbaAboveGoal) {
            NextBestActionsSection(
                actions = state.nextBestActions,
                onAction = onAction
            )
        }

        // 3. Weekly goal card
        WeeklyGoalsSection(
            weeklyGoal = state.weeklyGoal,
            onClick = { onAction(DashboardAction.OnGoalsCardClick) },
        )

        // 4. Today's work — dedup any rows whose order already has an NBA
        //    surfaced on this dashboard. Showing the same Gose Wale order in
        //    Today's Work AND in the NBA card just below it triples the
        //    "Gose Wale" mentions on screen for no extra information.
        val nbaOrderIds = remember(state.nextBestActions) {
            state.nextBestActions.mapTo(mutableSetOf()) { it.orderId }
        }
        val todayWorkRows = remember(state.overdue, state.dueToday, state.ready, nbaOrderIds) {
            buildTodayWorkRows(state.overdue, state.dueToday, state.ready)
                .filter { it.orderId !in nbaOrderIds }
        }
        // ReadyForPickup intentionally suppresses TodayWorkCard — the focus
        // card already pins the top ready customer and the pipeline section
        // covers the rest. Without this guard the card always re-renders here
        // because state.ready is non-empty by definition in that state.
        if (todayWorkRows.isNotEmpty() && state.uiState != DashboardUiState.ReadyForPickup) {
            TodayWorkCard(
                rows = todayWorkRows,
                onRowClick = { id -> onAction(DashboardAction.OnOrderClick(id)) },
                onViewAllClick = { onAction(DashboardAction.OnViewAllOrdersClick) },
            )
        }

        // 5. The "next action" surface. Branches in priority order:
        //    a) During first-order onboarding once an order exists, show the
        //       compact OrderSetupActionCard pointing at the incomplete order
        //       — this REPLACES the NBA carousel so we're not surfacing the
        //       same "collect deposit" action twice in different visual
        //       languages.
        //    b) Otherwise, if NBAs exist *and* haven't already been promoted
        //       above the Weekly Goal, show the carousel here.
        //    c) Otherwise, show the "Nothing urgent right now" hero — but
        //       only in states where reconnecting makes sense. BrandNew and
        //       FirstCustomer have no past customers to reconnect with, so
        //       the empty card would be dead UI (and its CTA misleading) in
        //       those.
        val activeOrderId = firstOrderSetup?.orderId
        if (firstOrderSetup != null && firstOrderSetup.hasOrder && activeOrderId != null) {
            OrderSetupActionCard(
                setup = firstOrderSetup,
                // Routes to Edit Order (not Order Detail) so the user lands
                // on the form that actually edits the missing fields.
                onClick = { onAction(DashboardAction.OnSetupOrderEditClick(activeOrderId)) },
            )
        } else if (!promoteNbaAboveGoal && state.nextBestActions.isNotEmpty()) {
            NextBestActionsSection(
                actions = state.nextBestActions,
                onAction = onAction
            )
        } else if (
            state.nextBestActions.isEmpty() &&
            state.uiState in EMPTY_NBA_CARD_STATES
        ) {
            EmptyIllustrationCard(
                slot = EmptyIllustrationSlot.Nba,
                title = stringResource(Res.string.dashboard_nba_quiet_title),
                supporting = stringResource(Res.string.dashboard_nba_quiet_supporting),
                ctaLabel = stringResource(Res.string.dashboard_nba_quiet_cta),
                onCtaClick = { onAction(DashboardAction.OnViewReconnectClick) },
                ctaStyle = EmptyCardCtaStyle.OutlinedPill,
                largeIllustration = true,
            )
        }

        // 5b. Smart Suggestions section — gated on having at least one customer
        //     (BrandNew state = no customers → no point opening a picker with nobody in it).
        if (state.uiState != DashboardUiState.BrandNew &&
            state.uiState != DashboardUiState.Loading
        ) {
            SmartSectionCard(
                // V1: dashboard free-tier chip hidden; the in-screen counter on
                // DraftMessageScreen is the V1 quota surface. Wire this to a
                // real value in V1.5 when the premium entitlement flow ships.
                remainingFreeQuota = null,
                onDraftMessageClick = { onAction(DashboardAction.OnDraftMessageClick) },
            )
        }

        // 6. Work pipeline — self-handles empty + populated states. Hidden
        //    during first-order onboarding because the OrderSetupActionCard
        //    above already surfaces that single order; the empty hero would
        //    just repeat "Add first order" which the focus card and Setup
        //    Checklist are already saying. Pipeline returns once
        //    `firstOrderSetup` flips to null (setup complete or 2+ orders).
        //
        //    Also hidden on BusyDay / ReadyForPickup / QuietDay when the
        //    pipeline is empty:
        //     - BusyDay / ReadyForPickup: every active order is already
        //       surfaced in Today's Work + the urgent NBA card; the empty
        //       hero would falsely claim "Nothing in progress yet".
        //     - QuietDay: hero already says "Add a new order →" and the
        //       FAB does the same; a third "Add first order" CTA is noise,
        //       and the "first" copy is wrong for users who have already
        //       delivered orders.
        val pipelineEmpty = state.pipelineInProgressTotal + state.pipelinePendingTotal == 0
        val hidePipelineWhenEmpty = pipelineEmpty && state.uiState in PIPELINE_EMPTY_HIDDEN_STATES
        if (firstOrderSetup == null && !hidePipelineWhenEmpty) {
            PipelineSection(
                inProgress = state.pipelineInProgress,
                inProgressTotal = state.pipelineInProgressTotal,
                notStarted = state.pipelinePending,
                notStartedTotal = state.pipelinePendingTotal,
                onRowClick = { id -> onAction(DashboardAction.OnOrderClick(id)) },
                onCreateOrderClick = { onAction(DashboardAction.OnCreateOrderClick) },
            )
        }

        // 7. Reconnect hero carousel — paginated up to 3 candidates with full count
        //    surfaced via the header pill. Hides itself when no candidates.
        ReconnectHeroSection(
            candidates = state.reconnectCandidates,
            onCardClick = { id -> onAction(DashboardAction.OnReconnectViewCustomerClick(id)) },
            onMessageClick = { id -> onAction(DashboardAction.OnReconnectClick(id)) },
            onViewAllClick = { onAction(DashboardAction.OnViewReconnectClick) },
        )
    }
}

@Composable
private fun WeeklyGoalsSection(
    weeklyGoal: WeeklyGoalUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardState: WeeklyGoalsCardState = when {
        weeklyGoal == null -> emptyWeeklyGoalsState()
        weeklyGoal.targetAmount > 0 &&
            weeklyGoal.collectedAmount >= weeklyGoal.targetAmount ->
            achievedWeeklyGoalsState(weeklyGoal)
        else -> inProgressWeeklyGoalsState(weeklyGoal)
    }
    WeeklyGoalsCard(state = cardState, onClick = onClick, modifier = modifier)
}

@Composable
private fun emptyWeeklyGoalsState(): WeeklyGoalsCardState.Empty =
    WeeklyGoalsCardState.Empty(
        sectionLabel = stringResource(Res.string.goals_set_first_section),
        title = stringResource(Res.string.goals_set_first_label),
        supporting = stringResource(Res.string.goals_set_first_supporting),
        ctaLabel = stringResource(Res.string.goals_set_first_cta)
    )

@Composable
private fun achievedWeeklyGoalsState(weeklyGoal: WeeklyGoalUi): WeeklyGoalsCardState.Filled {
    val collectedAbbrev = stringResource(
        Res.string.currency_naira,
        formatAbbreviated(weeklyGoal.collectedAmount)
    )
    val targetAbbrev = stringResource(
        Res.string.currency_naira,
        formatAbbreviated(weeklyGoal.targetAmount)
    )
    return WeeklyGoalsCardState.Filled(
        sectionLabel = stringResource(Res.string.goals_achieved_section_label),
        daysLeftLabel = daysLeftLabel(weeklyGoal.daysLeft),
        revenueLabel = stringResource(Res.string.goals_revenue_label),
        progressText = formatGoalProgress(weeklyGoal),
        progressPercent = weeklyGoal.progressPercent,
        achievedCtaLabel = stringResource(Res.string.goals_achieved_cta),
        achievedAmountLabel = collectedAbbrev,
        achievedTargetLabel = targetAbbrev,
    )
}

@Composable
private fun inProgressWeeklyGoalsState(weeklyGoal: WeeklyGoalUi): WeeklyGoalsCardState.Filled {
    val percentInt = (weeklyGoal.progressPercent * 100f).toInt().coerceIn(0, 999)
    val toGoAmount = (weeklyGoal.targetAmount - weeklyGoal.collectedAmount).coerceAtLeast(0.0)
    val earned = stringResource(
        Res.string.currency_naira,
        formatNaira(weeklyGoal.collectedAmount)
    )
    val toGo = stringResource(
        Res.string.currency_naira,
        formatNaira(toGoAmount)
    )
    val motivationRes = when (weeklyGoal.pace) {
        WeeklyGoalPace.Behind -> Res.string.goals_motivation_behind
        WeeklyGoalPace.OnPace -> Res.string.goals_motivation_on_pace
        WeeklyGoalPace.Ahead -> Res.string.goals_motivation_ahead
        WeeklyGoalPace.NearGoal -> Res.string.goals_motivation_near_goal
    }
    return WeeklyGoalsCardState.Filled(
        sectionLabel = stringResource(Res.string.goals_section_label),
        daysLeftLabel = daysLeftLabel(weeklyGoal.daysLeft),
        revenueLabel = stringResource(Res.string.goals_revenue_label),
        progressText = formatGoalProgress(weeklyGoal),
        progressPercent = weeklyGoal.progressPercent,
        supporting = stringResource(Res.string.goals_supporting),
        motivationLabel = stringResource(motivationRes),
        progressPercentLabel = stringResource(Res.string.goals_percent_of_goal, "$percentInt%"),
        earnedLabel = stringResource(Res.string.goals_earned, earned),
        toGoLabel = stringResource(Res.string.goals_to_go, toGo),
    )
}

@Composable
private fun daysLeftLabel(daysLeft: Int): String =
    if (daysLeft == 1) {
        stringResource(Res.string.goals_days_left_one, daysLeft)
    } else {
        stringResource(Res.string.goals_days_left, daysLeft)
    }

@Composable
private fun formatGoalProgress(goal: WeeklyGoalUi): String {
    val collected = stringResource(
        Res.string.currency_naira,
        formatAbbreviated(goal.collectedAmount)
    )
    val target = stringResource(
        Res.string.currency_naira,
        formatAbbreviated(goal.targetAmount)
    )
    return "$collected / $target"
}

/**
 * Horizontal carousel for Next Best Actions. Each card spans the full
 * LazyRow width via `fillParentMaxWidth()` so the financial stake on the
 * trailing metric reads at a glance, with horizontal swipe to peek the
 * next action.
 *
 * TODO: When `actions.size > 3`, cap the visible list to 3 and surface a
 * "View all" affordance (matching the ReconnectHeroSection 3-dot pattern)
 * that opens a dedicated full-screen NBAs list.
 */
@Composable
private fun NextBestActionsSection(
    actions: List<NextBestAction>,
    onAction: (DashboardAction) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        NextBestActionsHeader()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            contentPadding = PaddingValues(horizontal = DesignTokens.space4),
        ) {
            items(actions, key = { it.id }) { action ->
                NbaCard(
                    action = action,
                    onClick = { onAction(DashboardAction.OnNextActionPrimaryClick(action)) },
                    onOpenOrderClick = { orderId ->
                        onAction(DashboardAction.OnOrderClick(orderId))
                    },
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NextBestActionsHeader() {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier.padding(horizontal = DesignTokens.space4),
    ) {
        Box(
            modifier = Modifier
                .size(NBA_HEADER_BADGE_SIZE)
                .background(color = scheme.primary.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.dashboard_section_next_actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.dashboard_section_next_actions_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private val NBA_HEADER_BADGE_SIZE = 52.dp

@Composable
private fun NbaCard(
    action: NextBestAction,
    onClick: () -> Unit,
    onOpenOrderClick: (orderId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = nbaPresentationFor(action)
    NextBestActionCard(
        accent = presentation.accent,
        accentBackground = presentation.accentBackground,
        typeIcon = presentation.typeIcon,
        ctaIcon = presentation.ctaIcon,
        typeLabel = presentation.typeLabel,
        customerName = action.customerName,
        primaryLine = presentation.primaryLine,
        secondaryLine = presentation.secondaryLine,
        ctaLabel = presentation.ctaLabel,
        contentDescription = stringResource(
            Res.string.dashboard_nba_card_cd,
            presentation.primaryLine,
            presentation.secondaryLine,
            presentation.ctaLabel
        ),
        onClick = onClick,
        secondaryActionLabel = presentation.secondaryActionLabel,
        onSecondaryClick = if (presentation.secondaryActionLabel != null) {
            { onOpenOrderClick(action.orderId) }
        } else {
            null
        },
        modifier = modifier,
    )
}

private data class NbaPresentation(
    val accent: Color,
    val accentBackground: Color,
    /** Icon shown inside the top type pill (next to e.g. "OVERDUE"). */
    val typeIcon: ImageVector,
    /**
     * Icon shown inside the CTA pill (next to e.g. "Send reminder"). Often
     * differs from [typeIcon]: the type pill conveys *what status* the order
     * is in, while the CTA icon conveys *what the user is about to do*.
     */
    val ctaIcon: ImageVector,
    val typeLabel: String,
    val primaryLine: String,
    val secondaryLine: String,
    val ctaLabel: String,
    /**
     * When non-null, renders an "Open order →" style text link on the right
     * of the CTA pill row. Set for money-collecting NBAs where the primary
     * CTA opens WhatsApp / payment, so the user still has a one-tap escape
     * to the order detail. Operational NBAs (FinishStale / DeliverStale /
     * StartSoon) leave it null because their primary CTA already opens the
     * order — a duplicate secondary would be noise.
     */
    val secondaryActionLabel: String? = null,
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun nbaPresentationFor(action: NextBestAction): NbaPresentation {
    val isDark = LocalIsDarkTheme.current
    val amount = formatNaira(action.balanceAmount)
    return when (action.type) {
        NextBestActionType.CollectOverdue -> NbaPresentation(
            accent = if (isDark) DesignTokens.errorDarkText else DesignTokens.error500,
            accentBackground = if (isDark) DesignTokens.errorDarkBg else DesignTokens.error50,
            typeIcon = Icons.Default.Error,
            ctaIcon = Icons.Default.Notifications,
            typeLabel = "Overdue",
            primaryLine = stringResource(
                Res.string.dashboard_nba_collect_overdue_title,
                amount,
                action.customerName
            ),
            secondaryLine = if (action.daysCount == 1) {
                stringResource(
                    Res.string.dashboard_nba_collect_overdue_sub_one,
                    action.garmentLabel
                )
            } else {
                stringResource(
                    Res.string.dashboard_nba_collect_overdue_sub,
                    action.garmentLabel,
                    action.daysCount
                )
            },
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_send_reminder),
            secondaryActionLabel = stringResource(Res.string.dashboard_nba_cta_open_order),
        )
        NextBestActionType.CollectOnReady -> NbaPresentation(
            accent = MaterialTheme.colorScheme.onPrimaryContainer,
            accentBackground = MaterialTheme.colorScheme.primaryContainer,
            typeIcon = Icons.Default.Payments,
            ctaIcon = Icons.Default.Notifications,
            typeLabel = "Ready · Unpaid",
            primaryLine = stringResource(
                Res.string.dashboard_nba_collect_ready_title,
                amount,
                action.customerName
            ),
            secondaryLine = when (action.daysCount) {
                0 -> stringResource(
                    Res.string.dashboard_nba_collect_ready_sub_today,
                    action.garmentLabel
                )
                1 -> stringResource(
                    Res.string.dashboard_nba_collect_ready_sub_one,
                    action.garmentLabel
                )
                else -> stringResource(
                    Res.string.dashboard_nba_collect_ready_sub,
                    action.garmentLabel,
                    action.daysCount
                )
            },
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_send_reminder),
            secondaryActionLabel = stringResource(Res.string.dashboard_nba_cta_open_order),
        )
        NextBestActionType.FinishStale -> NbaPresentation(
            accent = if (isDark) DesignTokens.warningDarkText else DesignTokens.warning500,
            accentBackground = if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50,
            typeIcon = Icons.Default.Edit,
            ctaIcon = Icons.Default.Edit,
            typeLabel = "In progress",
            primaryLine = stringResource(
                Res.string.dashboard_nba_finish_stale_title,
                firstNameOf(action.customerName).ifBlank { action.customerName },
                action.garmentLabel
            ),
            secondaryLine = stringResource(
                Res.string.dashboard_nba_finish_stale_sub,
                action.daysCount
            ),
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_view_order)
        )
        NextBestActionType.DeliverStale -> NbaPresentation(
            accent = if (isDark) DesignTokens.successDarkText else DesignTokens.success500,
            accentBackground = if (isDark) DesignTokens.successDarkBg else DesignTokens.success50,
            typeIcon = Icons.Default.CheckCircle,
            ctaIcon = Icons.Default.CheckCircle,
            typeLabel = "Ready to deliver",
            primaryLine = stringResource(
                Res.string.dashboard_nba_deliver_stale_title,
                action.customerName
            ),
            secondaryLine = stringResource(
                Res.string.dashboard_nba_deliver_stale_sub,
                action.garmentLabel,
                action.daysCount
            ),
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_view_order)
        )
        NextBestActionType.CollectDeposit -> NbaPresentation(
            // Adire-aligned: deposit asks aren't a "status" the way overdue
            // (red) or ready (green) are — they're a primary brand action, so
            // they take primary tones rather than the info/blue palette. Use
            // onPrimaryContainer for accent text/icon to match the on-container
            // pattern used by the error/warning/success branches.
            accent = MaterialTheme.colorScheme.onPrimaryContainer,
            accentBackground = MaterialTheme.colorScheme.primaryContainer,
            typeIcon = Icons.Default.Payments,
            ctaIcon = Icons.Default.Payments,
            typeLabel = "No deposit",
            primaryLine = stringResource(
                Res.string.dashboard_nba_collect_deposit_title,
                action.customerName
            ),
            secondaryLine = stringResource(
                Res.string.dashboard_nba_collect_deposit_sub,
                action.garmentLabel,
                amount
            ),
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_collect_deposit),
            secondaryActionLabel = stringResource(Res.string.dashboard_nba_cta_open_order),
        )
        NextBestActionType.StartSoon -> NbaPresentation(
            accent = if (isDark) DesignTokens.warningDarkText else DesignTokens.warning500,
            accentBackground = if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50,
            typeIcon = Icons.Default.Today,
            ctaIcon = Icons.Default.Today,
            typeLabel = "Starting soon",
            primaryLine = stringResource(
                Res.string.dashboard_nba_start_soon_title,
                firstNameOf(action.customerName).ifBlank { action.customerName },
                action.garmentLabel
            ),
            secondaryLine = if (action.daysCount == 0) {
                stringResource(Res.string.dashboard_nba_start_soon_sub_today)
            } else {
                stringResource(Res.string.dashboard_nba_start_soon_sub, action.daysCount)
            },
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_view_order)
        )
    }
}

@Composable
private fun DashboardHeader(
    firstName: String,
    greeting: Greeting,
    todayDate: LocalDate?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = firstName.ifBlank { "?" }
    val greetingText = when (greeting) {
        Greeting.MORNING -> stringResource(Res.string.dashboard_greeting_morning, name)
        Greeting.AFTERNOON -> stringResource(Res.string.dashboard_greeting_afternoon, name)
        Greeting.EVENING -> stringResource(Res.string.dashboard_greeting_evening, name)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (todayDate != null) {
                Text(
                    text = todayDate.formatFriendly(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            BellButton(
                onClick = { /* notifications screen ships later */ },
                hasUnread = false,
            )
            UserAvatar(
                name = firstName,
                onClick = onAvatarClick,
            )
        }
    }
}

private suspend fun buildWhatsAppMessage(action: NextBestAction, signature: String): String {
    val firstName = firstNameOf(action.customerName).ifBlank { action.customerName }
    val amount = formatNaira(action.balanceAmount)
    return when (action.type) {
        NextBestActionType.CollectOnReady -> getString(
            Res.string.dashboard_whatsapp_collect_ready,
            firstName,
            action.garmentLabel,
            amount,
            signature
        )
        NextBestActionType.CollectOverdue -> getString(
            Res.string.dashboard_whatsapp_collect_overdue,
            firstName,
            action.garmentLabel,
            action.daysCount,
            amount,
            signature
        )
        else -> ""
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
private fun DashboardScreenLoadingPreview() {
    StitchPadTheme {
        DashboardScreen(state = DashboardState(uiState = DashboardUiState.Loading), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenFilledPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                uiState = DashboardUiState.BusyDay,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22),
                overdue = listOf(
                    DashboardOrderRow("1", "Fola Sunday", "Corset", daysLate = 4)
                ),
                dueToday = listOf(
                    DashboardOrderRow("2", "Bimbo Dann", "Dress")
                ),
                ready = listOf(
                    DashboardOrderRow("3", "Mr Tunde", "Senator")
                ),
                outstandingAmount = 480_000.0,
                outstandingOrderCount = 1,
                nextBestActions = listOf(
                    sampleNba(
                        type = NextBestActionType.CollectOverdue,
                        customer = "Mrs Adebayo",
                        garment = "Senator",
                        balance = 480_000.0,
                        days = 4
                    ),
                    sampleNba(
                        type = NextBestActionType.CollectOnReady,
                        customer = "Mr Tunde",
                        garment = "Senator",
                        balance = 120_000.0,
                        days = 2
                    ),
                    sampleNba(
                        type = NextBestActionType.CollectDeposit,
                        customer = "Mrs Ibe",
                        garment = "Blouse",
                        balance = 60_000.0,
                        days = 1
                    )
                ),
                pipelineInProgress = listOf(
                    DashboardOrderRow("p1", "Mr Femi", "Suit", daysUntilDeadline = 5),
                    DashboardOrderRow("p2", "Mrs Chika", "Bridal Gown", daysUntilDeadline = 12)
                ),
                pipelineInProgressTotal = 2,
                pipelinePending = listOf(
                    DashboardOrderRow("p3", "Mr Kola", "Agbada", daysUntilDeadline = 9)
                ),
                pipelinePendingTotal = 4
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenAllNbaTypesPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                uiState = DashboardUiState.NbaActive,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.AFTERNOON,
                todayDate = LocalDate(2026, 4, 22),
                nextBestActions = NextBestActionType.entries.map { type ->
                    sampleNba(
                        type = type,
                        customer = "Sample Customer",
                        garment = "Senator",
                        balance = 250_000.0,
                        days = 3
                    )
                }
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenPipelineOnlyPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                uiState = DashboardUiState.PipelineSteady,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22),
                pipelineInProgress = listOf(
                    DashboardOrderRow("p1", "Mrs Funke", "Senator", daysUntilDeadline = 6),
                    DashboardOrderRow("p2", "Mr Tope", "Suit", daysUntilDeadline = 10)
                ),
                pipelineInProgressTotal = 2,
                pipelinePending = listOf(
                    DashboardOrderRow("p3", "Mrs Chika", "Dress", daysUntilDeadline = 14),
                    DashboardOrderRow("p4", "Mr Kola", "Agbada"),
                    DashboardOrderRow("p5", "Bimbo D.", "Two Piece", daysUntilDeadline = 21)
                ),
                pipelinePendingTotal = 5
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
                uiState = DashboardUiState.BrandNew,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22)
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenReadyForPickupPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                uiState = DashboardUiState.ReadyForPickup,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22),
                ready = listOf(
                    DashboardOrderRow("r1", "Ade Yinka", "Senator")
                ),
                outstandingAmount = 60_000.0,
                outstandingOrderCount = 1,
                focusVariant = FocusVariant.Pickup,
                focusHeadline = com.danzucker.stitchpad.core.presentation.UiText.DynamicString(
                    "1 ready for pickup."
                ),
                focusSupporting = com.danzucker.stitchpad.core.presentation.UiText.DynamicString(
                    "Reach out about pickup or mark delivered."
                ),
                focusCtaLabel = com.danzucker.stitchpad.core.presentation.UiText.DynamicString(
                    "Open Ade Yinka"
                ),
                pipelineInProgress = listOf(
                    DashboardOrderRow("p1", "Mr Femi", "Suit", daysUntilDeadline = 5)
                ),
                pipelineInProgressTotal = 1
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenQuietDayPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                uiState = DashboardUiState.QuietDay,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.AFTERNOON,
                todayDate = LocalDate(2026, 4, 22)
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DashboardScreenEmptyNbaPreview() {
    StitchPadTheme {
        DashboardScreen(
            state = DashboardState(
                uiState = DashboardUiState.PipelineSteady,
                firstName = "Ade",
                businessName = "Ade's Fashions",
                greeting = Greeting.MORNING,
                todayDate = LocalDate(2026, 4, 22),
                // nextBestActions is empty — exercises EmptyIllustrationCard for the NBA section
                nextBestActions = emptyList(),
                pipelineInProgress = listOf(
                    DashboardOrderRow("p1", "Mr Femi", "Suit", daysUntilDeadline = 5),
                    DashboardOrderRow("p2", "Mrs Chika", "Bridal Gown", daysUntilDeadline = 12)
                ),
                pipelineInProgressTotal = 2,
                pipelinePending = listOf(
                    DashboardOrderRow("p3", "Mr Kola", "Agbada", daysUntilDeadline = 9)
                ),
                pipelinePendingTotal = 3
            ),
            onAction = {}
        )
    }
}

private fun sampleNba(
    type: NextBestActionType,
    customer: String,
    garment: String,
    balance: Double,
    days: Int
) = NextBestAction(
    type = type,
    orderId = "preview-${type.name}",
    customerId = "c-${type.name}",
    customerName = customer,
    customerPhone = "+2348012345678",
    garmentLabel = garment,
    balanceAmount = balance,
    daysCount = days
)

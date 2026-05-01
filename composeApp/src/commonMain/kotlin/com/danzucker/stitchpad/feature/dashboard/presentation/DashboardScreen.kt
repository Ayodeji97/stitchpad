@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
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
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyIllustrationCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyIllustrationSlot
import com.danzucker.stitchpad.feature.dashboard.presentation.components.IllustratedFocusCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.OnboardingStepsCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.PipelineSection
import com.danzucker.stitchpad.feature.dashboard.presentation.components.ReconnectChipStrip
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupChecklistCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStep
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStepKey
import com.danzucker.stitchpad.feature.dashboard.presentation.components.SetupStepStatus
import com.danzucker.stitchpad.feature.dashboard.presentation.components.TodayWorkCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.UserAvatar
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.buildTodayWorkRows
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.NextBestActionCard
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.components.WeeklyGoalsCard
import com.danzucker.stitchpad.ui.components.WeeklyGoalsCardState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.currency_naira
import stitchpad.composeapp.generated.resources.dashboard_fab_cd
import stitchpad.composeapp.generated.resources.dashboard_greeting_afternoon
import stitchpad.composeapp.generated.resources.dashboard_greeting_evening
import stitchpad.composeapp.generated.resources.dashboard_greeting_morning
import stitchpad.composeapp.generated.resources.dashboard_loading_cd
import stitchpad.composeapp.generated.resources.dashboard_nba_card_cd
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_deposit_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_deposit_title
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_overdue_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_overdue_title
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_ready_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_collect_ready_title
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_collect_deposit
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_send_reminder
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_view_order
import stitchpad.composeapp.generated.resources.dashboard_nba_deliver_stale_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_deliver_stale_title
import stitchpad.composeapp.generated.resources.dashboard_nba_empty_supporting
import stitchpad.composeapp.generated.resources.dashboard_nba_empty_title
import stitchpad.composeapp.generated.resources.dashboard_nba_finish_stale_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_finish_stale_title
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_sub_today
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_title
import stitchpad.composeapp.generated.resources.dashboard_section_next_actions
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_collect_overdue
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_collect_ready
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_launch_failed
import stitchpad.composeapp.generated.resources.goals_achieved_cta
import stitchpad.composeapp.generated.resources.goals_achieved_section_label
import stitchpad.composeapp.generated.resources.goals_days_left
import stitchpad.composeapp.generated.resources.goals_revenue_label
import stitchpad.composeapp.generated.resources.goals_section_label
import stitchpad.composeapp.generated.resources.goals_set_first_cta
import stitchpad.composeapp.generated.resources.goals_set_first_label
import stitchpad.composeapp.generated.resources.reconnect_whatsapp_template
import kotlin.math.roundToLong

private const val THOUSANDS = 1_000L
private const val MILLIONS = 1_000_000L

/**
 * Static FirstCustomer checklist: customer is created (step 1 ✅), the
 * first order is the next nudge (step 2 active), due-date and deposit
 * follow (steps 3–4 pending). When the first order is created the
 * dashboard transitions out of FirstCustomer and this card stops
 * rendering — no need to recompute "done" status from inside this state.
 */
private fun firstCustomerChecklistSteps(): List<SetupStep> = listOf(
    SetupStep(SetupStepKey.CustomerCreated, 1, SetupStepStatus.Done),
    SetupStep(SetupStepKey.AddFirstOrder, 2, SetupStepStatus.Active),
    SetupStep(SetupStepKey.SetDueDate, 3, SetupStepStatus.Pending),
    SetupStep(SetupStepKey.RecordDeposit, 4, SetupStepStatus.Pending),
)

@Composable
fun DashboardRoot(
    onNavigateToOrderDetail: (String) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToOrderForm: () -> Unit,
    onNavigateToCustomerForm: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToGoalSetup: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddCustomerFirst: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
    whatsAppLauncher: WhatsAppLauncher = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val signature = state.businessName ?: state.firstName

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DashboardEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
            DashboardEvent.NavigateToOrders -> onNavigateToOrders()
            DashboardEvent.NavigateToOrderForm -> onNavigateToOrderForm()
            DashboardEvent.NavigateToCustomerForm -> onNavigateToCustomerForm()
            DashboardEvent.NavigateToCustomers -> onNavigateToCustomers()
            DashboardEvent.NavigateToGoalSetup -> onNavigateToGoalSetup()
            DashboardEvent.NavigateToSettings -> onNavigateToSettings()
            DashboardEvent.NavigateToAddCustomerFirst -> onNavigateToAddCustomerFirst()
            is DashboardEvent.LaunchWhatsApp -> {
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
            is DashboardEvent.LaunchWhatsAppForReconnect -> {
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
                modifier = contentModifier
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

@Composable
private fun DashboardContent(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = 96.dp),
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

        // FirstCustomer (1+ customers, 0 orders) gets the 4-step setup
        // checklist between the hero and the rest of the dashboard. Step 1
        // is done; step 2 is active and tappable; steps 3-4 are pending.
        // Once the first order is created, the state flips out of
        // FirstCustomer and the checklist disappears.
        if (state.uiState == DashboardUiState.FirstCustomer) {
            SetupChecklistCard(
                steps = firstCustomerChecklistSteps(),
                onActiveStepClick = { _ -> onAction(DashboardAction.OnSetupChecklistAdvance) },
            )
        }

        // 3. Weekly goal card
        WeeklyGoalsSection(
            weeklyGoal = state.weeklyGoal,
            onClick = { onAction(DashboardAction.OnGoalsCardClick) },
        )

        // 4. Today's work
        val todayWorkRows = remember(state.overdue, state.dueToday, state.ready) {
            buildTodayWorkRows(state.overdue, state.dueToday, state.ready)
        }
        if (todayWorkRows.isNotEmpty()) {
            TodayWorkCard(
                rows = todayWorkRows,
                onRowClick = { id -> onAction(DashboardAction.OnOrderClick(id)) },
                onViewAllClick = { onAction(DashboardAction.OnViewAllOrdersClick) },
            )
        }

        // 5. Next best actions (horizontal carousel). Empty state shows EmptyIllustrationCard.
        if (state.nextBestActions.isNotEmpty()) {
            NextBestActionsSection(
                actions = state.nextBestActions,
                onAction = onAction
            )
        } else {
            EmptyIllustrationCard(
                slot = EmptyIllustrationSlot.Nba,
                title = stringResource(Res.string.dashboard_nba_empty_title),
                supporting = stringResource(Res.string.dashboard_nba_empty_supporting),
            )
        }

        // 6. Work pipeline — self-handles empty + populated states. The
        //    "Work pipeline" header sits above whichever inner state renders.
        PipelineSection(
            inProgress = state.pipelineInProgress,
            inProgressTotal = state.pipelineInProgressTotal,
            notStarted = state.pipelinePending,
            notStartedTotal = state.pipelinePendingTotal,
            onRowClick = { id -> onAction(DashboardAction.OnOrderClick(id)) },
        )

        // 7. Reconnect chip-strip (hides itself when candidates list is empty)
        ReconnectChipStrip(
            candidates = state.reconnectCandidates,
            onCandidateClick = { id -> onAction(DashboardAction.OnReconnectClick(id)) },
            onMoreClick = { onAction(DashboardAction.OnViewReconnectClick) },
        )
    }
}

@Composable
private fun WeeklyGoalsSection(
    weeklyGoal: WeeklyGoalUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardState: WeeklyGoalsCardState = if (weeklyGoal == null) {
        WeeklyGoalsCardState.Empty(
            label = stringResource(Res.string.goals_set_first_label),
            ctaLabel = stringResource(Res.string.goals_set_first_cta)
        )
    } else {
        val achieved = weeklyGoal.targetAmount > 0 &&
            weeklyGoal.collectedAmount >= weeklyGoal.targetAmount
        WeeklyGoalsCardState.Filled(
            sectionLabel = if (achieved) {
                stringResource(Res.string.goals_achieved_section_label)
            } else {
                stringResource(Res.string.goals_section_label)
            },
            daysLeftLabel = stringResource(Res.string.goals_days_left, weeklyGoal.daysLeft),
            revenueLabel = stringResource(Res.string.goals_revenue_label),
            progressText = formatGoalProgress(weeklyGoal),
            progressPercent = weeklyGoal.progressPercent,
            achievedCtaLabel = if (achieved) {
                stringResource(Res.string.goals_achieved_cta)
            } else {
                null
            }
        )
    }
    WeeklyGoalsCard(state = cardState, onClick = onClick, modifier = modifier)
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

/** Horizontal carousel for Next Best Actions. Only rendered when [actions] is non-empty. */
@Composable
private fun NextBestActionsSection(
    actions: List<NextBestAction>,
    onAction: (DashboardAction) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)
    ) {
        Text(
            text = stringResource(Res.string.dashboard_section_next_actions).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            contentPadding = PaddingValues(horizontal = DesignTokens.space4)
        ) {
            items(actions, key = { it.id }) { action ->
                NbaCard(
                    action = action,
                    onClick = { onAction(DashboardAction.OnNextActionPrimaryClick(action)) }
                )
            }
        }
    }
}

@Composable
private fun NbaCard(action: NextBestAction, onClick: () -> Unit) {
    val presentation = nbaPresentationFor(action)
    NextBestActionCard(
        accent = presentation.accent,
        accentBackground = presentation.accentBackground,
        icon = presentation.icon,
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
        onClick = onClick
    )
}

private data class NbaPresentation(
    val accent: Color,
    val accentBackground: Color,
    val icon: ImageVector,
    val typeLabel: String,
    val primaryLine: String,
    val secondaryLine: String,
    val ctaLabel: String
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun nbaPresentationFor(action: NextBestAction): NbaPresentation {
    val isDark = isSystemInDarkTheme()
    val amount = formatNaira(action.balanceAmount)
    return when (action.type) {
        NextBestActionType.CollectOverdue -> NbaPresentation(
            accent = if (isDark) DesignTokens.errorDarkText else DesignTokens.error500,
            accentBackground = if (isDark) DesignTokens.errorDarkBg else DesignTokens.error50,
            icon = Icons.Default.Error,
            typeLabel = "Overdue",
            primaryLine = stringResource(
                Res.string.dashboard_nba_collect_overdue_title,
                amount,
                action.customerName
            ),
            secondaryLine = stringResource(
                Res.string.dashboard_nba_collect_overdue_sub,
                action.garmentLabel,
                action.daysCount
            ),
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_send_reminder)
        )
        NextBestActionType.CollectOnReady -> NbaPresentation(
            accent = if (isDark) DesignTokens.primary400 else DesignTokens.primary600,
            accentBackground = if (isDark) DesignTokens.primary900 else DesignTokens.primary50,
            icon = Icons.Default.Payments,
            typeLabel = "Ready · Unpaid",
            primaryLine = stringResource(
                Res.string.dashboard_nba_collect_ready_title,
                amount,
                action.customerName
            ),
            secondaryLine = stringResource(
                Res.string.dashboard_nba_collect_ready_sub,
                action.garmentLabel,
                action.daysCount
            ),
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_send_reminder)
        )
        NextBestActionType.FinishStale -> NbaPresentation(
            accent = if (isDark) DesignTokens.warningDarkText else DesignTokens.warning500,
            accentBackground = if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50,
            icon = Icons.Default.Edit,
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
            icon = Icons.Default.CheckCircle,
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
            accent = if (isDark) DesignTokens.infoDarkText else DesignTokens.info500,
            accentBackground = if (isDark) DesignTokens.infoDarkBg else DesignTokens.info50,
            icon = Icons.Default.Payments,
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
            ctaLabel = stringResource(Res.string.dashboard_nba_cta_collect_deposit)
        )
        NextBestActionType.StartSoon -> NbaPresentation(
            accent = if (isDark) DesignTokens.warningDarkText else DesignTokens.warning500,
            accentBackground = if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50,
            icon = Icons.Default.Today,
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

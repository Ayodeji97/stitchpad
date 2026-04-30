@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.presentation.components.BellButton
import com.danzucker.stitchpad.feature.dashboard.presentation.components.UserAvatar
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import com.danzucker.stitchpad.ui.components.AccentedOrderRow
import com.danzucker.stitchpad.ui.components.FocusTodayCard
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.NextBestActionCard
import com.danzucker.stitchpad.ui.components.QuickStartTile
import com.danzucker.stitchpad.ui.components.QuickStartTiles
import com.danzucker.stitchpad.ui.components.SectionEmptyAffordance
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.components.Tile
import com.danzucker.stitchpad.ui.components.TileValueCurrency
import com.danzucker.stitchpad.ui.components.TileValueDefault
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
import stitchpad.composeapp.generated.resources.affordance_nba_empty_cta
import stitchpad.composeapp.generated.resources.affordance_nba_empty_supporting
import stitchpad.composeapp.generated.resources.affordance_nba_empty_title
import stitchpad.composeapp.generated.resources.affordance_pipeline_empty_cta
import stitchpad.composeapp.generated.resources.affordance_pipeline_empty_supporting
import stitchpad.composeapp.generated.resources.affordance_pipeline_empty_title
import stitchpad.composeapp.generated.resources.currency_naira
import stitchpad.composeapp.generated.resources.dashboard_chip_ready
import stitchpad.composeapp.generated.resources.dashboard_chip_today
import stitchpad.composeapp.generated.resources.dashboard_days_late
import stitchpad.composeapp.generated.resources.dashboard_due_in_days
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
import stitchpad.composeapp.generated.resources.dashboard_nba_finish_stale_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_finish_stale_title
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_sub
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_sub_today
import stitchpad.composeapp.generated.resources.dashboard_nba_start_soon_title
import stitchpad.composeapp.generated.resources.dashboard_pipeline_in_progress
import stitchpad.composeapp.generated.resources.dashboard_pipeline_pending
import stitchpad.composeapp.generated.resources.dashboard_section_next_actions
import stitchpad.composeapp.generated.resources.dashboard_section_pipeline
import stitchpad.composeapp.generated.resources.dashboard_section_todays_work
import stitchpad.composeapp.generated.resources.dashboard_tile_due_today
import stitchpad.composeapp.generated.resources.dashboard_tile_outstanding
import stitchpad.composeapp.generated.resources.dashboard_tile_overdue
import stitchpad.composeapp.generated.resources.dashboard_tile_ready
import stitchpad.composeapp.generated.resources.dashboard_welcome_cta
import stitchpad.composeapp.generated.resources.dashboard_welcome_subtitle
import stitchpad.composeapp.generated.resources.dashboard_welcome_title
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
import stitchpad.composeapp.generated.resources.quickstart_add_customer
import stitchpad.composeapp.generated.resources.quickstart_add_measurement
import stitchpad.composeapp.generated.resources.quickstart_create_order
import stitchpad.composeapp.generated.resources.reconnect_whatsapp_template
import kotlin.math.roundToLong

private const val THOUSANDS = 1_000L
private const val MILLIONS = 1_000_000L

@Composable
fun DashboardRoot(
    onNavigateToOrderDetail: (String) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToOrderForm: () -> Unit,
    onNavigateToCustomerForm: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToGoalSetup: () -> Unit,
    onNavigateToSettings: () -> Unit,
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
    // FAB is hidden during the brand-new and loading states. Brand-new shows the WelcomeHero
    // ("Add your first customer") and the Order form requires an existing customer; surfacing
    // the FAB there would route the user into a dead end. Loading is suppressed too so the FAB
    // doesn't briefly flash before the first state emission resolves.
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
            DashboardUiState.BrandNew -> WelcomeHero(
                onAddCustomerClick = { onAction(DashboardAction.OnNewCustomerClick) },
                modifier = contentModifier
            )
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
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space5),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = DesignTokens.space4, bottom = 96.dp)
    ) {
        DashboardHeader(
            firstName = state.firstName,
            greeting = state.greeting,
            todayDate = state.todayDate,
            onAvatarClick = { onAction(DashboardAction.OnSettingsClick) },
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )

        FocusTodayCardSection(
            state = state,
            onClick = { onAction(DashboardAction.OnFocusCtaClick) },
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )

        WeeklyGoalsSection(
            weeklyGoal = state.weeklyGoal,
            onClick = { onAction(DashboardAction.OnGoalsCardClick) },
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )

        ReconnectStripSection(
            uiState = state.uiState,
            candidates = state.reconnectCandidates,
            onAction = onAction
        )

        if (state.uiState == DashboardUiState.FirstCustomer) {
            QuickStartTiles(
                tiles = listOf(
                    QuickStartTile(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = stringResource(Res.string.quickstart_create_order),
                        onClick = { onAction(DashboardAction.OnNewOrderClick) }
                    ),
                    QuickStartTile(
                        icon = Icons.Filled.PersonAdd,
                        label = stringResource(Res.string.quickstart_add_customer),
                        onClick = { onAction(DashboardAction.OnNewCustomerClick) }
                    ),
                    QuickStartTile(
                        icon = Icons.Filled.Straighten,
                        label = stringResource(Res.string.quickstart_add_measurement),
                        onClick = { onAction(DashboardAction.OnAddMeasurementClick) }
                    )
                ),
                modifier = Modifier.padding(horizontal = DesignTokens.space4)
            )
        }

        TileGrid(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )
        TodaysWorkList(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )
        NextBestActionsSection(
            actions = state.nextBestActions,
            onAction = onAction
        )
        PipelineSection(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = DesignTokens.space4)
        )
    }
}

private data class FocusVariantStyle(
    val icon: ImageVector,
    val accent: Color,
    val brush: Brush?
)

@Suppress("CyclomaticComplexMethod")
@Composable
private fun focusVariantStyle(variant: FocusVariant): FocusVariantStyle {
    val isDark = isSystemInDarkTheme()
    return when (variant) {
        FocusVariant.FirstOrder -> FocusVariantStyle(
            icon = Icons.Filled.RocketLaunch,
            accent = if (isDark) DesignTokens.infoDarkText else DesignTokens.info500,
            brush = null
        )
        FocusVariant.Quiet -> FocusVariantStyle(
            icon = Icons.Filled.Spa,
            accent = if (isDark) DesignTokens.successDarkText else DesignTokens.success500,
            brush = null
        )
        FocusVariant.Steady -> FocusVariantStyle(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            accent = if (isDark) DesignTokens.infoDarkText else DesignTokens.info500,
            brush = null
        )
        FocusVariant.Earn -> FocusVariantStyle(
            icon = Icons.Filled.Savings,
            accent = if (isDark) DesignTokens.primary400 else DesignTokens.primary600,
            brush = Brush.linearGradient(
                colors = listOf(
                    if (isDark) DesignTokens.primary900 else DesignTokens.primary50,
                    (if (isDark) DesignTokens.primary900 else DesignTokens.primary50).copy(alpha = 0.5f),
                    Color.Transparent
                )
            )
        )
        FocusVariant.Focus -> FocusVariantStyle(
            icon = Icons.Filled.PriorityHigh,
            accent = if (isDark) DesignTokens.errorDarkText else DesignTokens.error500,
            brush = Brush.linearGradient(
                colors = listOf(
                    if (isDark) DesignTokens.errorDarkBg else DesignTokens.error50,
                    (if (isDark) DesignTokens.errorDarkBg else DesignTokens.error50).copy(alpha = 0.4f),
                    Color.Transparent
                )
            )
        )
        FocusVariant.Pickup -> FocusVariantStyle(
            icon = Icons.Filled.CheckCircle,
            accent = if (isDark) DesignTokens.successDarkText else DesignTokens.success500,
            brush = null
        )
    }
}

@Composable
private fun FocusTodayCardSection(
    state: DashboardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = focusVariantStyle(state.focusVariant)
    val headline = state.focusHeadline?.asString() ?: return
    FocusTodayCard(
        icon = style.icon,
        accentColor = style.accent,
        headline = headline,
        onClick = onClick,
        modifier = modifier,
        supporting = state.focusSupporting?.asString(),
        ctaLabel = state.focusCtaLabel?.asString(),
        containerBrush = style.brush
    )
}

@Composable
private fun ReconnectStripSection(
    uiState: DashboardUiState,
    candidates: List<com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate>,
    onAction: (DashboardAction) -> Unit
) {
    val style = when (uiState) {
        DashboardUiState.FirstCustomer, DashboardUiState.QuietDay -> ReconnectStripStyle.Cards
        DashboardUiState.PipelineSteady -> ReconnectStripStyle.Pills
        else -> return
    }
    ReconnectStrip(
        candidates = candidates,
        style = style,
        onCandidateClick = { onAction(DashboardAction.OnReconnectCandidateClick(it)) }
    )
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

/** Dashboard accent buckets driving foreground + background colour for tiles and rows. */
private enum class RowAccent { Overdue, DueToday, Ready, Pipeline }

private data class AccentColors(val fg: Color, val bg: Color)

@Composable
private fun accentColorsFor(accent: RowAccent): AccentColors {
    val isDark = isSystemInDarkTheme()
    return when (accent) {
        RowAccent.Overdue -> AccentColors(
            fg = if (isDark) DesignTokens.errorDarkText else DesignTokens.error500,
            bg = if (isDark) DesignTokens.errorDarkBg else DesignTokens.error50
        )
        RowAccent.DueToday -> AccentColors(
            fg = if (isDark) DesignTokens.warningDarkText else DesignTokens.warning500,
            bg = if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50
        )
        RowAccent.Ready -> AccentColors(
            fg = if (isDark) DesignTokens.successDarkText else DesignTokens.success500,
            bg = if (isDark) DesignTokens.successDarkBg else DesignTokens.success50
        )
        RowAccent.Pipeline -> AccentColors(
            fg = MaterialTheme.colorScheme.outline,
            bg = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

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
        if (actions.isEmpty()) {
            SectionEmptyAffordance(
                icon = Icons.Filled.Lightbulb,
                title = stringResource(Res.string.affordance_nba_empty_title),
                supporting = stringResource(Res.string.affordance_nba_empty_supporting),
                ctaLabel = stringResource(Res.string.affordance_nba_empty_cta),
                onClick = { onAction(DashboardAction.OnSeeAllClick) },
                modifier = Modifier.padding(horizontal = DesignTokens.space4)
            )
        } else {
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

private data class TileData(
    val icon: ImageVector,
    val valueText: String,
    val labelText: String,
    val accent: Color,
    val background: Color,
    val border: Color,
    val onClick: () -> Unit,
    val valueFontSize: TextUnit = TileValueDefault
)

@Composable
private fun TileGrid(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val surface = MaterialTheme.colorScheme.surface
    val surfaceBorder = MaterialTheme.colorScheme.outlineVariant

    val overdue = accentColorsFor(RowAccent.Overdue)
    val due = accentColorsFor(RowAccent.DueToday)
    val ready = accentColorsFor(RowAccent.Ready)
    val overdueBorder = overdue.fg.copy(alpha = if (isDark) 0.25f else 0.15f)
    val outstandingFg = if (isDark) DesignTokens.primary400 else DesignTokens.primary600

    val tiles = buildList {
        if (state.overdue.isNotEmpty()) {
            add(
                TileData(
                    icon = Icons.Default.Error,
                    valueText = state.overdue.size.toString(),
                    labelText = stringResource(Res.string.dashboard_tile_overdue),
                    accent = overdue.fg,
                    background = overdue.bg,
                    border = overdueBorder,
                    onClick = { onAction(DashboardAction.OnSeeAllClick) }
                )
            )
        }
        if (state.dueToday.isNotEmpty()) {
            add(
                TileData(
                    icon = Icons.Default.Today,
                    valueText = state.dueToday.size.toString(),
                    labelText = stringResource(Res.string.dashboard_tile_due_today),
                    accent = due.fg,
                    background = surface,
                    border = surfaceBorder,
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
                    accent = ready.fg,
                    background = surface,
                    border = surfaceBorder,
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
                    accent = outstandingFg,
                    background = surface,
                    border = surfaceBorder,
                    onClick = { onAction(DashboardAction.OnOutstandingClick) },
                    valueFontSize = TileValueCurrency
                )
            )
        }
    }

    if (tiles.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
    ) {
        tiles.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                pair.forEach { tile ->
                    Tile(
                        icon = tile.icon,
                        valueText = tile.valueText,
                        labelText = tile.labelText,
                        accent = tile.accent,
                        background = tile.background,
                        border = tile.border,
                        onClick = tile.onClick,
                        valueFontSize = tile.valueFontSize,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class ListItem(val row: DashboardOrderRow, val accent: RowAccent)

@Composable
private fun TodaysWorkList(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // In ReadyForPickup the FocusTodayCard already pins the top ready customer and the
    // READY tile + Orders list cover the rest — surfacing these rows again would stack
    // the same one or two orders three times in the visible viewport.
    if (state.uiState == DashboardUiState.ReadyForPickup) return
    val items = buildList {
        state.overdue.forEach { add(ListItem(it, RowAccent.Overdue)) }
        state.dueToday.forEach { add(ListItem(it, RowAccent.DueToday)) }
        state.ready.forEach { add(ListItem(it, RowAccent.Ready)) }
    }
    if (items.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
    ) {
        Text(
            text = stringResource(Res.string.dashboard_section_todays_work).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { item ->
            val colors = accentColorsFor(item.accent)
            val chipText = when (item.accent) {
                RowAccent.Overdue ->
                    item.row.daysLate
                        ?.let { stringResource(Res.string.dashboard_days_late, it) }
                        .orEmpty()
                RowAccent.DueToday -> stringResource(Res.string.dashboard_chip_today)
                RowAccent.Ready -> stringResource(Res.string.dashboard_chip_ready)
                RowAccent.Pipeline -> ""
            }
            AccentedOrderRow(
                customerName = item.row.customerName,
                primaryLabel = item.row.primaryLabel,
                accentColor = colors.fg,
                chipText = chipText,
                chipTextColor = colors.fg,
                chipBackground = colors.bg,
                onClick = { onAction(DashboardAction.OnOrderClick(item.row.orderId)) }
            )
        }
    }
}

@Composable
private fun PipelineSection(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val pipelineColors = accentColorsFor(RowAccent.Pipeline)
    val isEmpty = state.pipelineInProgressTotal == 0 && state.pipelinePendingTotal == 0
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier
    ) {
        Text(
            text = stringResource(Res.string.dashboard_section_pipeline).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isEmpty) {
            SectionEmptyAffordance(
                icon = Icons.Filled.Inbox,
                title = stringResource(Res.string.affordance_pipeline_empty_title),
                supporting = stringResource(Res.string.affordance_pipeline_empty_supporting),
                ctaLabel = stringResource(Res.string.affordance_pipeline_empty_cta),
                onClick = { onAction(DashboardAction.OnNewOrderClick) }
            )
        } else {
            if (state.pipelineInProgressTotal > 0) {
                PipelineSubsection(
                    title = stringResource(
                        Res.string.dashboard_pipeline_in_progress,
                        state.pipelineInProgressTotal
                    ),
                    rows = state.pipelineInProgress,
                    accentColors = pipelineColors,
                    onAction = onAction
                )
            }
            if (state.pipelinePendingTotal > 0) {
                PipelineSubsection(
                    title = stringResource(
                        Res.string.dashboard_pipeline_pending,
                        state.pipelinePendingTotal
                    ),
                    rows = state.pipelinePending,
                    accentColors = pipelineColors,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun PipelineSubsection(
    title: String,
    rows: List<DashboardOrderRow>,
    accentColors: AccentColors,
    onAction: (DashboardAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        rows.forEach { row ->
            val chipText = row.daysUntilDeadline
                ?.let { stringResource(Res.string.dashboard_due_in_days, it) }
                .orEmpty()
            AccentedOrderRow(
                customerName = row.customerName,
                primaryLabel = row.primaryLabel,
                accentColor = accentColors.fg,
                chipText = chipText,
                chipTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                chipBackground = accentColors.bg,
                onClick = { onAction(DashboardAction.OnOrderClick(row.orderId)) }
            )
        }
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
                .size(DesignTokens.iconXl)
                .clip(RoundedCornerShape(DesignTokens.radiusXl))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(DesignTokens.iconHero)
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

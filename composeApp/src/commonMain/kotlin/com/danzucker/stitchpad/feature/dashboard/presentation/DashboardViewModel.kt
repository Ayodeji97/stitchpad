package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.dashboard.domain.BucketCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.NbaCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.ReconnectCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.model.Buckets
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusResolution
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.focus_busy_cta
import stitchpad.composeapp.generated.resources.focus_busy_supporting
import stitchpad.composeapp.generated.resources.focus_busy_title
import stitchpad.composeapp.generated.resources.focus_earn_cta
import stitchpad.composeapp.generated.resources.focus_earn_supporting
import stitchpad.composeapp.generated.resources.focus_earn_title
import stitchpad.composeapp.generated.resources.focus_first_order_cta
import stitchpad.composeapp.generated.resources.focus_first_order_title
import stitchpad.composeapp.generated.resources.focus_pickup_cta
import stitchpad.composeapp.generated.resources.focus_pickup_supporting
import stitchpad.composeapp.generated.resources.focus_pickup_title
import stitchpad.composeapp.generated.resources.focus_quiet_cta
import stitchpad.composeapp.generated.resources.focus_quiet_cta_no_candidate
import stitchpad.composeapp.generated.resources.focus_quiet_supporting
import stitchpad.composeapp.generated.resources.focus_quiet_title
import stitchpad.composeapp.generated.resources.focus_steady_cta
import stitchpad.composeapp.generated.resources.focus_steady_supporting
import stitchpad.composeapp.generated.resources.focus_steady_title
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MORNING_CUTOFF_HOUR = 12
private const val AFTERNOON_CUTOFF_HOUR = 17

private const val DAYS_IN_WEEK = 7

@OptIn(ExperimentalTime::class)
@Suppress("TooManyFunctions")
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(DashboardState())

    private val _events = Channel<DashboardEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadData()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DashboardState()
        )

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.OnOrderClick -> emitEvent(
                DashboardEvent.NavigateToOrderDetail(action.orderId)
            )
            is DashboardAction.OnNextActionPrimaryClick -> emitEvent(
                if (action.action.opensWhatsApp) {
                    DashboardEvent.LaunchWhatsApp(action.action)
                } else {
                    DashboardEvent.NavigateToOrderDetail(action.action.orderId)
                }
            )
            DashboardAction.OnSeeAllClick -> emitEvent(DashboardEvent.NavigateToOrders)
            DashboardAction.OnOutstandingClick -> emitEvent(DashboardEvent.NavigateToOrders)
            DashboardAction.OnNewOrderClick -> emitEvent(DashboardEvent.NavigateToOrderForm)
            DashboardAction.OnNewCustomerClick -> emitEvent(DashboardEvent.NavigateToCustomerForm)
            DashboardAction.OnAddMeasurementClick -> emitEvent(DashboardEvent.NavigateToCustomers)
            DashboardAction.OnGoalsCardClick -> emitEvent(DashboardEvent.NavigateToGoalSetup)
            DashboardAction.OnFocusCtaClick -> handleFocusCtaClick()
            DashboardAction.OnSettingsClick -> emitEvent(DashboardEvent.NavigateToSettings)
            is DashboardAction.OnReconnectCandidateClick -> emitEvent(
                DashboardEvent.LaunchWhatsAppForReconnect(action.candidate)
            )
            DashboardAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleFocusCtaClick() {
        val current = _state.value
        when (current.focusVariant) {
            FocusVariant.FirstOrder -> emitEvent(DashboardEvent.NavigateToOrderForm)
            FocusVariant.Focus -> {
                val firstUrgentId = current.overdue.firstOrNull()?.orderId
                    ?: current.dueToday.firstOrNull()?.orderId
                    ?: current.ready.firstOrNull()?.orderId
                firstUrgentId?.let { emitEvent(DashboardEvent.NavigateToOrderDetail(it)) }
            }
            FocusVariant.Pickup -> {
                val firstReadyId = current.ready.firstOrNull()?.orderId
                firstReadyId?.let { emitEvent(DashboardEvent.NavigateToOrderDetail(it)) }
            }
            FocusVariant.Earn -> {
                val topNba = current.nextBestActions.firstOrNull() ?: return
                emitEvent(
                    if (topNba.opensWhatsApp) {
                        DashboardEvent.LaunchWhatsApp(topNba)
                    } else {
                        DashboardEvent.NavigateToOrderDetail(topNba.orderId)
                    }
                )
            }
            FocusVariant.Steady -> {
                val firstPipelineId = current.pipelineInProgress.firstOrNull()?.orderId
                    ?: current.pipelinePending.firstOrNull()?.orderId
                firstPipelineId?.let { emitEvent(DashboardEvent.NavigateToOrderDetail(it)) }
            }
            FocusVariant.Quiet -> {
                val reconnectCandidate = current.reconnectCandidates.firstOrNull()
                emitEvent(
                    if (reconnectCandidate != null) {
                        DashboardEvent.LaunchWhatsAppForReconnect(reconnectCandidate)
                    } else {
                        DashboardEvent.NavigateToOrderForm
                    }
                )
            }
        }
    }

    private fun emitEvent(event: DashboardEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    @Suppress("LongMethod")
    private fun loadData() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: run {
                _state.update { it.copy(uiState = DashboardUiState.BrandNew) }
                return@launch
            }
            val firstName = firstNameOf(user.displayName)
            val workshopName = user.businessName?.takeIf { it.isNotBlank() }

            combine(
                orderRepository.observeOrders(user.id),
                customerRepository.observeCustomers(user.id),
                weeklyGoalRepository.observeWeeklyGoal(user.id)
            ) { ordersResult, customersResult, goalResult ->
                Triple(ordersResult, customersResult, goalResult)
            }.collect { (ordersResult, customersResult, goalResult) ->
                val orders = (ordersResult as? Result.Success)?.data ?: emptyList()
                val customers = (customersResult as? Result.Success)?.data ?: emptyList()
                val goal = (goalResult as? Result.Success)?.data
                val error = when {
                    ordersResult is Result.Error -> ordersResult.error.toDashboardUiText()
                    customersResult is Result.Error -> customersResult.error.toDashboardUiText()
                    else -> null
                }
                // Recomputed on every emission so the greeting rolls morning -> afternoon -> evening
                // without recreating the ViewModel. A pure ticker would be more accurate but adds a
                // separate flow and a coroutine; emission-driven recompute is enough for this MVP
                // because data updates are frequent in the workshop flow.
                val greeting = computeGreeting()
                val today = Instant.fromEpochMilliseconds(nowMillis())
                    .toLocalDateTime(timeZone).date
                val customersById = customers.associateBy { it.id }
                val buckets = BucketCalculator.compute(orders, today, timeZone)
                val nextBestActions = NbaCalculator.compute(orders, customersById, today, timeZone)
                val uiState = resolveUiState(buckets, nextBestActions, orders, customers)
                val reconnect = ReconnectCalculator.compute(orders, customers, today, timeZone)
                val focus = resolveFocus(uiState, buckets, nextBestActions, customers, reconnect)
                val weeklyGoal = resolveWeeklyGoal(orders, today, goal)

                _state.update {
                    it.copy(
                        uiState = uiState,
                        firstName = firstName,
                        businessName = workshopName,
                        greeting = greeting,
                        todayDate = today,
                        overdue = buckets.overdue,
                        dueToday = buckets.dueToday,
                        ready = buckets.ready,
                        outstandingAmount = buckets.outstandingAmount,
                        outstandingOrderCount = buckets.outstandingOrderCount,
                        nextBestActions = nextBestActions,
                        pipelineInProgress = buckets.pipelineInProgress,
                        pipelineInProgressTotal = buckets.pipelineInProgressTotal,
                        pipelinePending = buckets.pipelinePending,
                        pipelinePendingTotal = buckets.pipelinePendingTotal,
                        focusVariant = focus.variant,
                        focusHeadline = focus.headline,
                        focusSupporting = focus.supporting,
                        focusCtaLabel = focus.ctaLabel,
                        reconnectCandidates = reconnect,
                        weeklyGoal = weeklyGoal,
                        errorMessage = error
                    )
                }
            }
        }
    }

    /**
     * Resolves the canonical screen-level state. Single source of priority truth —
     * everything else (focus variant, copy, CTA) derives from this.
     *
     * Priority (first match wins):
     *   BrandNew → FirstCustomer → BusyDay → ReadyForPickup → NbaActive → PipelineSteady → QuietDay
     *
     * `BusyDay` is reserved for genuine urgency (overdue or due-today). A day where
     * the only triage signal is ready-for-pickup is its own calmer state — see
     * [DashboardUiState.ReadyForPickup] for why.
     *
     * `Loading` is never returned here — that's set on initial state and cleared
     * once the first data emission arrives.
     */
    @Suppress("ReturnCount")
    private fun resolveUiState(
        buckets: Buckets,
        nextBestActions: List<NextBestAction>,
        orders: List<Order>,
        customers: List<Customer>
    ): DashboardUiState {
        if (orders.isEmpty() && customers.isEmpty()) return DashboardUiState.BrandNew
        if (orders.isEmpty()) return DashboardUiState.FirstCustomer
        if (buckets.overdue.isNotEmpty() || buckets.dueToday.isNotEmpty()) {
            return DashboardUiState.BusyDay
        }
        if (buckets.ready.isNotEmpty()) return DashboardUiState.ReadyForPickup
        if (nextBestActions.isNotEmpty()) return DashboardUiState.NbaActive
        val pipelineTotal = buckets.pipelineInProgressTotal + buckets.pipelinePendingTotal
        if (pipelineTotal > 0) return DashboardUiState.PipelineSteady
        return DashboardUiState.QuietDay
    }

    /**
     * Resolves the FocusTodayCard's variant + copy + CTA label for the current
     * [uiState]. Pivots on a sealed-type when so the compiler enforces handling
     * every state. For `Loading` and `BrandNew` returns a placeholder bundle that
     * the screen ignores — those states render LoadingDots / WelcomeHero instead.
     */
    @Suppress("LongMethod")
    private fun resolveFocus(
        uiState: DashboardUiState,
        buckets: Buckets,
        nextBestActions: List<NextBestAction>,
        customers: List<Customer>,
        reconnect: List<ReconnectCandidate>
    ): FocusResolution = when (uiState) {
        DashboardUiState.FirstCustomer -> {
            val firstCustomer = customers.first()
            FocusResolution(
                variant = FocusVariant.FirstOrder,
                headline = UiText.StringResourceText(Res.string.focus_first_order_title),
                supporting = null,
                ctaLabel = UiText.StringResourceText(
                    Res.string.focus_first_order_cta,
                    arrayOf(firstCustomer.name)
                )
            )
        }
        DashboardUiState.BusyDay -> {
            // Headline counts only what the supporting line counts (overdue + dueToday).
            // Ready orders show up in the READY tile + Today's Work green-stripe rows;
            // they do not inflate the urgency number. resolveUiState guarantees
            // overdue.isNotEmpty() || dueToday.isNotEmpty() here, so urgentCount >= 1.
            val urgentCount = buckets.overdue.size + buckets.dueToday.size
            val firstUrgent = buckets.overdue.firstOrNull()
                ?: buckets.dueToday.firstOrNull()
                ?: buckets.ready.firstOrNull()
            FocusResolution(
                variant = FocusVariant.Focus,
                headline = UiText.StringResourceText(
                    Res.string.focus_busy_title,
                    arrayOf(urgentCount)
                ),
                supporting = UiText.StringResourceText(
                    Res.string.focus_busy_supporting,
                    arrayOf(buckets.overdue.size, buckets.dueToday.size)
                ),
                ctaLabel = firstUrgent?.let {
                    UiText.StringResourceText(
                        Res.string.focus_busy_cta,
                        arrayOf(it.customerName)
                    )
                }
            )
        }
        DashboardUiState.ReadyForPickup -> {
            val firstReady = buckets.ready.first()
            FocusResolution(
                variant = FocusVariant.Pickup,
                headline = UiText.StringResourceText(
                    Res.string.focus_pickup_title,
                    arrayOf(buckets.ready.size)
                ),
                supporting = UiText.StringResourceText(Res.string.focus_pickup_supporting),
                ctaLabel = UiText.StringResourceText(
                    Res.string.focus_pickup_cta,
                    arrayOf(firstReady.customerName)
                )
            )
        }
        DashboardUiState.NbaActive -> {
            val topNba = nextBestActions.first()
            FocusResolution(
                variant = FocusVariant.Earn,
                headline = UiText.StringResourceText(
                    Res.string.focus_earn_title,
                    arrayOf(nextBestActions.size)
                ),
                supporting = UiText.StringResourceText(
                    Res.string.focus_earn_supporting,
                    arrayOf(topNba.customerName)
                ),
                ctaLabel = UiText.StringResourceText(Res.string.focus_earn_cta)
            )
        }
        DashboardUiState.PipelineSteady -> {
            val pipelineTotal = buckets.pipelineInProgressTotal + buckets.pipelinePendingTotal
            FocusResolution(
                variant = FocusVariant.Steady,
                headline = UiText.StringResourceText(Res.string.focus_steady_title),
                supporting = UiText.StringResourceText(
                    Res.string.focus_steady_supporting,
                    arrayOf(pipelineTotal)
                ),
                ctaLabel = UiText.StringResourceText(Res.string.focus_steady_cta)
            )
        }
        DashboardUiState.QuietDay -> {
            val topReconnect = reconnect.firstOrNull()
            FocusResolution(
                variant = FocusVariant.Quiet,
                headline = UiText.StringResourceText(Res.string.focus_quiet_title),
                supporting = UiText.StringResourceText(Res.string.focus_quiet_supporting),
                ctaLabel = if (topReconnect != null) {
                    UiText.StringResourceText(
                        Res.string.focus_quiet_cta,
                        arrayOf(topReconnect.customerName)
                    )
                } else {
                    UiText.StringResourceText(Res.string.focus_quiet_cta_no_candidate)
                }
            )
        }
        DashboardUiState.Loading, DashboardUiState.BrandNew -> FocusResolution(
            // Card not rendered in these states; values are placeholders.
            variant = FocusVariant.Quiet,
            headline = UiText.StringResourceText(Res.string.focus_quiet_title),
            supporting = null,
            ctaLabel = null
        )
    }

    /**
     * Builds the WeeklyGoalsCard's UI render model from the user's saved [goal] and
     * collected revenue derived from orders updated in the current ISO week
     * (Monday-Sunday). Returns `null` when the user has not set a goal yet — the
     * card renders its empty "Set your first goal" state in that case.
     */
    private fun resolveWeeklyGoal(
        orders: List<Order>,
        today: LocalDate,
        goal: WeeklyGoal?
    ): WeeklyGoalUi? {
        if (goal == null) return null
        val daysFromMonday = today.dayOfWeek.ordinal
        val weekStart = today.minus(daysFromMonday, DateTimeUnit.DAY)
        val weekStartMillis = weekStart.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val collected = orders
            .filter { it.updatedAt >= weekStartMillis }
            .sumOf { (it.totalPrice - it.balanceRemaining).coerceAtLeast(0.0) }
        val daysLeft = (DAYS_IN_WEEK - 1 - daysFromMonday).coerceIn(0, DAYS_IN_WEEK)
        return WeeklyGoalUi(
            targetAmount = goal.targetAmount,
            collectedAmount = collected,
            daysLeft = daysLeft
        )
    }

    private fun computeGreeting(): Greeting {
        val hour = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(timeZone).hour
        return when {
            hour < MORNING_CUTOFF_HOUR -> Greeting.MORNING
            hour < AFTERNOON_CUTOFF_HOUR -> Greeting.AFTERNOON
            else -> Greeting.EVENING
        }
    }

}

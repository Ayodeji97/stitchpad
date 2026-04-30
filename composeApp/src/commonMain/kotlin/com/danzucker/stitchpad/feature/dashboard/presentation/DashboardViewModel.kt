package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.dashboard.domain.BucketCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.FocusResolver
import com.danzucker.stitchpad.feature.dashboard.domain.NbaCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.ReconnectCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.WeeklyGoalCalculator
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MORNING_CUTOFF_HOUR = 12
private const val AFTERNOON_CUTOFF_HOUR = 17

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
                val uiState = FocusResolver.resolveUiState(buckets, nextBestActions, orders, customers)
                val reconnect = ReconnectCalculator.compute(orders, customers, today, timeZone)
                val focus = FocusResolver.resolveFocus(uiState, buckets, nextBestActions, customers, reconnect)
                val weeklyGoal = WeeklyGoalCalculator.compute(orders, today, goal, timeZone)

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

    private fun computeGreeting(): Greeting {
        val hour = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(timeZone).hour
        return when {
            hour < MORNING_CUTOFF_HOUR -> Greeting.MORNING
            hour < AFTERNOON_CUTOFF_HOUR -> Greeting.AFTERNOON
            else -> Greeting.EVENING
        }
    }
}

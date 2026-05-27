package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.displayGarmentName
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.dashboard.domain.BucketCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.FocusResolver
import com.danzucker.stitchpad.feature.dashboard.domain.NbaCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.ReconnectCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.WeeklyGoalCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.internal.simpleLabel
import com.danzucker.stitchpad.feature.dashboard.presentation.model.CustomerReadyUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FirstOrderSetupUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MORNING_CUTOFF_HOUR = 12
private const val AFTERNOON_CUTOFF_HOUR = 17
private const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

@OptIn(ExperimentalTime::class)
@Suppress("TooManyFunctions")
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val smartUsageStore: SmartUsageStore,
    private val entitlements: EntitlementsProvider,
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
                observeSmartQuota()
                observeEntitlements()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DashboardState()
        )

    /**
     * Mirror the Smart Suggestions cross-feature cache into dashboard state
     * so the SmartSectionCard counter chip stays in sync. The store is
     * updated by DraftMessageViewModel after each successful draft.
     */
    private fun observeSmartQuota() {
        viewModelScope.launch {
            smartUsageStore.remainingFreeQuota.collect { remaining ->
                _state.update { it.copy(smartFreeQuotaRemaining = remaining) }
            }
        }
    }

    /**
     * Observe the user's entitlements and push the welcome-ending banner
     * state when [UserEntitlements.isWithinWelcomeEndingWarning] is true.
     * `welcomeDaysLeft` comes straight from EntitlementsCalculator so the
     * banner copy and the show/hide flag share Lagos calendar math —
     * previously this used `ms / 86_400_000` in the system default timezone,
     * which could drift the displayed number by one day vs. the warning flag.
     */
    private fun observeEntitlements() {
        viewModelScope.launch {
            entitlements.flow.collect { e ->
                _state.update {
                    it.copy(
                        welcomeBannerDaysLeft = e.welcomeDaysLeft,
                        showWelcomeBanner = e.isWithinWelcomeEndingWarning,
                    )
                }
            }
        }
    }

    // Single sealed-action dispatch table — every DashboardAction handled in
    // one place. Splitting into per-group helpers would scatter the contract
    // across the file without clarifying any one branch.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
            DashboardAction.OnViewAllOrdersClick -> emitEvent(DashboardEvent.NavigateToOrders)
            DashboardAction.OnViewPipelineInProgressClick -> emitEvent(DashboardEvent.NavigateToOrders)
            DashboardAction.OnViewPipelineNotStartedClick -> emitEvent(DashboardEvent.NavigateToOrders)
            DashboardAction.OnViewReconnectClick -> emitEvent(DashboardEvent.NavigateToCustomers)
            DashboardAction.OnNewOrderClick -> emitEvent(DashboardEvent.NavigateToOrderForm)
            DashboardAction.OnCreateOrderClick -> emitEvent(
                if (_state.value.uiState == DashboardUiState.BrandNew) {
                    DashboardEvent.NavigateToAddCustomerFirst
                } else {
                    DashboardEvent.NavigateToOrderForm
                }
            )
            DashboardAction.OnNewCustomerClick -> emitEvent(DashboardEvent.NavigateToCustomerForm)
            DashboardAction.OnAddMeasurementClick -> emitEvent(
                if (_state.value.uiState == DashboardUiState.BrandNew) {
                    DashboardEvent.NavigateToAddCustomerFirst
                } else {
                    DashboardEvent.NavigateToCustomers
                }
            )
            DashboardAction.OnGoalsCardClick -> emitEvent(DashboardEvent.NavigateToGoalSetup)
            DashboardAction.OnFocusCtaClick -> handleFocusCtaClick()
            DashboardAction.OnSettingsClick -> emitEvent(DashboardEvent.NavigateToSettings)
            DashboardAction.OnSetupChecklistAdvance -> emitEvent(DashboardEvent.NavigateToOrderForm)
            is DashboardAction.OnSetupOrderEditClick ->
                emitEvent(DashboardEvent.NavigateToEditOrder(action.orderId))
            is DashboardAction.OnCustomerReadyClick -> emitEvent(
                DashboardEvent.NavigateToCustomerDetail(action.customerId)
            )
            is DashboardAction.OnCustomerReadyMessageClick -> {
                val customer = _state.value.customerReady
                if (customer != null && customer.customerId == action.customerId) {
                    emitEvent(
                        DashboardEvent.LaunchWhatsAppForReconnect(
                            com.danzucker.stitchpad.feature.dashboard.presentation.model
                                .ReconnectCandidate(
                                    customerId = customer.customerId,
                                    customerName = customer.name,
                                    customerPhone = customer.phone,
                                    daysSinceLastInteraction = 0,
                                    hasOrderHistory = false,
                                )
                        )
                    )
                }
            }
            is DashboardAction.OnReconnectCandidateClick -> emitEvent(
                DashboardEvent.LaunchWhatsAppForReconnect(action.candidate)
            )
            is DashboardAction.OnReconnectClick -> {
                val candidate = _state.value.reconnectCandidates
                    .firstOrNull { it.customerId == action.customerId }
                if (candidate != null) {
                    emitEvent(DashboardEvent.LaunchWhatsAppForReconnect(candidate))
                }
            }
            is DashboardAction.OnReconnectViewCustomerClick -> emitEvent(
                DashboardEvent.NavigateToCustomerDetail(action.customerId)
            )
            DashboardAction.OnDraftMessageClick -> emitEvent(DashboardEvent.NavigateToDraftMessage)
            DashboardAction.OpenUpgrade -> emitEvent(DashboardEvent.NavigateToUpgrade)
            DashboardAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleFocusCtaClick() {
        val current = _state.value
        when (current.focusVariant) {
            FocusVariant.BrandNew -> emitEvent(DashboardEvent.NavigateToAddCustomerFirst)
            FocusVariant.FirstOrder -> {
                // Two FocusResolver sub-cases collapse into FirstOrder:
                //  1. No order yet → open the new-order form.
                //  2. One order without a deadline → "Complete setup" CTA;
                //     route to the edit form so the user lands on the field
                //     they need to fill, not a blank new-order form.
                val incompleteOrderId = current.firstOrderSetup
                    ?.takeIf { it.hasOrder }
                    ?.orderId
                if (incompleteOrderId != null) {
                    emitEvent(DashboardEvent.NavigateToEditOrder(incompleteOrderId))
                } else {
                    emitEvent(DashboardEvent.NavigateToOrderForm)
                }
            }
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
            // Apple Sign-In only returns fullName on the very first auth per Apple ID
            // per app (Apple's privacy model). Re-auths and failed-first-attempts come
            // back with no name, so displayName ends up blank. Fall back to the email's
            // local-part split on common separators so the greeting + avatar show
            // something sensible instead of "?".
            val nameSource = user.displayName.ifBlank {
                user.email.substringBefore('@')
                    .replace('.', ' ').replace('_', ' ').replace('-', ' ')
            }
            val firstName = firstNameOf(nameSource)
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
                val focus = FocusResolver.resolveFocus(
                    uiState = uiState,
                    buckets = buckets,
                    nextBestActions = nextBestActions,
                    customers = customers,
                    orders = orders,
                    reconnect = reconnect,
                )
                val weeklyGoal = WeeklyGoalCalculator.compute(orders, today, goal, timeZone)
                // "Your customer" card surfaces only on FirstCustomer. Pick the
                // most recently added so a user who just created a second
                // customer sees that one first, not whoever was created earlier.
                // "Your customer" card is the no-orders-yet celebration —
                // once an order exists the screen pivots to the Order setup
                // checklist + order row, so the customer card stops earning
                // its space.
                val customerReady = if (
                    uiState == DashboardUiState.FirstCustomer && orders.isEmpty()
                ) {
                    customers.maxByOrNull { it.createdAt }?.let { c ->
                        val daysSinceAdded = (
                            (nowMillis() - c.createdAt) /
                                ONE_DAY_MILLIS
                            ).toInt().coerceAtLeast(0)
                        CustomerReadyUi(
                            customerId = c.id,
                            name = c.name,
                            phone = c.phone,
                            daysSinceAdded = daysSinceAdded,
                            hasOrders = false,
                        )
                    }
                } else {
                    null
                }
                val firstOrderSetup = computeFirstOrderSetup(customers, orders)

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
                        focusCtaSubtitle = focus.ctaSubtitle,
                        focusSectionLabel = focus.sectionLabel,
                        reconnectCandidates = reconnect,
                        customerReady = customerReady,
                        firstOrderSetup = firstOrderSetup,
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

    /**
     * Drives the persistent "Order setup" checklist. Returns non-null only
     * during the first-order onboarding window:
     *   - has at least one customer, AND
     *   - has 0 or 1 orders, AND
     *   - the first order (if any) is missing a deadline OR a deposit.
     *
     * Once the first order has both a deadline and a deposit > 0, OR the
     * user has more than one order (past onboarding), this returns null
     * and the checklist disappears.
     */
    private fun computeFirstOrderSetup(
        customers: List<Customer>,
        orders: List<Order>,
    ): FirstOrderSetupUi? {
        if (customers.isEmpty() || orders.size > 1) return null

        val firstOrder = orders.minByOrNull { it.createdAt }
        val customerName = firstOrder?.customerName?.takeIf { it.isNotBlank() }
            ?: customers.minByOrNull { it.createdAt }?.name
        val hasOrder = firstOrder != null
        val hasDueDate = firstOrder?.deadline != null
        val hasDeposit = (firstOrder?.depositPaid ?: 0.0) > 0.0
        val setupComplete = hasOrder && hasDueDate && hasDeposit

        return if (customerName != null && !setupComplete) {
            FirstOrderSetupUi(
                customerName = customerName,
                orderId = firstOrder?.id,
                hasOrder = hasOrder,
                hasDueDate = hasDueDate,
                hasDeposit = hasDeposit,
                garmentLabel = firstOrder?.items?.firstOrNull()?.displayGarmentName { it.simpleLabel() }.orEmpty(),
                totalAmount = firstOrder?.totalPrice ?: 0.0,
            )
        } else {
            null
        }
    }
}

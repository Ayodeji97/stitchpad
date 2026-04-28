package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusResolution
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
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
import kotlinx.datetime.daysUntil
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

private const val PIPELINE_PREVIEW_LIMIT = 3
private const val NBA_LIMIT = 5
private const val FINISH_STALE_DAYS = 7
private const val DELIVER_STALE_DAYS = 3
private const val START_SOON_DAYS = 7

private const val RECONNECT_LIMIT = 5
private const val RECONNECT_MIN_DAYS = 14
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
                // PR 7 will route to reconnect/WhatsApp; for now fall back to new order.
                emitEvent(DashboardEvent.NavigateToOrderForm)
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
                val buckets = computeBuckets(orders, customersById, today)
                val uiState = resolveUiState(buckets, orders, customers)
                val reconnect = computeReconnectCandidates(orders, customers, today)
                val focus = resolveFocus(uiState, buckets, customers, reconnect)
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
                        nextBestActions = buckets.nextBestActions,
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
        orders: List<Order>,
        customers: List<Customer>
    ): DashboardUiState {
        if (orders.isEmpty() && customers.isEmpty()) return DashboardUiState.BrandNew
        if (orders.isEmpty()) return DashboardUiState.FirstCustomer
        if (buckets.overdue.isNotEmpty() || buckets.dueToday.isNotEmpty()) {
            return DashboardUiState.BusyDay
        }
        if (buckets.ready.isNotEmpty()) return DashboardUiState.ReadyForPickup
        if (buckets.nextBestActions.isNotEmpty()) return DashboardUiState.NbaActive
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
            val topNba = buckets.nextBestActions.first()
            FocusResolution(
                variant = FocusVariant.Earn,
                headline = UiText.StringResourceText(
                    Res.string.focus_earn_title,
                    arrayOf(buckets.nextBestActions.size)
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
     * Builds the list of customers to surface in the ReconnectStrip. A candidate is a
     * customer with no active (non-DELIVERED) order. Customers with order history must
     * have been inactive for at least [RECONNECT_MIN_DAYS] days; customers with no order
     * history (e.g. just-added) always pass. Capped at [RECONNECT_LIMIT].
     */
    private fun computeReconnectCandidates(
        orders: List<Order>,
        customers: List<Customer>,
        today: LocalDate
    ): List<ReconnectCandidate> {
        if (customers.isEmpty()) return emptyList()

        val activeOrderCustomerIds = orders
            .filter { it.status != OrderStatus.DELIVERED }
            .map { it.customerId }
            .toSet()

        return customers
            .asSequence()
            .filter { it.id !in activeOrderCustomerIds }
            .filter { it.phone.isNotBlank() }
            .map { customer ->
                val customerOrders = orders.filter { it.customerId == customer.id }
                val mostRecentMillis = customerOrders.maxOfOrNull { it.updatedAt }
                val daysSince = if (mostRecentMillis != null && mostRecentMillis > 0L) {
                    mostRecentMillis.toLocalDate(timeZone).daysUntil(today).coerceAtLeast(0)
                } else {
                    0
                }
                ReconnectCandidate(
                    customerId = customer.id,
                    customerName = customer.name,
                    customerPhone = customer.phone,
                    daysSinceLastInteraction = daysSince,
                    hasOrderHistory = customerOrders.isNotEmpty()
                )
            }
            .filter { !it.hasOrderHistory || it.daysSinceLastInteraction >= RECONNECT_MIN_DAYS }
            .sortedByDescending { it.daysSinceLastInteraction }
            .take(RECONNECT_LIMIT)
            .toList()
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

    @Suppress("LongMethod")
    private fun computeBuckets(
        orders: List<Order>,
        customersById: Map<String, Customer>,
        today: LocalDate
    ): Buckets {
        val active = orders.filter { it.status != OrderStatus.DELIVERED }

        val overdue = active
            .filter { order ->
                val deadlineDate = order.deadline?.toLocalDate(timeZone)
                deadlineDate != null && deadlineDate < today
            }
            .sortedBy { it.deadline }
            .map { it.toRow(today) }

        val dueToday = active
            .filter { order ->
                order.deadline?.toLocalDate(timeZone) == today
            }
            .map { it.toRow(today) }

        val ready = orders
            .filter { it.status == OrderStatus.READY }
            .map { it.toRow(today) }

        val unpaid = active.filter { it.balanceRemaining > 0.0 }

        val triageOrderIds = buildSet {
            active.forEach { order ->
                val deadlineDate = order.deadline?.toLocalDate(timeZone)
                if (deadlineDate != null && deadlineDate <= today) add(order.id)
            }
            orders.filter { it.status == OrderStatus.READY }.forEach { add(it.id) }
        }

        val pipelineCandidates = active
            .filter { it.id !in triageOrderIds }
            .sortedWith(compareBy(nullsLast()) { it.deadline })

        val pipelineInProgressAll = pipelineCandidates.filter { it.status == OrderStatus.IN_PROGRESS }
        val pipelinePendingAll = pipelineCandidates.filter { it.status == OrderStatus.PENDING }

        return Buckets(
            overdue = overdue,
            dueToday = dueToday,
            ready = ready,
            outstandingAmount = unpaid.sumOf { it.balanceRemaining },
            outstandingOrderCount = unpaid.size,
            pipelineInProgress = pipelineInProgressAll.take(PIPELINE_PREVIEW_LIMIT)
                .map { it.toPipelineRow(today) },
            pipelineInProgressTotal = pipelineInProgressAll.size,
            pipelinePending = pipelinePendingAll.take(PIPELINE_PREVIEW_LIMIT)
                .map { it.toPipelineRow(today) },
            pipelinePendingTotal = pipelinePendingAll.size,
            nextBestActions = deriveNextBestActions(orders, customersById, today)
        )
    }

    private fun Order.toPipelineRow(today: LocalDate): DashboardOrderRow {
        val garment = items.firstOrNull()?.garmentType?.simpleLabel().orEmpty()
        val deadlineDate = deadline?.toLocalDate(timeZone)
        val daysUntil = deadlineDate
            ?.takeIf { it > today }
            ?.let { today.daysUntil(it) }
        return DashboardOrderRow(
            orderId = id,
            customerName = customerName,
            primaryLabel = garment,
            daysUntilDeadline = daysUntil
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun deriveNextBestActions(
        orders: List<Order>,
        customersById: Map<String, Customer>,
        today: LocalDate
    ): List<NextBestAction> {
        val candidates = mutableListOf<NextBestAction>()

        orders.forEach { order ->
            if (order.status == OrderStatus.DELIVERED) return@forEach
            val customer = customersById[order.customerId] ?: return@forEach
            if (customer.phone.isBlank()) return@forEach
            val garment = order.items.firstOrNull()?.garmentType?.simpleLabel().orEmpty()
            val deadlineDate = order.deadline?.toLocalDate(timeZone)
            val daysUntilDeadline = deadlineDate?.let { today.daysUntil(it) }

            val action = when {
                deadlineDate != null && deadlineDate < today && order.balanceRemaining > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectOverdue,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = today.daysUntil(deadlineDate).let { -it }
                    )
                order.status == OrderStatus.READY && order.balanceRemaining > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectOnReady,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = daysSinceLastTransitionTo(order, OrderStatus.READY, today)
                    )
                order.status == OrderStatus.IN_PROGRESS &&
                    daysSinceLastTransitionTo(order, OrderStatus.IN_PROGRESS, today) > FINISH_STALE_DAYS ->
                    buildAction(
                        type = NextBestActionType.FinishStale,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = daysSinceLastTransitionTo(order, OrderStatus.IN_PROGRESS, today)
                    )
                order.status == OrderStatus.READY &&
                    daysSinceLastTransitionTo(order, OrderStatus.READY, today) > DELIVER_STALE_DAYS ->
                    buildAction(
                        type = NextBestActionType.DeliverStale,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = daysSinceLastTransitionTo(order, OrderStatus.READY, today)
                    )
                order.status == OrderStatus.PENDING &&
                    order.depositPaid == 0.0 &&
                    order.totalPrice > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectDeposit,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.totalPrice,
                        days = daysSinceCreation(order, today)
                    )
                order.status == OrderStatus.PENDING &&
                    daysUntilDeadline != null && daysUntilDeadline in 0..START_SOON_DAYS ->
                    buildAction(
                        type = NextBestActionType.StartSoon,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = 0.0,
                        days = daysUntilDeadline
                    )
                else -> null
            }
            if (action != null) candidates += action
        }

        return candidates
            .sortedWith(
                compareBy<NextBestAction> { it.type.ordinal }
                    .thenBy { if (it.type == NextBestActionType.StartSoon) it.daysCount else 0 }
                    .thenByDescending { it.balanceAmount }
            )
            .take(NBA_LIMIT)
    }

    private fun buildAction(
        type: NextBestActionType,
        order: Order,
        customer: Customer,
        garment: String,
        balance: Double,
        days: Int
    ): NextBestAction = NextBestAction(
        type = type,
        orderId = order.id,
        customerId = customer.id,
        customerName = order.customerName.ifBlank { customer.name },
        customerPhone = customer.phone,
        garmentLabel = garment,
        balanceAmount = balance,
        daysCount = days
    )

    private fun daysSinceLastTransitionTo(order: Order, target: OrderStatus, today: LocalDate): Int {
        val lastTransition = order.statusHistory.lastOrNull { it.status == target }
        val anchorMillis = lastTransition?.changedAt ?: order.updatedAt
        if (anchorMillis == 0L) return 0
        val anchorDate = anchorMillis.toLocalDate(timeZone)
        return anchorDate.daysUntil(today)
    }

    private fun daysSinceCreation(order: Order, today: LocalDate): Int {
        if (order.createdAt == 0L) return 0
        return order.createdAt.toLocalDate(timeZone).daysUntil(today)
    }

    private fun computeGreeting(): Greeting {
        val hour = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(timeZone).hour
        return when {
            hour < MORNING_CUTOFF_HOUR -> Greeting.MORNING
            hour < AFTERNOON_CUTOFF_HOUR -> Greeting.AFTERNOON
            else -> Greeting.EVENING
        }
    }

    private fun Long.toLocalDate(tz: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).date

    private fun Order.toRow(today: LocalDate): DashboardOrderRow {
        val garment = items.firstOrNull()?.garmentType?.simpleLabel().orEmpty()
        val deadlineDate = deadline?.toLocalDate(timeZone)
        val daysLate = deadlineDate
            ?.takeIf { it < today }
            ?.daysUntil(today)
        return DashboardOrderRow(
            orderId = id,
            customerName = customerName,
            primaryLabel = garment,
            daysLate = daysLate
        )
    }

    private data class Buckets(
        val overdue: List<DashboardOrderRow>,
        val dueToday: List<DashboardOrderRow>,
        val ready: List<DashboardOrderRow>,
        val outstandingAmount: Double,
        val outstandingOrderCount: Int,
        val pipelineInProgress: List<DashboardOrderRow>,
        val pipelineInProgressTotal: Int,
        val pipelinePending: List<DashboardOrderRow>,
        val pipelinePendingTotal: Int,
        val nextBestActions: List<NextBestAction>
    ) {
        /**
         * True when nothing in the dashboard genuinely needs the user's attention
         * "now" — i.e., no overdue / due-today / ready-for-pickup orders.
         *
         * Outstanding balance on pipeline orders (PENDING / IN_PROGRESS) is
         * deliberately excluded — money owed on work-in-flight is future revenue,
         * not urgent triage, so it should not push the dashboard into BusyDay/Focus.
         * Unpaid OVERDUE / READY orders are still surfaced via their own buckets.
         */
        fun isAllTriageEmpty(): Boolean = overdue.isEmpty() &&
            dueToday.isEmpty() &&
            ready.isEmpty()
    }
}

private fun GarmentType.simpleLabel(): String =
    name.lowercase().split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }

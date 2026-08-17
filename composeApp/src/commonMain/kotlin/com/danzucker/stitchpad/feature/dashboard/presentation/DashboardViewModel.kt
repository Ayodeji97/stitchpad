package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.config.domain.CommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.isUsableCommunityInviteUrl
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.model.displayGarmentName
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.collection.domain.CollectionCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.BucketCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.FocusQueue
import com.danzucker.stitchpad.feature.dashboard.domain.FocusResolver
import com.danzucker.stitchpad.feature.dashboard.domain.NbaCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.ReconnectCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.StaffPipelineCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.WeeklyGoalCalculator
import com.danzucker.stitchpad.feature.dashboard.domain.computeFocusQueue
import com.danzucker.stitchpad.feature.dashboard.domain.internal.simpleLabel
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import com.danzucker.stitchpad.feature.dashboard.domain.model.toOrderStatusAndSubStatus
import com.danzucker.stitchpad.feature.dashboard.presentation.model.CustomerReadyUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FirstOrderSetupUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.MeasurementsPickerRow
import com.danzucker.stitchpad.feature.dashboard.presentation.model.MeasurementsPickerUi
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryDestination
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolver
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.feature.order.presentation.list.OrderListFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.staff_advance_stage_error
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MORNING_CUTOFF_HOUR = 12
private const val AFTERNOON_CUTOFF_HOUR = 17
private const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

// iOS UIKit modal presentation fails if invoked right on the heels of a Compose
// sheet dismiss — same rationale as CustomerListViewModel.SHEET_DISMISS_DELAY_MS.
private const val PICKER_DISMISS_DELAY_MS = 450L
private const val COUNT_FETCH_TIMEOUT_MS = 3_000L

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val smartUsageStore: SmartUsageStore,
    private val entitlements: EntitlementsProvider,
    private val notificationRepository: NotificationRepository,
    private val pushTokenRegistrar: PushTokenRegistrar,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val appConfigRepository: AppConfigRepository,
    private val communityJoinTracker: CommunityJoinTracker,
    private val dismissal: CommunityBannerDismissal,
    private val activeWorkshopProvider: ActiveWorkshopProvider,
    private val staffMembershipPrefs: StaffMembershipPrefsStore,
    private val analytics: Analytics,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(DashboardState())

    // Captured from loadData()'s combine collect — not stored in DashboardState
    // because it's an implementation detail of the measurements picker, not
    // something any screen renders directly.
    private var latestCustomers: List<Customer> = emptyList()

    // Tracks the workshopUid loadData()'s combine last subscribed under, so a
    // genuine A -> B switch (not the first-ever subscribe, and not a
    // transition to/from null — already handled by the `workshopUid == null`
    // branch below) can be told apart from a cold start. See the reset note
    // in loadData().
    private var lastLoadDataWorkshopUid: String? = null

    private val _events = Channel<DashboardEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadData()
                observeSmartQuota()
                observeEntitlements()
                observeUnreadNotifications()
                observeCommunity()
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

    /**
     * Mirrors the live unread notification count from [NotificationRepository] into
     * dashboard state so the bell badge in the header stays in sync.
     */
    private fun observeUnreadNotifications() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            notificationRepository.observeUnreadCount(uid).collect { count ->
                _state.update { it.copy(unreadNotificationCount = count) }
            }
        }
    }

    /**
     * Hydrates the dismissal singleton from persisted prefs, then combines the
     * remote app config flow with [CommunityBannerDismissal.dismissed] so that
     * a dismiss/join from ANY surface (Settings row, dashboard banner, debug
     * reset) updates this already-alive ViewModel live — not just on recreation.
     */
    private fun observeCommunity() {
        viewModelScope.launch {
            dismissal.hydrate()
            combine(appConfigRepository.config, dismissal.dismissed) { cfg, dismissed ->
                cfg to dismissed
            }.collect { (cfg, dismissed) ->
                _state.update {
                    it.copy(
                        communityUrl = cfg.communityInviteUrl,
                        showCommunityBanner = cfg.communityEnabled &&
                            isUsableCommunityInviteUrl(cfg.communityInviteUrl) &&
                            !dismissed,
                    )
                }
            }
        }
    }

    private fun dismissCommunityBanner() {
        viewModelScope.launch { dismissal.markDismissed() }
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
            DashboardAction.OnSeeAllClick -> emitEvent(DashboardEvent.NavigateToOrders())
            DashboardAction.OnOutstandingClick -> emitEvent(DashboardEvent.NavigateToToCollect)
            DashboardAction.OnViewAllOrdersClick -> emitEvent(DashboardEvent.NavigateToOrders())
            // PR-A2 debt paid (Task 9, staff-phase2-assignment): tile taps now pass a filter
            // through NavigateToOrders so each lands on its own filtered Orders view. Overdue
            // and Due-today stay unfiltered — OrderListViewModel has no deadline filter yet,
            // and inventing one is out of scope here (brief explicitly reserves it).
            DashboardAction.OnViewOverdueClick -> emitEvent(DashboardEvent.NavigateToOrders())
            DashboardAction.OnViewDueTodayClick -> emitEvent(DashboardEvent.NavigateToOrders())
            DashboardAction.OnViewPipelineInProgressClick ->
                emitEvent(DashboardEvent.NavigateToOrders(filter = OrderListFilter.IN_PROGRESS))
            DashboardAction.OnViewPipelineNotStartedClick -> emitEvent(DashboardEvent.NavigateToOrders())
            DashboardAction.OnViewMyWorkClick ->
                emitEvent(DashboardEvent.NavigateToOrders(filter = OrderListFilter.MY_WORK))
            DashboardAction.OnViewReconnectClick -> emitEvent(DashboardEvent.NavigateToCustomers)
            DashboardAction.OnNewOrderClick,
            DashboardAction.OnCreateOrderClick,
            -> emitEvent(
                if (_state.value.uiState == DashboardUiState.BrandNew) {
                    DashboardEvent.NavigateToAddCustomerFirst
                } else {
                    DashboardEvent.NavigateToOrderForm
                }
            )
            DashboardAction.OnNewCustomerClick -> emitEvent(DashboardEvent.NavigateToCustomerForm)
            DashboardAction.OnInspirationClick -> emitEvent(DashboardEvent.NavigateToInspiration)
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
            DashboardAction.OnFoundingTailorsClick -> emitEvent(DashboardEvent.NavigateToFoundingTailors)
            DashboardAction.OpenUpgrade -> emitEvent(DashboardEvent.NavigateToUpgrade)
            DashboardAction.OnNotificationsClick -> emitEvent(DashboardEvent.NavigateToNotifications)
            DashboardAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
            DashboardAction.OnJoinCommunity -> {
                val url = _state.value.communityUrl
                if (url != null) emitEvent(DashboardEvent.OpenCommunityLink(url))
                viewModelScope.launch { communityJoinTracker.trackJoinTapped() }
                dismissCommunityBanner()
            }
            DashboardAction.OnDismissCommunityBanner -> dismissCommunityBanner()
            DashboardAction.OnMeasurementsShortcutClick -> openMeasurementsPicker()
            is DashboardAction.OnMeasurementsPickerQueryChange -> _state.update { s ->
                s.copy(measurementsPicker = s.measurementsPicker?.copy(query = action.query))
            }
            is DashboardAction.OnMeasurementsPickerRowClick -> onMeasurementsPickerRowClick(action.row)
            DashboardAction.OnDismissMeasurementsPicker -> _state.update { it.copy(measurementsPicker = null) }
            is DashboardAction.OnAdvanceStage -> {
                val next = action.fromStage.next()
                if (next != null) {
                    handleSetStage(action.orderId, action.fromStage, next, announceAdvance = true)
                }
            }
            is DashboardAction.OnSetStage ->
                handleSetStage(action.orderId, action.fromStage, action.toStage, announceAdvance = false)
            is DashboardAction.OnStageStepperClick ->
                _state.update { it.copy(stageSheetOrderId = action.orderId) }
            DashboardAction.OnDismissStageSheet ->
                _state.update { it.copy(stageSheetOrderId = null) }
        }
    }

    /**
     * Staff dashboard focus-queue hero CTA / undo snackbar / stage sheet — moves
     * [orderId] from [fromStage] to [toStage] via the same two repository calls
     * Order Detail's production-timeline "Update" action uses (`updateOrderStatus`
     * + `updateSubStatus`; see `OrderDetailViewModel.performStatusUpdate`).
     *
     * Guards, in order:
     *  1. No-op — [toStage] already equals [fromStage]; nothing to do.
     *  2. Re-entrancy — [orderId] already has an in-flight move recorded in
     *     [DashboardState.advancingOrders]; a second tap before it resolves is
     *     ignored outright (checked, and the flag set, synchronously — before
     *     the coroutine launches — so a same-frame double-tap can't race past it).
     *  3. Stale tap — the order's live stage (from [DashboardState.staffStageByOrderId],
     *     which — unlike [DashboardState.staffOpenQueue] — still covers READY
     *     orders, so the undo snackbar's backward move off a READY advance can
     *     resolve a live stage instead of finding null) no longer matches
     *     [fromStage], meaning a concurrent update elsewhere already moved it;
     *     no-op rather than moving from a state the caller wasn't actually
     *     looking at.
     *
     * Guards 2 and 3 are both SKIPPED for the exact-undo signature
     * (`advancingOrders[orderId] == toStage`). Undo is offered the instant the
     * advance's writes commit, which is typically before the order listener echoes
     * them back — so in that window the advance's own entry is still in
     * `advancingOrders` and `staffStageByOrderId` still reports the pre-advance stage.
     * Both guards would then silently swallow a legitimate undo. The signature is
     * only reachable from that advance's own snackbar, so the server state is known;
     * a duplicate forward tap never matches it (its toStage is the NEXT stage, not the
     * recorded fromStage) and stays fully guarded.
     *
     * No optimistic stage change: the in-flight flag only disables the CTA.
     * The visible stage updates when the order listener's next tick echoes it
     * (see `updateStaffState`'s pruning of stale `advancingOrders` entries),
     * which also self-heals the flag — no dedicated cleanup call needed on the
     * success path.
     *
     * Analytics only fires for forward moves ([toStage] ordinal > [fromStage]
     * ordinal), matching the hero CTA's original behavior. When [announceAdvance]
     * is true and both repository calls succeed, emits [DashboardEvent.StageAdvanced]
     * so the caller (the hero CTA) can offer an undo snackbar; backward/sheet moves
     * (`OnSetStage`) pass `announceAdvance = false` so undo is never re-offered.
     */
    @Suppress("ReturnCount")
    private fun handleSetStage(
        orderId: String,
        fromStage: PipelineStage,
        toStage: PipelineStage,
        announceAdvance: Boolean,
    ) {
        // Selection always closes the sheet, even when a guard below then no-ops
        // the move (stale fromStage, an in-flight duplicate, no toStage change) —
        // the sheet is a one-shot picker, not a form that stays open on rejection.
        _state.update { it.copy(stageSheetOrderId = null) }
        if (toStage == fromStage) return
        val current = _state.value
        // Exact-undo signature: the recorded fromStage of the advance still in
        // advancingOrders equals THIS request's toStage — i.e. "put it back where that
        // advance took it from". Only the undo snackbar (emitted after both of that
        // advance's writes committed) can produce it, so the server state is known and
        // both guards below would be false negatives: the entry is still there because
        // the listener echo hasn't arrived, and staffStageByOrderId still shows the
        // pre-advance stage for the same reason. A duplicate ADVANCE tap can't fake
        // this — its toStage is the stage ahead, never the recorded fromStage.
        val isExactUndo = current.advancingOrders[orderId] == toStage
        if (!isExactUndo) {
            if (current.advancingOrders.containsKey(orderId)) return
            val liveStage = current.staffStageByOrderId[orderId]
            if (liveStage != fromStage) return
        }
        // Replaces any existing entry with this call's fromStage, so the pruning pass in
        // updateStaffState still self-heals the flag for the undo write too.
        _state.update { it.copy(advancingOrders = it.advancingOrders + (orderId to fromStage)) }
        viewModelScope.launch {
            val userId = activeWorkshopProvider.workshopUidOrNull() ?: run {
                _state.update { it.copy(advancingOrders = it.advancingOrders - orderId) }
                return@launch
            }
            val (newStatus, newSubStatus) = toStage.toOrderStatusAndSubStatus()
            val statusResult = orderRepository.updateOrderStatus(userId, orderId, newStatus)
            if (statusResult is Result.Error) {
                _state.update {
                    it.copy(
                        advancingOrders = it.advancingOrders - orderId,
                        errorMessage = UiText.StringResourceText(Res.string.staff_advance_stage_error),
                    )
                }
                return@launch
            }
            // Matches OrderDetailViewModel.performStatusUpdate exactly: logged right
            // after the status write succeeds, before the sub-status write is attempted.
            if (toStage.ordinal > fromStage.ordinal) {
                analytics.logEvent(AnalyticsEvent.OrderStatusAdvanced(status = newStatus.name.lowercase()))
            }
            val subResult = orderRepository.updateSubStatus(userId, orderId, newSubStatus)
            if (subResult is Result.Error) {
                _state.update {
                    it.copy(
                        advancingOrders = it.advancingOrders - orderId,
                        errorMessage = UiText.StringResourceText(Res.string.staff_advance_stage_error),
                    )
                }
                return@launch
            }
            // Success: leave the in-flight flag set. updateStaffState prunes it
            // once the listener's next tick shows the order past fromStage.
            if (announceAdvance) {
                emitEvent(DashboardEvent.StageAdvanced(orderId, fromStage, toStage))
            }
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
                // CollectionOverdue reuses the Earn variant (see focusVariant
                // mapping) but routes to the To-Collect list instead of the
                // top NBA — the CTA needs the uiState, not just the variant,
                // to disambiguate the two sub-cases.
                if (current.uiState == DashboardUiState.CollectionOverdue) {
                    emitEvent(DashboardEvent.NavigateToToCollect)
                    return
                }
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

    private fun openMeasurementsPicker() {
        // No customers yet — same affordance as the "Measurement" tile: go create one.
        if (latestCustomers.isEmpty()) {
            onAction(DashboardAction.OnAddMeasurementClick)
            return
        }
        _state.update { it.copy(measurementsPicker = MeasurementsPickerUi(isLoading = true)) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(measurementsPicker = null) }
                return@launch
            }
            // One cached read per customer; freemium caps keep N small (15-200).
            // Each read is time-bounded: a cold Firestore cache with no network can
            // leave a first snapshot pending forever, and one stuck customer must not
            // hold the whole sheet in its loading state (codex, PR #261 — mirrors
            // MeasurementEntryResolver's bound).
            val rows = supervisorScope {
                latestCustomers.map { customer ->
                    async {
                        val result = try {
                            withTimeoutOrNull(COUNT_FETCH_TIMEOUT_MS) {
                                measurementRepository.observeMeasurements(userId, customer.id).first()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                            // A throwing repo (future refactor) must degrade to the same
                            // unknown-count row as a timeout, not cancel every sibling.
                            null
                        }
                        when (result) {
                            is Result.Success -> MeasurementsPickerRow(
                                customerId = customer.id,
                                name = customer.name,
                                measurementCount = result.data.size,
                                singleMeasurementId = result.data.singleOrNull()?.id,
                            )
                            // Errors AND timeouts must not masquerade as "no measurements" —
                            // that would show the destructive "+ Add" affordance and route
                            // to the create form for a customer who may already have data.
                            // Unknown count routes to customer detail instead (Bugbot, PR #261).
                            else -> MeasurementsPickerRow(
                                customerId = customer.id,
                                name = customer.name,
                                measurementCount = null,
                                singleMeasurementId = null,
                            )
                        }
                    }
                }.awaitAll()
            }
                .sortedWith(
                    // Unknown-count rows (null) group with has-measurements rows since
                    // the customer may well have data — only a confirmed zero sorts last.
                    compareByDescending<MeasurementsPickerRow> { (it.measurementCount ?: 1) > 0 }
                        .thenBy { it.name.lowercase() }
                )
            _state.update { current ->
                // The user may have dismissed the sheet while counts were loading.
                if (current.measurementsPicker == null) {
                    current
                } else {
                    current.copy(measurementsPicker = current.measurementsPicker.copy(isLoading = false, rows = rows))
                }
            }
        }
    }

    private fun onMeasurementsPickerRowClick(row: MeasurementsPickerRow) {
        _state.update { it.copy(measurementsPicker = null) }
        viewModelScope.launch {
            delay(PICKER_DISMISS_DELAY_MS)
            // Same decision rule as the customer actions sheet — including that an
            // unknown count (fetch failed/timed out) must not masquerade as "no
            // measurements" and trigger the create flow (Bugbot, PR #261). One
            // deliberate divergence: a confirmed zero goes straight to the create
            // form, not the detail empty state — the "+ Add" row already told the
            // user this customer is empty (product decision, PR #263).
            val destination = MeasurementEntryResolver.destinationFor(
                customerId = row.customerId,
                measurementCount = row.measurementCount,
                singleMeasurementId = row.singleMeasurementId,
            )
            val event = when (destination) {
                is MeasurementEntryDestination.Detail -> destination.measurementId?.let { measurementId ->
                    DashboardEvent.NavigateToMeasurementDetail(destination.customerId, measurementId)
                } ?: DashboardEvent.NavigateToAddMeasurement(destination.customerId)
                is MeasurementEntryDestination.CustomerDetail ->
                    DashboardEvent.NavigateToCustomerDetail(destination.customerId)
            }
            emitEvent(event)
        }
    }

    // Kill-switch / staff-revocation must bite mid-session, not on next restart
    // (StitchPad exploration, 2026-08-08): the old one-shot `awaitHydrated()` read
    // pinned workshopUid/isStaff for the VM's whole lifetime. Re-deriving on every
    // (workshopUid, isStaff) change — flatMapLatest cancels the previous combine
    // (and its Firestore listeners) before starting the new one — keeps the
    // dashboard correct for the LIVE session, not the one it started in.
    private fun loadData() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser() ?: run {
                _state.update { it.copy(uiState = DashboardUiState.BrandNew) }
                return@launch
            }
            // Fire-and-forget: register the device's push token for this user.
            // The merge-write is idempotent so running on every cold start is safe.
            viewModelScope.launch { pushTokenRegistrar.registerForUser(authUser.id) }

            // Identity split: active staff read the OWNER's tree (workshopUid); an
            // owner reads their own. authUser stays the signed-in identity, so the
            // greeting name/avatar is always the person actually looking.
            activeWorkshopProvider.flow
                .map { session ->
                    Triple(session.workshopUid.takeIf { it.isNotBlank() }, session.isActiveStaff, session.authUid)
                }
                .distinctUntilChanged()
                .flatMapLatest { (workshopUid, isStaff, staffAuthUid) ->
                    // A -> B (both non-null, different uids): flatMapLatest cancels A's
                    // combine and starts B's, but the combine can't produce a tick until
                    // ALL four of its flows have emitted at least once under the new
                    // uid — until then this stays stale on A's last tick, which is only
                    // masked today by the role-change nav redirect. Emit the same null
                    // sentinel the sign-out branch already uses so the reset logic isn't
                    // duplicated. Skipped on cold start (lastLoadDataWorkshopUid == null)
                    // so first load doesn't flash BrandNew before the real data lands.
                    val isWorkshopSwitch = lastLoadDataWorkshopUid != null &&
                        workshopUid != null &&
                        workshopUid != lastLoadDataWorkshopUid
                    lastLoadDataWorkshopUid = workshopUid
                    if (workshopUid == null) {
                        flowOf(null)
                    } else {
                        if (isStaff) {
                            // Publish staff identity up front so the header renders the staff
                            // variant (name + Staff pill + workshop) while the first snapshot loads.
                            _state.update {
                                it.copy(isStaff = true, businessName = staffMembershipPrefs.workshopName.value)
                            }
                        }
                        // Orders/customers are scoped to workshopUid so active staff see the
                        // owner's book; the user doc stays on authUid (the signed-in person's
                        // own profile drives the greeting name + avatar). The Firestore user
                        // doc is in the combine so logo + workshop-name edits flow through live.
                        val combined: Flow<DashboardLoadTick?> = combine(
                            userRepository.observeUser(authUser.id).onStart { emit(null) },
                            orderRepository.observeOrders(workshopUid),
                            customerRepository.observeCustomers(workshopUid),
                            goalFlowFor(isStaff, workshopUid),
                        ) { firestoreUser, ordersResult, customersResult, goalResult ->
                            DashboardLoadTick(
                                isStaff = isStaff,
                                staffAuthUid = staffAuthUid,
                                data = UserAndDashboardData(firestoreUser, ordersResult, customersResult, goalResult),
                            )
                        }
                        if (isWorkshopSwitch) combined.onStart { emit(null) } else combined
                    }
                }
                .collect { tick ->
                    if (tick == null) {
                        resetOrderAndCustomerDerivedState()
                        return@collect
                    }
                    if (tick.isStaff) {
                        updateStaffState(authUser, tick.staffAuthUid, tick.data)
                    } else {
                        updateOwnerState(authUser, tick.data)
                    }
                }
        }
    }

    /**
     * Handles loadData()'s null tick — hit both when signed out (workshopUid
     * null, the whole screen navigates away so this was historically a
     * `uiState`-only reset) and by [loadData]'s isWorkshopSwitch sentinel,
     * which fires mid-session while this screen stays put. Leaving every
     * other order/customer-derived field untouched in that second case would
     * keep showing the PREVIOUS tree's pipeline counts, buckets, and focus
     * card under the new (loading) uiState/staffPipeline. `staffPipeline =
     * null` doubles as the staff loading sentinel; every other field here
     * just matches DashboardState()'s own default for that field.
     */
    private fun resetOrderAndCustomerDerivedState() {
        latestCustomers = emptyList()
        _state.update {
            it.copy(
                uiState = DashboardUiState.BrandNew,
                staffPipeline = null,
                staffMineCount = 0,
                staffOpenQueue = emptyList(),
                staffStageByOrderId = emptyMap(),
                focusQueue = FocusQueue(hero = null, thenQueue = emptyList(), shopQueue = emptyList()),
                advancingOrders = emptyMap(),
                overdue = emptyList(),
                dueToday = emptyList(),
                ready = emptyList(),
                outstandingAmount = 0.0,
                outstandingOrderCount = 0,
                outstandingOverdueCount = 0,
                nextBestActions = emptyList(),
                pipelineInProgress = emptyList(),
                pipelineInProgressTotal = 0,
                pipelinePending = emptyList(),
                pipelinePendingTotal = 0,
                focusVariant = FocusVariant.Quiet,
                focusHeadline = null,
                focusSupporting = null,
                focusCtaLabel = null,
                focusCtaSubtitle = null,
                focusSectionLabel = null,
                reconnectCandidates = emptyList(),
                customerReady = null,
                firstOrderSetup = null,
                weeklyGoal = null,
            )
        }
    }

    /**
     * Weekly goals are an owner-only concept and live outside the staff read
     * wall, so a staff read would just be denied — substitute a constant null.
     */
    private fun goalFlowFor(
        isStaff: Boolean,
        workshopUid: String,
    ): Flow<Result<WeeklyGoal?, DataError.Network>> =
        if (isStaff) {
            flowOf(Result.Success<WeeklyGoal?>(null))
        } else {
            weeklyGoalRepository.observeWeeklyGoal(workshopUid)
        }

    /**
     * Merge the Firestore profile doc over the Auth identity: Auth identity wins
     * when Firestore lacks the field, Firestore-only fields (businessName, logo)
     * win when present. The editable profile doc doesn't redundantly store email
     * or displayName, so a wholesale replacement would blank identity for a new
     * signup whose snapshot has just arrived.
     */
    private fun resolveUser(authUser: User, firestoreUser: User?): User =
        firestoreUser?.copy(
            email = firestoreUser.email.ifBlank { authUser.email },
            displayName = firestoreUser.displayName.ifBlank { authUser.displayName },
        ) ?: authUser

    /**
     * Apple Sign-In only returns fullName on the very first auth per Apple ID,
     * so displayName can be blank; fall back to the email local-part split on
     * common separators so the greeting + avatar show something sensible.
     */
    private fun firstNameFor(user: User): String {
        val nameSource = user.displayName.ifBlank {
            user.email.substringBefore('@')
                .replace('.', ' ').replace('_', ' ').replace('-', ' ')
        }
        return firstNameOf(nameSource)
    }

    /**
     * Money-free STAFF dashboard: throughput counts + advance queue + pipeline.
     * Deliberately populates none of the money/owner surfaces (outstanding,
     * nextBestActions, weeklyGoal, focus, reconnect, banners) — they stay at
     * their defaults, so the staff view is money-free at the STATE level, not
     * merely hidden in the composable.
     */
    private fun updateStaffState(authUser: User, staffAuthUid: String, combined: UserAndDashboardData) {
        val ordersResult = combined.ordersResult
        val orders = (ordersResult as? Result.Success)?.data ?: emptyList()
        val today = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(timeZone).date
        val buckets = BucketCalculator.compute(orders, today, timeZone)
        val user = resolveUser(authUser, combined.firestoreUser)
        // Every staff-visible order's live stage, INCLUDING READY — unlike
        // buckets.openQueue (staffOpenQueue's source), which BucketCalculator
        // filters READY out of. Union of openQueue + ready together covers exactly
        // BucketCalculator's `active` set (non-DELIVERED orders), since READY and
        // non-READY are disjoint, exhaustive subsets of it. This is the source both
        // handleSetStage's stale-tap guard and the advancingOrders pruning below
        // use, so a hero advance landing on READY doesn't strand the undo snackbar.
        val staffStageByOrderId = buildMap {
            buckets.openQueue.forEach { row -> row.stage?.let { put(row.orderId, it) } }
            buckets.ready.forEach { row -> row.stage?.let { put(row.orderId, it) } }
        }
        // Self-heals advancingOrders (focus-queue design): an entry survives only
        // while the live order's stage still matches what it was recorded as when
        // the advance/undo tap landed — the moment this tick's fresh stage map
        // shows it moved on (or it drops out of the map entirely, e.g. the order
        // was delivered), the in-flight flag clears itself with no dedicated
        // cleanup call.
        val prunedAdvancing = _state.value.advancingOrders.filter { (orderId, fromStage) ->
            staffStageByOrderId[orderId] == fromStage
        }
        // Same self-healing pass for the open stage sheet: an id that no longer names a
        // staff-visible order (delivered, reassigned out of this workshop, listener
        // dropped it) can never be cleared by the UI — OnStageStepperClick is a toggle
        // onto the SAME id, not a re-open — so it would stay stranded forever. Keyed off
        // the UNION map, not staffOpenQueue: an order merely ticking to READY while its
        // sheet is open must NOT force-close it (Decision 2B lets staff move a READY
        // order back), and BucketCalculator filters READY out of openQueue. Only a fully
        // vanished order clears the sheet.
        val prunedStageSheetOrderId = _state.value.stageSheetOrderId
            ?.takeIf { staffStageByOrderId.containsKey(it) }
        val staffOpenQueue = buckets.openQueue.map { row -> row.moneyFree() }
        // Business logic lives here, not in the composable (CLAUDE.md) — the
        // hero/then/shop-queue split is computed once per tick and handed to the
        // UI as plain state.
        val focusQueue = computeFocusQueue(staffOpenQueue, staffAuthUid)
        _state.update {
            it.copy(
                isStaff = true,
                viewerMemberId = staffAuthUid,
                firstName = firstNameFor(user),
                // Owner's workshop name comes from redeem-time prefs — staff can't
                // read the owner user doc, so it never comes from Firestore here.
                businessName = staffMembershipPrefs.workshopName.value,
                businessLogoUrl = null,
                greeting = computeGreeting(),
                todayDate = today,
                // Strip per-row money so the staff STATE carries no order value.
                overdue = buckets.overdue.map { row -> row.moneyFree() },
                dueToday = buckets.dueToday.map { row -> row.moneyFree() },
                staffPipeline = StaffPipelineCalculator.compute(orders),
                // "Mine" count tile — orders assigned to THIS session's authUid, not the
                // whole workshop's roster (mirrors OrderListViewModel's "My work" match).
                staffMineCount = orders.count { order -> order.assignedMemberId == staffAuthUid },
                staffOpenQueue = staffOpenQueue,
                staffStageByOrderId = staffStageByOrderId,
                focusQueue = focusQueue,
                advancingOrders = prunedAdvancing,
                stageSheetOrderId = prunedStageSheetOrderId,
                // Only a genuine NEW listener error overwrites errorMessage. A Success
                // tick must not silently wipe an action error (e.g. staff_advance_stage_error,
                // set moments earlier by handleAdvanceStage) that the UI hasn't shown/
                // dismissed yet — design review, PR #366.
                errorMessage = (ordersResult as? Result.Error)?.error?.toDashboardUiText() ?: it.errorMessage,
            )
        }
    }

    @Suppress("LongMethod")
    private fun updateOwnerState(authUser: User, combined: UserAndDashboardData) {
        val ordersResult = combined.ordersResult
        val customersResult = combined.customersResult
        val goalResult = combined.goalResult
        val user = resolveUser(authUser, combined.firestoreUser)
        val firstName = firstNameFor(user)
        val workshopName = user.businessName?.takeIf { it.isNotBlank() }
        val orders = (ordersResult as? Result.Success)?.data ?: emptyList()
        val customers = (customersResult as? Result.Success)?.data ?: emptyList()
        // Keep the last successful snapshot: a transient customers fetch error must not
        // make the Measurements shortcut think the account has no customers (Bugbot, PR #261).
        if (customersResult is Result.Success) latestCustomers = customersResult.data
        val goal = (goalResult as? Result.Success)?.data
        val error = when {
            ordersResult is Result.Error -> ordersResult.error.toDashboardUiText()
            customersResult is Result.Error -> customersResult.error.toDashboardUiText()
            else -> null
        }
        // Recomputed on every emission so the greeting rolls morning -> afternoon -> evening
        // without recreating the ViewModel — emission-driven recompute is enough for this MVP.
        val greeting = computeGreeting()
        val today = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(timeZone).date
        val customersById = customers.associateBy { it.id }
        val buckets = BucketCalculator.compute(orders, today, timeZone)
        val collectibles = CollectionCalculator.collectibles(orders, customersById, nowMillis())
        val collectionSummary = CollectionCalculator.summarize(collectibles)
        val nextBestActions = NbaCalculator.compute(orders, customersById, today, timeZone)
        val uiState = FocusResolver.resolveUiState(
            buckets,
            nextBestActions,
            orders,
            customers,
            collectionOverdueCount = collectionSummary.overdueCount,
        )
        val reconnect = ReconnectCalculator.compute(orders, customers, today, timeZone)
        val focus = FocusResolver.resolveFocus(
            uiState = uiState,
            buckets = buckets,
            nextBestActions = nextBestActions,
            customers = customers,
            orders = orders,
            reconnect = reconnect,
            collectionOverdueCount = collectionSummary.overdueCount,
        )
        val weeklyGoal = WeeklyGoalCalculator.compute(orders, today, goal, timeZone)
        // "Your customer" card surfaces only on FirstCustomer. Pick the most
        // recently added so a user who just created a second customer sees that
        // one first. Once an order exists the screen pivots to the Order setup
        // checklist + order row, so the customer card stops earning its space.
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
                // A prior tick may have been staff (kill-switch / revocation flipped
                // the session mid-session) — clear all three explicitly so the money wall
                // holds at the state level, not just while `isStaff` composable guards
                // are in effect.
                isStaff = false,
                viewerMemberId = authUser.id,
                staffPipeline = null,
                staffMineCount = 0,
                staffOpenQueue = emptyList(),
                staffStageByOrderId = emptyMap(),
                focusQueue = FocusQueue(hero = null, thenQueue = emptyList(), shopQueue = emptyList()),
                advancingOrders = emptyMap(),
                businessName = workshopName,
                businessLogoUrl = user.businessLogoUrl,
                greeting = greeting,
                todayDate = today,
                overdue = buckets.overdue,
                dueToday = buckets.dueToday,
                ready = buckets.ready,
                outstandingAmount = collectionSummary.totalOutstanding,
                outstandingOrderCount = collectionSummary.orderCount,
                outstandingOverdueCount = collectionSummary.overdueCount,
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
                totalAmount = firstOrder?.payableTotal ?: 0.0,
            )
        } else {
            null
        }
    }
}

/** Combine-output holder so the 4-way flow combine has a typed payload for the collect lambda. */
private data class UserAndDashboardData(
    val firestoreUser: User?,
    val ordersResult: Result<List<Order>, DataError.Network>,
    val customersResult: Result<List<Customer>, DataError.Network>,
    val goalResult: Result<WeeklyGoal?, DataError.Network>,
)

/**
 * [loadData]'s flatMapLatest emits one of these per combine tick. [isStaff] rides
 * along with the data (rather than being read from an outer closure) so a
 * role flip mid-collection is dispatched with the role it was actually resolved
 * under for THAT tick, not a stale value captured when the listener started.
 */
private data class DashboardLoadTick(
    val isStaff: Boolean,
    val staffAuthUid: String,
    val data: UserAndDashboardData,
)

/**
 * Drops the optional money fields from a triage row for the staff view, so the
 * staff dashboard STATE never carries order value or payment status — the wall
 * holds at the state level, not just in the composable.
 */
private fun DashboardOrderRow.moneyFree(): DashboardOrderRow =
    copy(orderValue = null, paymentStatus = null)

package com.danzucker.stitchpad.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.reports.domain.CustomerInsightsCalculator
import com.danzucker.stitchpad.feature.reports.domain.KpiCalculator
import com.danzucker.stitchpad.feature.reports.domain.ProductionCountsCalculator
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.DebtorEntry
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalTime::class)
class ReportsViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val entitlementsProvider: EntitlementsProvider,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(ReportsState())
    private val periodFlow = MutableStateFlow(ReportsPeriod.WEEK)
    private val customRangeFlow = MutableStateFlow<CustomRange?>(null)

    // Cached for synchronous lookup when handling OnSendReminderClick — avoids a
    // round-trip through the customer repository for an action that fires off a
    // single deep link.
    private var cachedCustomers: List<Customer> = emptyList()
    private var cachedDebtors: List<DebtorEntry> = emptyList()

    private val _events = Channel<ReportsEvent>()
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
            initialValue = ReportsState()
        )

    fun onAction(action: ReportsAction) {
        when (action) {
            is ReportsAction.OnPeriodSelected -> periodFlow.value = action.period
            is ReportsAction.OnCustomRangeSelected -> {
                customRangeFlow.value = action.range
                periodFlow.value = ReportsPeriod.CUSTOM
            }
            ReportsAction.OnClearCustomRange -> {
                customRangeFlow.value = null
                periodFlow.value = ReportsPeriod.WEEK
            }
            is ReportsAction.OnTopCustomerClick -> emitEvent(
                ReportsEvent.NavigateToCustomerDetail(action.customerId)
            )
            is ReportsAction.OnDebtorClick -> emitEvent(
                ReportsEvent.NavigateToCustomerDetail(action.customerId)
            )
            is ReportsAction.OnSendReminderClick -> handleSendReminder(action.customerId)
            ReportsAction.OnUpgradeClick -> emitEvent(ReportsEvent.NavigateToUpgrade)
            ReportsAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun handleSendReminder(customerId: String) {
        val customer = cachedCustomers.firstOrNull { it.id == customerId }
        val debtor = cachedDebtors.firstOrNull { it.customerId == customerId }
        if (customer == null || debtor == null || customer.phone.isBlank()) return
        emitEvent(
            ReportsEvent.LaunchWhatsAppReminder(
                customerName = customer.name,
                customerPhone = customer.phone,
                totalOwed = debtor.totalOwed,
                oldestDeadline = debtor.oldestDeadline
            )
        )
    }

    private fun emitEvent(event: ReportsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun loadData() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: run {
                _state.update {
                    it.copy(isLoading = false, hasAnyOrders = false)
                }
                return@launch
            }
            combine(
                orderRepository.observeOrders(user.id),
                customerRepository.observeCustomers(user.id),
                periodFlow,
                customRangeFlow,
                entitlementsProvider.flow.map { it.tier != SubscriptionTier.FREE }
            ) { ordersResult, customersResult, period, customRange, isPremium ->
                Inputs(ordersResult, customersResult, period, customRange, isPremium)
            }.collect { recompute(it) }
        }
    }

    private data class Inputs(
        val ordersResult: Result<List<Order>, com.danzucker.stitchpad.core.domain.error.DataError.Network>,
        val customersResult: Result<List<Customer>, com.danzucker.stitchpad.core.domain.error.DataError.Network>,
        val period: ReportsPeriod,
        val customRange: CustomRange?,
        val isPremium: Boolean
    )

    @Suppress("LongMethod")
    private fun recompute(inputs: Inputs) {
        val orders = (inputs.ordersResult as? Result.Success)?.data ?: emptyList()
        val customers = (inputs.customersResult as? Result.Success)?.data ?: emptyList()
        cachedCustomers = customers
        val error = when {
            inputs.ordersResult is Result.Error -> inputs.ordersResult.error.toReportsUiText()
            inputs.customersResult is Result.Error -> inputs.customersResult.error.toReportsUiText()
            else -> null
        }
        val today = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(timeZone).date
        val hasAnyOrders = orders.isNotEmpty()
        // Custom is only computable once a range is picked; fall back to Week math
        // until the user picks one so the screen doesn't blank.
        val effectivePeriod = if (
            inputs.period == ReportsPeriod.CUSTOM && inputs.customRange == null
        ) {
            ReportsPeriod.WEEK
        } else {
            inputs.period
        }
        val kpiSummary = if (hasAnyOrders) {
            KpiCalculator.computeSummary(
                orders = orders,
                period = effectivePeriod,
                today = today,
                timeZone = timeZone,
                customRange = inputs.customRange
            )
        } else {
            null
        }
        val productionCounts = if (hasAnyOrders) {
            ProductionCountsCalculator.compute(orders)
        } else {
            null
        }
        val topCustomers = CustomerInsightsCalculator.topCustomers(
            orders = orders,
            customers = customers,
            period = effectivePeriod,
            today = today,
            timeZone = timeZone,
            customRange = inputs.customRange
        )
        val debtors = CustomerInsightsCalculator.debtors(orders, customers, timeZone)
        cachedDebtors = debtors.items

        _state.update {
            it.copy(
                isLoading = false,
                isPremium = inputs.isPremium,
                selectedPeriod = inputs.period,
                customRange = inputs.customRange,
                hasAnyOrders = hasAnyOrders,
                kpiSummary = kpiSummary,
                productionCounts = productionCounts,
                topCustomers = topCustomers,
                debtors = debtors,
                today = today,
                errorMessage = error
            )
        }
    }
}

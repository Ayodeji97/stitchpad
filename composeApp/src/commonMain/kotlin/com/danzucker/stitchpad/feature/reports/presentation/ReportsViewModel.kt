package com.danzucker.stitchpad.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import com.danzucker.stitchpad.feature.reports.domain.CustomerInsightsCalculator
import com.danzucker.stitchpad.feature.reports.domain.KpiCalculator
import com.danzucker.stitchpad.feature.reports.domain.ProductionCountsCalculator
import com.danzucker.stitchpad.feature.reports.domain.RevenueCalculator
import com.danzucker.stitchpad.feature.reports.domain.model.CustomRange
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
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

@OptIn(ExperimentalTime::class)
class ReportsViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val entitlementsRepository: EntitlementsRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(ReportsState())
    private val periodFlow = MutableStateFlow(ReportsPeriod.WEEK)
    private val customRangeFlow = MutableStateFlow<CustomRange?>(null)

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
            is ReportsAction.OnTopCustomerClick -> emitEvent(
                ReportsEvent.NavigateToCustomerDetail(action.customerId)
            )
            is ReportsAction.OnDebtorClick -> emitEvent(
                ReportsEvent.NavigateToCustomerDetail(action.customerId)
            )
            is ReportsAction.OnSendReminderClick -> emitEvent(
                ReportsEvent.LaunchWhatsAppReminder(action.customerId)
            )
            ReportsAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
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
                entitlementsRepository.observeIsPremium()
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
        val error = when {
            inputs.ordersResult is Result.Error -> inputs.ordersResult.error.toReportsUiText()
            inputs.customersResult is Result.Error -> inputs.customersResult.error.toReportsUiText()
            else -> null
        }
        val today = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(timeZone).date
        val hasAnyOrders = orders.isNotEmpty()
        // Custom is only computable once a range is picked. If the user taps Custom
        // before picking, fall back to Week math so the screen doesn't blank out.
        val effectivePeriod = if (
            inputs.period == ReportsPeriod.CUSTOM && inputs.customRange == null
        ) {
            ReportsPeriod.WEEK
        } else {
            inputs.period
        }
        val revenueSummary = if (hasAnyOrders) {
            RevenueCalculator.computeSummary(
                orders = orders,
                period = effectivePeriod,
                today = today,
                timeZone = timeZone,
                customRange = inputs.customRange
            )
        } else {
            null
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
        val allTimeSummary = if (hasAnyOrders) {
            RevenueCalculator.allTimeSummary(orders, customers)
        } else {
            null
        }

        _state.update {
            it.copy(
                isLoading = false,
                isPremium = inputs.isPremium,
                selectedPeriod = inputs.period,
                customRange = inputs.customRange,
                hasAnyOrders = hasAnyOrders,
                revenueSummary = revenueSummary,
                kpiSummary = kpiSummary,
                productionCounts = productionCounts,
                topCustomers = topCustomers,
                debtors = debtors,
                allTimeSummary = allTimeSummary,
                errorMessage = error
            )
        }
    }
}

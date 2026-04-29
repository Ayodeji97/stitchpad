package com.danzucker.stitchpad.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.reports.domain.CustomerInsightsCalculator
import com.danzucker.stitchpad.feature.reports.domain.RevenueCalculator
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
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(ReportsState())
    private val periodFlow = MutableStateFlow(ReportsPeriod.WEEK)

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
            is ReportsAction.OnTopCustomerClick -> emitEvent(
                ReportsEvent.NavigateToCustomerDetail(action.customerId)
            )
            is ReportsAction.OnDebtorClick -> emitEvent(
                ReportsEvent.NavigateToCustomerDetail(action.customerId)
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
                periodFlow
            ) { ordersResult, customersResult, period ->
                Triple(ordersResult, customersResult, period)
            }.collect { (ordersResult, customersResult, period) ->
                recompute(
                    ordersResult = ordersResult,
                    customersResult = customersResult,
                    period = period
                )
            }
        }
    }

    private fun recompute(
        ordersResult: Result<List<Order>, com.danzucker.stitchpad.core.domain.error.DataError.Network>,
        customersResult: Result<List<Customer>, com.danzucker.stitchpad.core.domain.error.DataError.Network>,
        period: ReportsPeriod
    ) {
        val orders = (ordersResult as? Result.Success)?.data ?: emptyList()
        val customers = (customersResult as? Result.Success)?.data ?: emptyList()
        val error = when {
            ordersResult is Result.Error -> ordersResult.error.toReportsUiText()
            customersResult is Result.Error -> customersResult.error.toReportsUiText()
            else -> null
        }
        val today = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(timeZone).date
        val hasAnyOrders = orders.isNotEmpty()
        val revenueSummary = if (hasAnyOrders) {
            RevenueCalculator.computeSummary(orders, period, today, timeZone)
        } else {
            null
        }
        val topCustomers = CustomerInsightsCalculator.topCustomers(
            orders = orders,
            customers = customers,
            period = period,
            today = today,
            timeZone = timeZone
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
                selectedPeriod = period,
                hasAnyOrders = hasAnyOrders,
                revenueSummary = revenueSummary,
                topCustomers = topCustomers,
                debtors = debtors,
                allTimeSummary = allTimeSummary,
                errorMessage = error
            )
        }
    }
}

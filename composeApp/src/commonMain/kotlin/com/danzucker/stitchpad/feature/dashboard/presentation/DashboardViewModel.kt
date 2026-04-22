package com.danzucker.stitchpad.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val MORNING_CUTOFF_HOUR = 12
private const val AFTERNOON_CUTOFF_HOUR = 17

@OptIn(ExperimentalTime::class)
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
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
            is DashboardAction.OnOrderClick -> viewModelScope.launch {
                _events.send(DashboardEvent.NavigateToOrderDetail(action.orderId))
            }
            DashboardAction.OnSeeAllClick -> viewModelScope.launch {
                _events.send(DashboardEvent.NavigateToOrders)
            }
            DashboardAction.OnOutstandingClick -> viewModelScope.launch {
                _events.send(DashboardEvent.NavigateToOrders)
            }
            DashboardAction.OnNewOrderClick -> viewModelScope.launch {
                _events.send(DashboardEvent.NavigateToOrderForm)
            }
            DashboardAction.OnNewCustomerClick -> viewModelScope.launch {
                _events.send(DashboardEvent.NavigateToCustomerForm)
            }
            DashboardAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val businessName = user.businessName?.takeIf { it.isNotBlank() } ?: user.displayName
            val greeting = computeGreeting()

            combine(
                orderRepository.observeOrders(user.id),
                customerRepository.observeCustomers(user.id)
            ) { ordersResult, customersResult ->
                ordersResult to customersResult
            }.collect { (ordersResult, customersResult) ->
                val orders = (ordersResult as? Result.Success)?.data ?: emptyList()
                val customers = (customersResult as? Result.Success)?.data ?: emptyList()
                val error = when {
                    ordersResult is Result.Error -> ordersResult.error.toDashboardUiText()
                    customersResult is Result.Error -> customersResult.error.toDashboardUiText()
                    else -> null
                }
                val today = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(timeZone).date
                val buckets = computeBuckets(orders, today)
                val isBrandNew = orders.isEmpty() && customers.isEmpty()
                val isAllClear = !isBrandNew && buckets.isAllEmpty()

                _state.update {
                    it.copy(
                        isLoading = false,
                        businessName = businessName,
                        greeting = greeting,
                        todayDate = today,
                        overdue = buckets.overdue,
                        dueToday = buckets.dueToday,
                        ready = buckets.ready,
                        outstandingAmount = buckets.outstandingAmount,
                        outstandingOrderCount = buckets.outstandingOrderCount,
                        isBrandNew = isBrandNew,
                        isAllClear = isAllClear,
                        errorMessage = error
                    )
                }
            }
        }
    }

    private fun computeBuckets(orders: List<Order>, today: LocalDate): Buckets {
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

        return Buckets(
            overdue = overdue,
            dueToday = dueToday,
            ready = ready,
            outstandingAmount = unpaid.sumOf { it.balanceRemaining },
            outstandingOrderCount = unpaid.size
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

    private fun Long.toLocalDate(tz: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).date

    private fun Order.toRow(today: LocalDate): DashboardOrderRow {
        val garment = items.firstOrNull()?.garmentType?.simpleLabel().orEmpty()
        val deadlineDate = deadline?.toLocalDate(timeZone)
        val secondary = if (deadlineDate != null && deadlineDate < today) {
            val daysLate = deadlineDate.daysUntil(today)
            "${daysLate}d late"
        } else {
            null
        }
        return DashboardOrderRow(
            orderId = id,
            customerName = customerName,
            primaryLabel = garment,
            secondaryLabel = secondary
        )
    }

    private data class Buckets(
        val overdue: List<DashboardOrderRow>,
        val dueToday: List<DashboardOrderRow>,
        val ready: List<DashboardOrderRow>,
        val outstandingAmount: Double,
        val outstandingOrderCount: Int
    ) {
        fun isAllEmpty(): Boolean = overdue.isEmpty() &&
            dueToday.isEmpty() &&
            ready.isEmpty() &&
            outstandingOrderCount == 0
    }
}

private fun GarmentType.simpleLabel(): String =
    name.lowercase().split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }

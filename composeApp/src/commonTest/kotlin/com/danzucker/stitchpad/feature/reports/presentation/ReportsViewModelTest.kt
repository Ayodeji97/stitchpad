package com.danzucker.stitchpad.feature.reports.presentation

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.reports.domain.model.ReportsPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz).toEpochMilliseconds()

    private fun TestScope.createViewModel(
        nowMillis: () -> Long = { millisAt(today, hour = 9) }
    ): ReportsViewModel {
        val vm = ReportsViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            nowMillis = nowMillis,
            timeZone = tz
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private suspend fun signIn() {
        authRepository.signUpWithEmail("t@t.com", "pass", "Tailor")
    }

    private fun customer(id: String, name: String, phone: String = "+2348012345678") = Customer(
        id = id,
        userId = "test-uid",
        name = name,
        phone = phone
    )

    private fun order(
        id: String = "o",
        customerId: String = "c1",
        updatedAt: Long = millisAt(today),
        totalPrice: Double = 10_000.0,
        balanceRemaining: Double = 0.0,
        status: OrderStatus = OrderStatus.PENDING
    ): Order = Order(
        id = id,
        userId = "test-uid",
        customerId = customerId,
        customerName = "Test",
        items = listOf(
            OrderItem(id = "i", garmentType = GarmentType.AGBADA, description = "", price = totalPrice)
        ),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = totalPrice,
        depositPaid = totalPrice - balanceRemaining,
        balanceRemaining = balanceRemaining,
        deadline = null,
        notes = null,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    @Test
    fun unauthedUserDoesNotCrashAndStaysEmpty() = runTest {
        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.hasAnyOrders)
        assertNull(vm.state.value.revenueSummary)
        assertTrue(vm.state.value.topCustomers.isEmpty())
        assertTrue(vm.state.value.debtors.isEmpty())
    }

    @Test
    fun authedUserEmptyDataPopulatesEmptyState() = runTest {
        signIn()

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.hasAnyOrders)
        assertNull(vm.state.value.revenueSummary)
        assertTrue(vm.state.value.topCustomers.isEmpty())
        assertTrue(vm.state.value.debtors.isEmpty())
    }

    @Test
    fun authedUserPopulatedDataPopulatesAllSections() = runTest {
        signIn()
        customerRepository.customersList = listOf(
            customer("c1", "Adaeze"),
            customer("c2", "Bola")
        )
        orderRepository.ordersList = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 30_000.0, balanceRemaining = 10_000.0),
            order(id = "o2", customerId = "c2", totalPrice = 50_000.0, balanceRemaining = 50_000.0)
        )

        val vm = createViewModel()

        assertTrue(vm.state.value.hasAnyOrders)
        val summary = vm.state.value.revenueSummary
        assertNotNull(summary)
        // c1 paid 20k, c2 paid 0k → total collected = 20k
        assertEquals(20_000.0, summary.current)
        // Top customers — only customers who collected anything
        assertEquals(listOf("Adaeze"), vm.state.value.topCustomers.map { it.customerName })
        // Debtors — both have unpaid balance
        assertEquals(setOf("Adaeze", "Bola"), vm.state.value.debtors.map { it.customerName }.toSet())
    }

    @Test
    fun onPeriodSelectedSwitchesToMonthAndRecomputes() = runTest {
        signIn()
        customerRepository.customersList = listOf(customer("c1", "Adaeze"))
        // Order from 3 months ago — outside week window, inside month-and-back sparkline
        orderRepository.ordersList = listOf(
            order(id = "o1", customerId = "c1",
                updatedAt = millisAt(LocalDate(2026, 4, 1)),
                totalPrice = 100_000.0, balanceRemaining = 0.0)
        )

        val vm = createViewModel()
        assertEquals(ReportsPeriod.WEEK, vm.state.value.selectedPeriod)

        vm.onAction(ReportsAction.OnPeriodSelected(ReportsPeriod.MONTH))

        assertEquals(ReportsPeriod.MONTH, vm.state.value.selectedPeriod)
        // April 1 is in current month → counted
        assertEquals(100_000.0, vm.state.value.revenueSummary?.current)
    }

    @Test
    fun onTopCustomerClickEmitsNavigationEvent() = runTest {
        signIn()
        val vm = createViewModel()

        vm.onAction(ReportsAction.OnTopCustomerClick("c1"))

        val event = vm.events.first()
        assertIs<ReportsEvent.NavigateToCustomerDetail>(event)
        assertEquals("c1", event.customerId)
    }

    @Test
    fun onDebtorClickEmitsNavigationEvent() = runTest {
        signIn()
        val vm = createViewModel()

        vm.onAction(ReportsAction.OnDebtorClick("c2"))

        val event = vm.events.first()
        assertIs<ReportsEvent.NavigateToCustomerDetail>(event)
        assertEquals("c2", event.customerId)
    }

    @Test
    fun orderRepositoryErrorSetsErrorMessage() = runTest {
        signIn()
        orderRepository.shouldReturnError = DataError.Network.NO_INTERNET

        val vm = createViewModel()

        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun onErrorDismissClearsErrorMessage() = runTest {
        signIn()
        orderRepository.shouldReturnError = DataError.Network.NO_INTERNET
        val vm = createViewModel()
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(ReportsAction.OnErrorDismiss)

        assertNull(vm.state.value.errorMessage)
    }
}

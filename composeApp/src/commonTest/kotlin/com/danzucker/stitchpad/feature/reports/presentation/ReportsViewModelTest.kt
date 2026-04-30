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
import com.danzucker.stitchpad.feature.billing.data.InMemoryEntitlementsRepository
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
    private lateinit var entitlementsRepository: InMemoryEntitlementsRepository

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        entitlementsRepository = InMemoryEntitlementsRepository()
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
            entitlementsRepository = entitlementsRepository,
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
        assertNull(vm.state.value.kpiSummary)
        assertTrue(vm.state.value.topCustomers.items.isEmpty())
        assertTrue(vm.state.value.debtors.items.isEmpty())
    }

    @Test
    fun authedUserEmptyDataPopulatesEmptyState() = runTest {
        signIn()

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.hasAnyOrders)
        assertNull(vm.state.value.kpiSummary)
        assertTrue(vm.state.value.topCustomers.items.isEmpty())
        assertTrue(vm.state.value.debtors.items.isEmpty())
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
        val kpis = vm.state.value.kpiSummary
        assertNotNull(kpis)
        // 80k earned (30k + 50k), 20k actually collected (c1 paid 20k of 30k).
        assertEquals(80_000.0, kpis.revenue.current)
        assertEquals(20_000.0, kpis.collected.current)
        // 60k outstanding (10k from c1 + 50k from c2)
        assertEquals(60_000.0, kpis.outstanding.current)
        assertEquals(2.0, kpis.orders.current)
        // Top customers — only customers who collected anything
        assertEquals(listOf("Adaeze"), vm.state.value.topCustomers.items.map { it.customerName })
        // Debtors — both have unpaid balance
        assertEquals(setOf("Adaeze", "Bola"), vm.state.value.debtors.items.map { it.customerName }.toSet())
    }

    @Test
    fun authedUserPopulatedDataPopulatesProductionCounts() = runTest {
        signIn()
        customerRepository.customersList = listOf(customer("c1", "Adaeze"))
        orderRepository.ordersList = listOf(
            order(id = "o1", customerId = "c1", totalPrice = 30_000.0,
                balanceRemaining = 10_000.0, status = OrderStatus.PENDING),
            order(id = "o2", customerId = "c1", totalPrice = 50_000.0,
                balanceRemaining = 0.0, status = OrderStatus.DELIVERED)
        )

        val vm = createViewModel()

        val production = vm.state.value.productionCounts
        assertNotNull(production)
        assertEquals(1, production.pending)
        assertEquals(1, production.delivered)
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
        // April 1 is in current month → counted in collected KPI
        assertEquals(100_000.0, vm.state.value.kpiSummary?.collected?.current)
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

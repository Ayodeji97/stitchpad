package com.danzucker.stitchpad.feature.dashboard.presentation

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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
import kotlinx.datetime.atStartOfDayIn
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
class DashboardViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository

    private val testTimeZone = TimeZone.UTC
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

    private fun millisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(testTimeZone).toEpochMilliseconds()

    private fun TestScope.createViewModel(
        nowMillis: () -> Long = { millisAt(today, hour = 9) }
    ): DashboardViewModel {
        val vm = DashboardViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            nowMillis = nowMillis,
            timeZone = testTimeZone
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun epochMillisAt(date: LocalDate): Long =
        date.atStartOfDayIn(testTimeZone).toEpochMilliseconds()

    private fun fakeOrder(
        id: String = "o1",
        status: OrderStatus = OrderStatus.PENDING,
        deadline: LocalDate? = null,
        customerName: String = "Ada Lovelace",
        totalPrice: Double = 0.0,
        depositPaid: Double = 0.0,
        balanceRemaining: Double = 0.0,
        garment: GarmentType = GarmentType.AGBADA
    ) = Order(
        id = id,
        userId = "test-uid",
        customerId = "c1",
        customerName = customerName,
        items = listOf(
            OrderItem(
                id = "i1",
                garmentType = garment,
                description = "",
                price = totalPrice
            )
        ),
        status = status,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = totalPrice,
        depositPaid = depositPaid,
        balanceRemaining = balanceRemaining,
        deadline = deadline?.let(::epochMillisAt),
        notes = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun fakeCustomer(id: String = "c1", name: String = "Ada Lovelace") =
        Customer(id = id, userId = "test-uid", name = name, phone = "+2348012345678")

    private suspend fun signIn(businessName: String? = "Ade's Fashions") {
        authRepository.signUpWithEmail("t@t.com", "pass", "Ade Bello")
        authRepository.currentBusinessName = businessName
    }

    // --- Brand-new detection ---

    @Test
    fun emptyOrdersAndCustomers_setsIsBrandNewTrue() = runTest {
        signIn()
        val vm = createViewModel()

        assertTrue(vm.state.value.isBrandNew)
        assertFalse(vm.state.value.isAllClear)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun hasCustomersButNoOrders_setsIsBrandNewFalse() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()

        assertFalse(vm.state.value.isBrandNew)
    }

    @Test
    fun hasOrdersButNoCustomers_setsIsBrandNewFalse() = runTest {
        signIn()
        orderRepository.ordersList = listOf(fakeOrder())

        val vm = createViewModel()

        assertFalse(vm.state.value.isBrandNew)
    }

    // --- Overdue bucket ---

    @Test
    fun overdueBucket_includesNonDeliveredOrdersWithPastDeadlines() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "late1", deadline = LocalDate(2026, 4, 20), status = OrderStatus.PENDING),
            fakeOrder(id = "late2", deadline = LocalDate(2026, 4, 21), status = OrderStatus.IN_PROGRESS)
        )

        val vm = createViewModel()

        val overdueIds = vm.state.value.overdue.map { it.orderId }.toSet()
        assertEquals(setOf("late1", "late2"), overdueIds)
    }

    @Test
    fun overdueBucket_excludesDeliveredOrders() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "delivered", deadline = LocalDate(2026, 4, 20), status = OrderStatus.DELIVERED)
        )

        val vm = createViewModel()

        assertTrue(vm.state.value.overdue.isEmpty())
    }

    @Test
    fun overdueBucket_excludesOrdersDueToday() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "today", deadline = today, status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        assertTrue(vm.state.value.overdue.isEmpty())
    }

    @Test
    fun overdueBucket_excludesOrdersWithoutDeadline() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "nodeadline", deadline = null, status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        assertTrue(vm.state.value.overdue.isEmpty())
    }

    // --- Due today bucket ---

    @Test
    fun dueTodayBucket_includesNonDeliveredOrdersWithTodaysDeadline() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "today1", deadline = today, status = OrderStatus.PENDING),
            fakeOrder(id = "today2", deadline = today, status = OrderStatus.IN_PROGRESS)
        )

        val vm = createViewModel()

        val ids = vm.state.value.dueToday.map { it.orderId }.toSet()
        assertEquals(setOf("today1", "today2"), ids)
    }

    @Test
    fun dueTodayBucket_excludesDeliveredOrders() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "today-delivered", deadline = today, status = OrderStatus.DELIVERED)
        )

        val vm = createViewModel()

        assertTrue(vm.state.value.dueToday.isEmpty())
    }

    // --- Ready for pickup bucket ---

    @Test
    fun readyBucket_includesReadyStatusOrders() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "ready1", status = OrderStatus.READY),
            fakeOrder(id = "pending", status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        val ids = vm.state.value.ready.map { it.orderId }
        assertEquals(listOf("ready1"), ids)
    }

    // --- Outstanding balance ---

    @Test
    fun outstandingAmount_sumsBalancesOfActiveOrdersWithRemainingBalance() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "a", balanceRemaining = 15_000.0, status = OrderStatus.PENDING),
            fakeOrder(id = "b", balanceRemaining = 30_000.0, status = OrderStatus.IN_PROGRESS),
            fakeOrder(id = "c", balanceRemaining = 5_000.0, status = OrderStatus.READY)
        )

        val vm = createViewModel()

        assertEquals(50_000.0, vm.state.value.outstandingAmount)
        assertEquals(3, vm.state.value.outstandingOrderCount)
    }

    @Test
    fun outstandingAmount_excludesDeliveredOrders() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "delivered", balanceRemaining = 50_000.0, status = OrderStatus.DELIVERED),
            fakeOrder(id = "pending", balanceRemaining = 10_000.0, status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        assertEquals(10_000.0, vm.state.value.outstandingAmount)
        assertEquals(1, vm.state.value.outstandingOrderCount)
    }

    @Test
    fun outstandingAmount_excludesOrdersWithZeroBalance() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "paid", balanceRemaining = 0.0, status = OrderStatus.IN_PROGRESS),
            fakeOrder(id = "unpaid", balanceRemaining = 20_000.0, status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        assertEquals(20_000.0, vm.state.value.outstandingAmount)
        assertEquals(1, vm.state.value.outstandingOrderCount)
    }

    // --- All clear ---

    @Test
    fun allClear_whenOrdersExistButAllBucketsEmpty() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "delivered", status = OrderStatus.DELIVERED, balanceRemaining = 0.0)
        )
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()

        assertFalse(vm.state.value.isBrandNew)
        assertTrue(vm.state.value.isAllClear)
        assertTrue(vm.state.value.overdue.isEmpty())
        assertTrue(vm.state.value.dueToday.isEmpty())
        assertTrue(vm.state.value.ready.isEmpty())
    }

    @Test
    fun allClear_isFalse_whenOverdueExists() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(deadline = LocalDate(2026, 4, 20), status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        assertFalse(vm.state.value.isAllClear)
    }

    // --- Greeting ---

    @Test
    fun greeting_beforeNoon_isMorning() = runTest {
        signIn()
        val vm = createViewModel(nowMillis = { millisAt(today, hour = 9) })
        assertEquals(Greeting.MORNING, vm.state.value.greeting)
    }

    @Test
    fun greeting_afternoon_isAfternoon() = runTest {
        signIn()
        val vm = createViewModel(nowMillis = { millisAt(today, hour = 14) })
        assertEquals(Greeting.AFTERNOON, vm.state.value.greeting)
    }

    @Test
    fun greeting_evening_isEvening() = runTest {
        signIn()
        val vm = createViewModel(nowMillis = { millisAt(today, hour = 20) })
        assertEquals(Greeting.EVENING, vm.state.value.greeting)
    }

    // --- Business name ---

    @Test
    fun businessName_populatesFromAuthUser() = runTest {
        signIn(businessName = "Ade's Fashions")
        val vm = createViewModel()
        assertEquals("Ade's Fashions", vm.state.value.businessName)
    }

    @Test
    fun businessName_fallsBackToDisplayName_whenNull() = runTest {
        signIn(businessName = null)
        val vm = createViewModel()
        assertEquals("Ade Bello", vm.state.value.businessName)
    }

    // --- Navigation events ---

    @Test
    fun onOrderClick_emitsNavigateToOrderDetail_withCorrectId() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnOrderClick("order-42"))
        val event = vm.events.first()
        assertIs<DashboardEvent.NavigateToOrderDetail>(event)
        assertEquals("order-42", event.orderId)
    }

    @Test
    fun onSeeAllClick_emitsNavigateToOrders() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnSeeAllClick)
        assertIs<DashboardEvent.NavigateToOrders>(vm.events.first())
    }

    @Test
    fun onOutstandingClick_emitsNavigateToOrders() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnOutstandingClick)
        assertIs<DashboardEvent.NavigateToOrders>(vm.events.first())
    }

    @Test
    fun onNewOrderClick_emitsNavigateToOrderForm() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnNewOrderClick)
        assertIs<DashboardEvent.NavigateToOrderForm>(vm.events.first())
    }

    @Test
    fun onNewCustomerClick_emitsNavigateToCustomerForm() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnNewCustomerClick)
        assertIs<DashboardEvent.NavigateToCustomerForm>(vm.events.first())
    }

    // --- No auth user ---

    @Test
    fun noAuthUser_setsIsLoadingFalse_andLeavesStateEmpty() = runTest {
        // no signIn
        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.businessName)
        assertTrue(vm.state.value.overdue.isEmpty())
    }

    // --- Errors ---

    @Test
    fun orderRepositoryError_setsErrorMessage() = runTest {
        signIn()
        orderRepository.shouldReturnError = DataError.Network.UNKNOWN

        val vm = createViewModel()

        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        signIn()
        orderRepository.shouldReturnError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(DashboardAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }
}

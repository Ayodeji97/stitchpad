package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import com.danzucker.stitchpad.feature.goals.data.FakeWeeklyGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var weeklyGoalRepository: FakeWeeklyGoalRepository
    private lateinit var smartUsageStore: FakeSmartUsageStore

    private val testTimeZone = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        userRepository = FakeUserRepository()
        weeklyGoalRepository = FakeWeeklyGoalRepository()
        smartUsageStore = FakeSmartUsageStore()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun millisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(testTimeZone).toEpochMilliseconds()

    private fun TestScope.createViewModel(
        nowMillis: () -> Long = { millisAt(today, hour = 9) },
        entitlements: EntitlementsProvider = FakeEntitlementsProvider(),
    ): DashboardViewModel {
        val vm = DashboardViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            userRepository = userRepository,
            weeklyGoalRepository = weeklyGoalRepository,
            smartUsageStore = smartUsageStore,
            entitlements = entitlements,
            nowMillis = nowMillis,
            timeZone = testTimeZone
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private class FakeSmartUsageStore : com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore {
        private val flow = MutableStateFlow<Int?>(null)
        override val remainingFreeQuota: StateFlow<Int?> = flow
        override fun update(remaining: Int?) {
            flow.value = remaining
        }
    }

    private class FakeEntitlementsProvider(
        entitlements: UserEntitlements = defaultEntitlements(),
    ) : EntitlementsProvider {
        private val _flow = MutableStateFlow(entitlements)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements = _flow.value
    }

    private companion object {
        fun defaultEntitlements() = UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = Int.MAX_VALUE,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
        )
    }

    private fun epochMillisAt(date: LocalDate): Long =
        date.atStartOfDayIn(testTimeZone).toEpochMilliseconds()

    private fun depositPayment(amount: Double, recordedAt: Long = 0L): Payment = Payment(
        id = "test-deposit",
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = recordedAt,
    )

    // Default deadline is 9 days past `today` (2026-04-22 → 2026-05-01).
    // Picked to land safely outside overdue/dueToday/ready bucketing AND
    // outside the FirstCustomer "no-due-date" sub-state so existing tests
    // continue exercising their intended state. Tests that specifically
    // need a deadline-less order pass `deadline = null` explicitly.
    private fun fakeOrder(
        id: String = "o1",
        customerId: String = "c1",
        status: OrderStatus = OrderStatus.PENDING,
        deadline: LocalDate? = LocalDate(2026, 5, 1),
        customerName: String = "Ada Lovelace",
        totalPrice: Double? = null,
        depositPaid: Double = 0.0,
        balanceRemaining: Double = 0.0,
        garment: GarmentType = GarmentType.AGBADA,
        statusEnteredOn: LocalDate? = null,
        createdAt: Long = 0L,
        updatedAt: Long = 0L
    ): Order {
        val resolvedTotalPrice = totalPrice ?: (depositPaid + balanceRemaining)
        val statusHistory = if (statusEnteredOn != null) {
            listOf(StatusChange(status = status, changedAt = epochMillisAt(statusEnteredOn)))
        } else {
            emptyList()
        }
        return Order(
            id = id,
            userId = "test-uid",
            customerId = customerId,
            customerName = customerName,
            items = listOf(
                OrderItem(
                    id = "i1",
                    garmentType = garment,
                    description = "",
                    price = resolvedTotalPrice
                )
            ),
            status = status,
            priority = OrderPriority.NORMAL,
            statusHistory = statusHistory,
            totalPrice = resolvedTotalPrice,
            payments = if (depositPaid > 0.0) listOf(depositPayment(depositPaid)) else emptyList(),
            deadline = deadline?.let(::epochMillisAt),
            notes = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun fakeCustomer(id: String = "c1", name: String = "Ada Lovelace") =
        Customer(id = id, userId = "test-uid", name = name, phone = "+2348012345678")

    private suspend fun signIn(businessName: String? = "Ade's Fashions") {
        authRepository.signUpWithEmail("t@t.com", "pass", "Ade Bello")
        authRepository.currentBusinessName = businessName
    }

    // --- Brand-new detection ---

    @Test
    fun emptyOrdersAndCustomers_resolvesBrandNew() = runTest {
        signIn()
        val vm = createViewModel()

        assertEquals(DashboardUiState.BrandNew, vm.state.value.uiState)
    }

    @Test
    fun hasCustomersButNoOrders_resolvesNonBrandNew() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()

        assertNotEquals(DashboardUiState.BrandNew, vm.state.value.uiState)
    }

    @Test
    fun hasOrdersButNoCustomers_resolvesNonBrandNew() = runTest {
        signIn()
        orderRepository.ordersList = listOf(fakeOrder())

        val vm = createViewModel()

        assertNotEquals(DashboardUiState.BrandNew, vm.state.value.uiState)
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

    // --- Quiet day (formerly "all clear") ---

    @Test
    fun quietDay_whenOrdersExistButAllBucketsEmpty() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "delivered", status = OrderStatus.DELIVERED, balanceRemaining = 0.0)
        )
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()

        assertEquals(DashboardUiState.QuietDay, vm.state.value.uiState)
        assertTrue(vm.state.value.overdue.isEmpty())
        assertTrue(vm.state.value.dueToday.isEmpty())
        assertTrue(vm.state.value.ready.isEmpty())
    }

    @Test
    fun quietDay_doesNotResolve_whenOverdueExists() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(deadline = LocalDate(2026, 4, 20), status = OrderStatus.PENDING)
        )

        val vm = createViewModel()

        assertNotEquals(DashboardUiState.QuietDay, vm.state.value.uiState)
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
    fun businessName_isNull_whenUserHasNotSetWorkshopName() = runTest {
        signIn(businessName = null)
        val vm = createViewModel()
        assertNull(vm.state.value.businessName)
    }

    @Test
    fun firstName_isExtractedFromDisplayName() = runTest {
        signIn() // displayName = "Ade Bello"
        val vm = createViewModel()
        assertEquals("Ade", vm.state.value.firstName)
    }

    @Test
    fun firstName_handlesSingleTokenDisplayName() = runTest {
        authRepository.signUpWithEmail("t@t.com", "pass", "Daniel")
        authRepository.currentBusinessName = null
        val vm = createViewModel()
        assertEquals("Daniel", vm.state.value.firstName)
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
    fun noAuthUser_resolvesBrandNew_andLeavesStateEmpty() = runTest {
        // no signIn
        val vm = createViewModel()

        assertEquals(DashboardUiState.BrandNew, vm.state.value.uiState)
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

    // --- Next Best Actions ---

    @Test
    fun nba_collectOverdue_firesWhenOrderIsPastDeadlineWithUnpaidBalance() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                deadline = LocalDate(2026, 4, 18),
                status = OrderStatus.IN_PROGRESS,
                balanceRemaining = 200_000.0
            )
        )
        val vm = createViewModel()

        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.CollectOverdue, nba.type)
        assertEquals("o1", nba.orderId)
        assertEquals(200_000.0, nba.balanceAmount)
        assertEquals(4, nba.daysCount)
    }

    @Test
    fun nba_collectOnReady_firesWhenReadyWithUnpaidBalance() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.READY,
                balanceRemaining = 50_000.0,
                statusEnteredOn = LocalDate(2026, 4, 20)
            )
        )
        val vm = createViewModel()

        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.CollectOnReady, nba.type)
        assertEquals(2, nba.daysCount)
    }

    @Test
    fun nba_finishStale_firesWhenInProgressLongerThanThreshold() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.IN_PROGRESS,
                balanceRemaining = 0.0,
                deadline = LocalDate(2026, 5, 1),
                statusEnteredOn = LocalDate(2026, 4, 12)
            )
        )
        val vm = createViewModel()

        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.FinishStale, nba.type)
        assertEquals(10, nba.daysCount)
    }

    @Test
    fun nba_deliverStale_firesWhenReadyForMoreThanThreshold_withZeroBalance() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.READY,
                balanceRemaining = 0.0,
                statusEnteredOn = LocalDate(2026, 4, 17)
            )
        )
        val vm = createViewModel()

        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.DeliverStale, nba.type)
        assertEquals(5, nba.daysCount)
    }

    @Test
    fun nba_collectDeposit_firesWhenPendingWithNoDeposit() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.PENDING,
                totalPrice = 120_000.0,
                depositPaid = 0.0,
                balanceRemaining = 120_000.0,
                deadline = LocalDate(2026, 5, 30)
            )
        )
        val vm = createViewModel()

        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.CollectDeposit, nba.type)
        assertEquals(120_000.0, nba.balanceAmount)
    }

    @Test
    fun nba_startSoon_firesWhenPendingWithDeadlineWithinSevenDays() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.PENDING,
                totalPrice = 100_000.0,
                depositPaid = 50_000.0,
                balanceRemaining = 50_000.0,
                deadline = LocalDate(2026, 4, 26)
            )
        )
        val vm = createViewModel()

        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.StartSoon, nba.type)
        assertEquals(4, nba.daysCount)
    }

    @Test
    fun nba_excludesDeliveredOrders() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.DELIVERED,
                balanceRemaining = 100_000.0,
                deadline = LocalDate(2026, 4, 18)
            )
        )
        val vm = createViewModel()

        assertTrue(vm.state.value.nextBestActions.isEmpty())
    }

    @Test
    fun nba_excludesOrdersForCustomersWithoutPhone() = runTest {
        signIn()
        customerRepository.customersList = listOf(
            Customer(id = "c1", userId = "test-uid", name = "Ada", phone = "")
        )
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.READY,
                balanceRemaining = 50_000.0,
                statusEnteredOn = LocalDate(2026, 4, 20)
            )
        )
        val vm = createViewModel()

        assertTrue(vm.state.value.nextBestActions.isEmpty())
    }

    @Test
    fun nba_ranksByImpactAndCapsAtFive() = runTest {
        signIn()
        customerRepository.customersList = listOf(
            fakeCustomer(id = "c1", name = "Customer 1"),
            fakeCustomer(id = "c2", name = "Customer 2"),
            fakeCustomer(id = "c3", name = "Customer 3"),
            fakeCustomer(id = "c4", name = "Customer 4"),
            fakeCustomer(id = "c5", name = "Customer 5"),
            fakeCustomer(id = "c6", name = "Customer 6")
        )
        orderRepository.ordersList = listOf(
            // StartSoon — should be dropped at the cap
            fakeOrder(
                id = "start",
                customerId = "c6",
                status = OrderStatus.PENDING,
                totalPrice = 100_000.0,
                depositPaid = 100_000.0,
                balanceRemaining = 0.0,
                deadline = LocalDate(2026, 4, 26)
            ),
            // CollectDeposit
            fakeOrder(
                id = "deposit",
                customerId = "c5",
                status = OrderStatus.PENDING,
                totalPrice = 60_000.0,
                depositPaid = 0.0,
                balanceRemaining = 60_000.0,
                deadline = LocalDate(2026, 5, 30)
            ),
            // DeliverStale
            fakeOrder(
                id = "deliver",
                customerId = "c4",
                status = OrderStatus.READY,
                balanceRemaining = 0.0,
                statusEnteredOn = LocalDate(2026, 4, 17)
            ),
            // FinishStale
            fakeOrder(
                id = "finish",
                customerId = "c3",
                status = OrderStatus.IN_PROGRESS,
                balanceRemaining = 0.0,
                deadline = LocalDate(2026, 5, 1),
                statusEnteredOn = LocalDate(2026, 4, 12)
            ),
            // CollectOnReady
            fakeOrder(
                id = "collectReady",
                customerId = "c2",
                status = OrderStatus.READY,
                balanceRemaining = 50_000.0,
                statusEnteredOn = LocalDate(2026, 4, 20)
            ),
            // CollectOverdue
            fakeOrder(
                id = "collectOverdue",
                customerId = "c1",
                status = OrderStatus.IN_PROGRESS,
                balanceRemaining = 200_000.0,
                deadline = LocalDate(2026, 4, 18)
            )
        )
        val vm = createViewModel()

        val nbas = vm.state.value.nextBestActions
        assertEquals(5, nbas.size)
        assertEquals(NextBestActionType.CollectOverdue, nbas[0].type)
        assertEquals(NextBestActionType.CollectOnReady, nbas[1].type)
        assertEquals(NextBestActionType.FinishStale, nbas[2].type)
        assertEquals(NextBestActionType.DeliverStale, nbas[3].type)
        assertEquals(NextBestActionType.CollectDeposit, nbas[4].type)
        assertTrue(nbas.none { it.type == NextBestActionType.StartSoon })
    }

    // --- Pipeline buckets ---

    @Test
    fun pipeline_inProgress_excludesTriageAndIncludesActiveInProgress() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            // overdue → excluded
            fakeOrder(
                id = "ov",
                status = OrderStatus.IN_PROGRESS,
                deadline = LocalDate(2026, 4, 20)
            ),
            // due today → excluded
            fakeOrder(
                id = "today",
                status = OrderStatus.IN_PROGRESS,
                deadline = today
            ),
            // pipeline
            fakeOrder(
                id = "p1",
                status = OrderStatus.IN_PROGRESS,
                deadline = LocalDate(2026, 5, 5)
            ),
            fakeOrder(
                id = "p2",
                status = OrderStatus.IN_PROGRESS,
                deadline = null
            )
        )
        val vm = createViewModel()

        val ids = vm.state.value.pipelineInProgress.map { it.orderId }
        assertEquals(listOf("p1", "p2"), ids)
        assertEquals(2, vm.state.value.pipelineInProgressTotal)
    }

    @Test
    fun pipeline_pending_includesPendingNotInTriage() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "p1", status = OrderStatus.PENDING, deadline = LocalDate(2026, 5, 1)),
            fakeOrder(id = "p2", status = OrderStatus.PENDING, deadline = LocalDate(2026, 5, 10)),
            // ready → excluded
            fakeOrder(id = "r1", status = OrderStatus.READY)
        )
        val vm = createViewModel()

        val ids = vm.state.value.pipelinePending.map { it.orderId }
        assertEquals(listOf("p1", "p2"), ids)
        assertEquals(2, vm.state.value.pipelinePendingTotal)
        assertTrue(vm.state.value.pipelineInProgress.isEmpty())
    }

    @Test
    fun pipeline_capsPreviewAtThreeButReportsTotal() = runTest {
        signIn()
        orderRepository.ordersList = (1..7).map { i ->
            fakeOrder(
                id = "p$i",
                status = OrderStatus.PENDING,
                deadline = LocalDate(2026, 5, i)
            )
        }
        val vm = createViewModel()

        assertEquals(3, vm.state.value.pipelinePending.size)
        assertEquals(7, vm.state.value.pipelinePendingTotal)
        assertEquals(listOf("p1", "p2", "p3"), vm.state.value.pipelinePending.map { it.orderId })
    }

    @Test
    fun pipeline_excludesDeliveredOrders() = runTest {
        signIn()
        orderRepository.ordersList = listOf(
            fakeOrder(id = "delivered", status = OrderStatus.DELIVERED)
        )
        val vm = createViewModel()

        assertTrue(vm.state.value.pipelineInProgress.isEmpty())
        assertTrue(vm.state.value.pipelinePending.isEmpty())
    }

    // --- New action wiring ---

    @Test
    fun onNextActionPrimaryClick_emitsLaunchWhatsApp_forCollectActions() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.READY,
                balanceRemaining = 50_000.0,
                statusEnteredOn = LocalDate(2026, 4, 20)
            )
        )
        val vm = createViewModel()
        val nba = vm.state.value.nextBestActions.single()

        vm.onAction(DashboardAction.OnNextActionPrimaryClick(nba))
        val event = vm.events.first()
        assertIs<DashboardEvent.LaunchWhatsApp>(event)
        assertEquals(nba, event.action)
    }

    @Test
    fun onNextActionPrimaryClick_emitsNavigateToOrderDetail_forNonCollectActions() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "o1",
                status = OrderStatus.IN_PROGRESS,
                balanceRemaining = 0.0,
                deadline = LocalDate(2026, 5, 1),
                statusEnteredOn = LocalDate(2026, 4, 12)
            )
        )
        val vm = createViewModel()
        val nba = vm.state.value.nextBestActions.single()
        assertEquals(NextBestActionType.FinishStale, nba.type)

        vm.onAction(DashboardAction.OnNextActionPrimaryClick(nba))
        val event = vm.events.first()
        assertIs<DashboardEvent.NavigateToOrderDetail>(event)
        assertEquals("o1", event.orderId)
    }

    // --- Focus variant resolution (priority order) ---

    @Test
    fun firstCustomerNoOrders_resolvesFocusToFirstOrder() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer(name = "Ola Kunle"))

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.FirstOrder,
            vm.state.value.focusVariant
        )
        assertNotNull(vm.state.value.focusCtaLabel)
    }

    @Test
    fun overdueOrder_resolvesFocusToFocusBusy() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "late",
                deadline = LocalDate(2026, 4, 18),
                status = OrderStatus.PENDING,
                balanceRemaining = 50_000.0,
                customerName = "Mr Kola"
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Focus,
            vm.state.value.focusVariant
        )
    }

    @Test
    fun nbaOnly_resolvesFocusToEarn_whenNoTriage() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        // PENDING order paid in full (no triage), with deadline within 7 days
        // → triggers StartSoon NBA without entering the unpaid/overdue buckets.
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "starting-soon",
                status = OrderStatus.PENDING,
                deadline = LocalDate(2026, 4, 27),
                totalPrice = 80_000.0,
                depositPaid = 80_000.0,
                balanceRemaining = 0.0
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Earn,
            vm.state.value.focusVariant
        )
    }

    @Test
    fun pipelineOnly_resolvesFocusToSteady_whenNoTriageOrNba() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        // IN_PROGRESS, paid in full (no unpaid triage), deadline far out (no NBA).
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "moving",
                status = OrderStatus.IN_PROGRESS,
                deadline = LocalDate(2026, 5, 30),
                totalPrice = 80_000.0,
                depositPaid = 80_000.0,
                balanceRemaining = 0.0,
                statusEnteredOn = LocalDate(2026, 4, 20)
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Steady,
            vm.state.value.focusVariant
        )
    }

    @Test
    fun noOrdersWithDeliveredHistory_resolvesFocusToQuiet() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(id = "done", status = OrderStatus.DELIVERED)
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Quiet,
            vm.state.value.focusVariant
        )
    }

    @Test
    fun triageBeatsNba_inFocusPriority() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        // Both an overdue triage item AND an NBA-eligible deposit candidate.
        // Triage (S6) must win priority over NBA-only (S5).
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "late",
                deadline = LocalDate(2026, 4, 18),
                status = OrderStatus.PENDING,
                balanceRemaining = 50_000.0
            ),
            fakeOrder(
                id = "deposit",
                customerId = "c1",
                status = OrderStatus.PENDING,
                deadline = LocalDate(2026, 5, 30),
                totalPrice = 80_000.0,
                depositPaid = 0.0,
                balanceRemaining = 80_000.0
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Focus,
            vm.state.value.focusVariant
        )
    }

    // --- Reconnect candidates ---

    @Test
    fun reconnectCandidates_excludesCustomerWithNoOrderHistory() = runTest {
        // No-history customers belong on FirstCustomer / onboarding surfaces, not Reconnect.
        signIn()
        customerRepository.customersList = listOf(fakeCustomer(id = "c1", name = "Ola Kunle"))

        val vm = createViewModel()

        assertTrue(vm.state.value.reconnectCandidates.isEmpty())
    }

    @Test
    fun reconnectCandidates_excludesCustomersWithActiveOrders() = runTest {
        signIn()
        customerRepository.customersList = listOf(
            fakeCustomer(id = "active-cust", name = "Active"),
            fakeCustomer(id = "quiet-cust", name = "Quiet")
        )
        orderRepository.ordersList = listOf(
            fakeOrder(id = "o1", customerId = "active-cust", status = OrderStatus.IN_PROGRESS),
            // "quiet-cust" needs a delivered order >=14d old to qualify as a reconnect
            // candidate at all. today = 2026-04-22; 31 days back = 2026-03-22.
            fakeOrder(
                id = "o-quiet",
                customerId = "quiet-cust",
                status = OrderStatus.DELIVERED,
                updatedAt = epochMillisAt(LocalDate(2026, 3, 22))
            )
        )

        val vm = createViewModel()

        val names = vm.state.value.reconnectCandidates.map { it.customerName }.toSet()
        assertEquals(setOf("Quiet"), names)
    }

    // --- Focus CTA routing ---

    @Test
    fun focusCtaClick_inBrandNewVariant_emitsNavigateToAddCustomerFirst() = runTest {
        signIn()
        // No customers, no orders → BrandNew. The hero CTA must route to
        // the gate screen, not OrderForm (no customer exists yet).

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnFocusCtaClick)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToAddCustomerFirst, event)
    }

    @Test
    fun createOrderClick_inBrandNew_emitsNavigateToAddCustomerFirst() = runTest {
        signIn()

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnCreateOrderClick)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToAddCustomerFirst, event)
    }

    @Test
    fun addMeasurementClick_inBrandNew_emitsNavigateToAddCustomerFirst() = runTest {
        signIn()

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnAddMeasurementClick)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToAddCustomerFirst, event)
    }

    @Test
    fun addMeasurementClick_outsideBrandNew_emitsNavigateToCustomers() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnAddMeasurementClick)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToCustomers, event)
    }

    @Test
    fun focusCtaClick_inFirstOrderVariant_emitsNavigateToOrderForm() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnFocusCtaClick)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToOrderForm, event)
    }

    @Test
    fun setupChecklistAdvance_emitsNavigateToOrderForm() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnSetupChecklistAdvance)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToOrderForm, event)
    }

    @Test
    fun customerReadyClick_emitsNavigateToCustomerDetail() = runTest {
        signIn()
        val customer = fakeCustomer(id = "cust-1")
        customerRepository.customersList = listOf(customer)

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnCustomerReadyClick("cust-1"))

        val event = vm.events.first()
        assertIs<DashboardEvent.NavigateToCustomerDetail>(event)
        assertEquals("cust-1", event.customerId)
    }

    @Test
    fun customerReadyMessageClick_emitsLaunchWhatsAppForReconnect() = runTest {
        signIn()
        // FirstCustomer state populates state.customerReady; the message
        // click looks up the customer by ID and synthesizes a candidate
        // for the existing WhatsApp launch event.
        customerRepository.customersList = listOf(fakeCustomer(id = "cust-1", name = "Bola"))

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnCustomerReadyMessageClick("cust-1"))

        val event = vm.events.first()
        assertIs<DashboardEvent.LaunchWhatsAppForReconnect>(event)
        assertEquals("cust-1", event.candidate.customerId)
        assertEquals("Bola", event.candidate.customerName)
    }

    @Test
    fun focusCtaClick_inFocusBusyVariant_emitsNavigateToFirstUrgentOrder() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(id = "late", deadline = LocalDate(2026, 4, 18), status = OrderStatus.PENDING)
        )

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnFocusCtaClick)

        val event = vm.events.first()
        assertIs<DashboardEvent.NavigateToOrderDetail>(event)
        assertEquals("late", event.orderId)
    }

    // --- Weekly goal ---

    @Test
    fun weeklyGoal_isNull_whenUserHasNotSetOne() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()

        assertNull(vm.state.value.weeklyGoal)
    }

    @Test
    fun weeklyGoal_reflectsRepositoryGoal_whenSet() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        weeklyGoalRepository.storedGoal = com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal(
            targetAmount = 500_000.0,
            updatedAt = 0L
        )

        val vm = createViewModel()

        val goal = vm.state.value.weeklyGoal
        assertNotNull(goal)
        assertEquals(500_000.0, goal.targetAmount)
    }

    // --- DashboardUiState (canonical screen state machine) ---

    @Test
    fun uiState_isBrandNew_whenNoCustomersAndNoOrders() = runTest {
        signIn()

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.BrandNew,
            vm.state.value.uiState
        )
    }

    @Test
    fun uiState_isFirstCustomer_whenCustomersButNoOrders() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.FirstCustomer,
            vm.state.value.uiState
        )
    }

    @Test
    fun uiState_isQuietDay_whenOnlyDeliveredOrders() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(id = "done", status = OrderStatus.DELIVERED)
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.QuietDay,
            vm.state.value.uiState
        )
    }

    @Test
    fun uiState_isBusyDay_whenAnyTriageActive() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "late",
                deadline = LocalDate(2026, 4, 18),
                status = OrderStatus.PENDING,
                balanceRemaining = 50_000.0
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.BusyDay,
            vm.state.value.uiState
        )
    }

    @Test
    fun uiState_isNbaActive_whenNbaButNoTriage() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "starting-soon",
                status = OrderStatus.PENDING,
                deadline = LocalDate(2026, 4, 27),
                totalPrice = 80_000.0,
                depositPaid = 80_000.0,
                balanceRemaining = 0.0
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.NbaActive,
            vm.state.value.uiState
        )
    }

    // --- ReadyForPickup (no overdue / due-today, but at least one ready order) ---

    @Test
    fun uiState_isReadyForPickup_whenOnlyReadyOrdersAndNoOverdueOrDueToday() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "ready1",
                status = OrderStatus.READY,
                statusEnteredOn = LocalDate(2026, 4, 22)
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.ReadyForPickup,
            vm.state.value.uiState
        )
    }

    @Test
    fun uiState_isBusyDay_whenOverdueAndReadyMixed() = runTest {
        // Regression: a mix of overdue + ready still resolves to BusyDay, not ReadyForPickup.
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "late",
                deadline = LocalDate(2026, 4, 18),
                status = OrderStatus.PENDING
            ),
            fakeOrder(
                id = "ready",
                status = OrderStatus.READY,
                statusEnteredOn = LocalDate(2026, 4, 22)
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.BusyDay,
            vm.state.value.uiState
        )
    }

    @Test
    fun readyForPickup_resolvesFocusToPickupVariant_withReadyCount() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer(name = "Ade Yinka"))
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "r1",
                customerName = "Ade Yinka",
                status = OrderStatus.READY,
                statusEnteredOn = LocalDate(2026, 4, 22)
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Pickup,
            vm.state.value.focusVariant
        )
        assertNotNull(vm.state.value.focusHeadline)
        assertNotNull(vm.state.value.focusCtaLabel)
    }

    @Test
    fun busyDay_focusHeadline_excludesReadyFromUrgentCount() = runTest {
        // Regression for the headline math fix: when overdue=1 and ready=2, the
        // urgent-count headline must report "1 order needs attention", not "3" —
        // ready orders are no longer rolled into the urgency total.
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "late",
                deadline = LocalDate(2026, 4, 18),
                status = OrderStatus.PENDING
            ),
            fakeOrder(id = "r1", status = OrderStatus.READY),
            fakeOrder(id = "r2", status = OrderStatus.READY)
        )

        val vm = createViewModel()

        assertEquals(1, vm.state.value.overdue.size)
        assertEquals(2, vm.state.value.ready.size)
        // Headline copy is built via a UiText with the urgentCount as its first arg.
        // We can't render the UiText in commonTest without resources, so assert on
        // the bucket sizes the resolver feeds the headline (overdue + dueToday only).
        assertEquals(0, vm.state.value.dueToday.size)
    }

    @Test
    fun focusCtaClick_inPickupVariant_emitsNavigateToFirstReadyOrder() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "ready-first",
                status = OrderStatus.READY,
                statusEnteredOn = LocalDate(2026, 4, 22)
            ),
            fakeOrder(
                id = "ready-second",
                status = OrderStatus.READY,
                statusEnteredOn = LocalDate(2026, 4, 22)
            )
        )

        val vm = createViewModel()
        vm.onAction(DashboardAction.OnFocusCtaClick)

        val event = vm.events.first()
        assertIs<DashboardEvent.NavigateToOrderDetail>(event)
        assertEquals("ready-first", event.orderId)
    }

    @Test
    fun focusCtaClick_inQuietVariant_withReconnectCandidate_emitsLaunchWhatsAppForReconnect() = runTest {
        signIn()
        // Customer "active" anchors the system into a non-empty orders state (so we land
        // on QuietDay rather than FirstCustomer). Customer "quiet" has a delivered order
        // from 31 days ago (today = 2026-04-22) → eligible reconnect candidate.
        customerRepository.customersList = listOf(
            fakeCustomer(id = "active", name = "Active"),
            fakeCustomer(id = "quiet", name = "Quiet")
        )
        orderRepository.ordersList = listOf(
            fakeOrder(id = "done", customerId = "active", status = OrderStatus.DELIVERED),
            fakeOrder(
                id = "quiet-done",
                customerId = "quiet",
                status = OrderStatus.DELIVERED,
                updatedAt = epochMillisAt(LocalDate(2026, 3, 22))
            )
        )

        val vm = createViewModel()
        // Sanity: this scenario must produce Quiet + a non-empty reconnect list, otherwise
        // the routing test below isn't actually exercising the reconnect branch.
        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Quiet,
            vm.state.value.focusVariant
        )
        assertTrue(vm.state.value.reconnectCandidates.isNotEmpty())

        vm.onAction(DashboardAction.OnFocusCtaClick)

        val event = vm.events.first()
        assertIs<DashboardEvent.LaunchWhatsAppForReconnect>(event)
        assertEquals("Quiet", event.candidate.customerName)
    }

    @Test
    fun focusCtaClick_inQuietVariant_withNoReconnectCandidates_emitsNavigateToOrderForm() = runTest {
        signIn()
        // One customer with one delivered order (default fakeOrder.updatedAt = 0L) →
        // resolves to QuietDay, but the customer is filtered out of the reconnect list
        // because they have order history with daysSinceLastInteraction = 0 < 14.
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(id = "done", status = OrderStatus.DELIVERED)
        )

        val vm = createViewModel()
        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant.Quiet,
            vm.state.value.focusVariant
        )
        assertTrue(vm.state.value.reconnectCandidates.isEmpty())

        vm.onAction(DashboardAction.OnFocusCtaClick)

        val event = vm.events.first()
        assertEquals(DashboardEvent.NavigateToOrderForm, event)
    }

    @Test
    fun uiState_isPipelineSteady_whenPipelineButNoTriageOrNba() = runTest {
        signIn()
        customerRepository.customersList = listOf(fakeCustomer())
        orderRepository.ordersList = listOf(
            fakeOrder(
                id = "moving",
                status = OrderStatus.IN_PROGRESS,
                deadline = LocalDate(2026, 5, 30),
                totalPrice = 80_000.0,
                depositPaid = 80_000.0,
                balanceRemaining = 0.0,
                statusEnteredOn = LocalDate(2026, 4, 20)
            )
        )

        val vm = createViewModel()

        assertEquals(
            com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState.PipelineSteady,
            vm.state.value.uiState
        )
    }

}

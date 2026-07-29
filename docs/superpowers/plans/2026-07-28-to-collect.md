# "To Collect" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "To collect" surface — a dedicated list of Ready/Delivered orders with an outstanding balance, plus a dashboard "You're owed" card that escalates into the Focus hero when a debt is overdue.

**Architecture:** A pure `object CollectionCalculator` (in `feature/collection/domain`) is the single source of truth for "money to collect", derived entirely from existing `Order` fields (`payments` → `balanceRemaining`, `status`, `statusHistory`). A standard MVI screen (`feature/collection/presentation`) renders and sorts/filters the list; the dashboard reads the same calculator for its card and hero escalation. No new Firestore data, no migration.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin DI, kotlinx.coroutines Flow, kotlin.test + Turbine for tests.

## Global Constraints

- **KMP-safe only.** No JVM-only APIs (`String.format` compiles on Android but breaks the iOS link — use the existing `formatPrice` / `formatNaira` helpers for money).
- **Time via injected lambda.** Use `nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }` with `import kotlin.time.Clock` (this repo is on `kotlin.time.Clock`, not `kotlinx.datetime.Clock`). Never call `Clock.System` directly in constructor-default-free code paths that iOS resolves.
- **Koin:** register ViewModels with a `viewModel { … }` lambda (NOT `viewModelOf(::…)`) whenever the constructor has default-valued params (`nowMillis`). Pure `object` calculators are NOT registered — call them statically.
- **No hardcoded user-facing strings** — use `compose.resources` string resources (`Res.string.*`).
- **Every Screen composable has a `@Preview`** (populated + empty states).
- **MVI:** State / Action / Event sealed types + ViewModel; Root (stateful, `koinViewModel()`) + stateless Screen split.
- **Test names:** use camelCase method names (like `NbaCalculatorTest`), NOT backtick names — keeps the `compileTestKotlinIosSimulatorArm64` gate green.
- **Run one test class:** `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.<...>"`
- **iOS compile gate** (run after common-code tasks): `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
- **Detekt:** `./gradlew detekt`

---

### Task 1: `CollectionCalculator` — models + `collectibles()`

Derives which orders are collectible, when the money became owed, and whether it's overdue.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionModels.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionCalculator.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionCalculatorTest.kt`

**Interfaces:**
- Produces (used by Tasks 2, 3, 6):
  - `data class CollectibleOrder(orderId: String, customerId: String, customerName: String, customerPhone: String, balanceRemaining: Double, owedSince: Long, daysOwed: Int, isOverdue: Boolean, status: OrderStatus)`
  - `enum class CollectionSort { OLDEST_OWED, BIGGEST_BALANCE, NEWEST, CUSTOMER_NAME }`
  - `sealed interface CollectionFilter { None; OverdueOnly; ByStatus(status); ByCustomer(customerId) }`
  - `data class CollectionSummary(totalOutstanding: Double, orderCount: Int, overdueCount: Int)`
  - `CollectionCalculator.collectibles(orders, customersById, now): List<CollectibleOrder>`
  - `const val CollectionCalculator.OVERDUE_THRESHOLD_DAYS = 7`

- [ ] **Step 1: Write `CollectionModels.kt`** (no logic, pure declarations — commit with the calculator)

```kotlin
package com.danzucker.stitchpad.feature.collection.domain

import com.danzucker.stitchpad.core.domain.model.OrderStatus

/** One order that is done (Ready/Delivered) but still owes money. */
data class CollectibleOrder(
    val orderId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val balanceRemaining: Double,
    val owedSince: Long,
    val daysOwed: Int,
    val isOverdue: Boolean,
    val status: OrderStatus,
)

data class CollectionSummary(
    val totalOutstanding: Double,
    val orderCount: Int,
    val overdueCount: Int,
)

enum class CollectionSort { OLDEST_OWED, BIGGEST_BALANCE, NEWEST, CUSTOMER_NAME }

sealed interface CollectionFilter {
    data object None : CollectionFilter
    data object OverdueOnly : CollectionFilter
    data class ByStatus(val status: OrderStatus) : CollectionFilter
    data class ByCustomer(val customerId: String) : CollectionFilter
}
```

- [ ] **Step 2: Write the failing test** `CollectionCalculatorTest.kt`

```kotlin
package com.danzucker.stitchpad.feature.collection.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

private const val DAY = 24L * 60L * 60L * 1000L
private const val NOW = 1_000L * DAY // arbitrary fixed "now" in ms

private fun order(
    id: String = "o",
    customerId: String = "c1",
    status: OrderStatus = OrderStatus.DELIVERED,
    totalPrice: Double = 10_000.0,
    depositPaid: Double = 0.0,
    statusHistory: List<StatusChange> = emptyList(),
    createdAt: Long = NOW,
    updatedAt: Long = NOW,
): Order = Order(
    id = id,
    userId = "u",
    customerId = customerId,
    customerName = "Ada Obi",
    items = listOf(OrderItem(id = "i-$id", garmentType = GarmentType.AGBADA, description = "", price = totalPrice)),
    status = status,
    priority = OrderPriority.NORMAL,
    statusHistory = statusHistory,
    totalPrice = totalPrice,
    payments = if (depositPaid > 0.0) {
        listOf(Payment(id = "p-$id", amount = depositPaid, method = PaymentMethod.CASH, type = PaymentType.DEPOSIT, recordedAt = createdAt))
    } else {
        emptyList()
    },
    deadline = null,
    notes = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun customer(id: String = "c1", phone: String = "08030000000") =
    Customer(id = id, userId = "u", name = "Ada Obi", phone = phone)

class CollectionCalculatorTest {

    private val customers = mapOf("c1" to customer())

    @Test
    fun includesReadyAndDeliveredWithBalanceOnly() {
        val orders = listOf(
            order(id = "delivered", status = OrderStatus.DELIVERED, totalPrice = 5_000.0, depositPaid = 1_000.0),
            order(id = "ready", status = OrderStatus.READY, totalPrice = 5_000.0, depositPaid = 0.0),
            order(id = "pending", status = OrderStatus.PENDING, totalPrice = 5_000.0, depositPaid = 0.0),
            order(id = "inprogress", status = OrderStatus.IN_PROGRESS, totalPrice = 5_000.0, depositPaid = 0.0),
            order(id = "paid", status = OrderStatus.DELIVERED, totalPrice = 5_000.0, depositPaid = 5_000.0),
        )
        val result = CollectionCalculator.collectibles(orders, customers, NOW)
        assertEquals(setOf("delivered", "ready"), result.map { it.orderId }.toSet())
    }

    @Test
    fun owedSincePicksEarliestReadyOrDeliveredChange() {
        val readyAt = NOW - 10 * DAY
        val deliveredAt = NOW - 3 * DAY
        val o = order(
            status = OrderStatus.DELIVERED,
            statusHistory = listOf(
                StatusChange(OrderStatus.PENDING, NOW - 20 * DAY),
                StatusChange(OrderStatus.READY, readyAt),
                StatusChange(OrderStatus.DELIVERED, deliveredAt),
            ),
        )
        val item = CollectionCalculator.collectibles(listOf(o), customers, NOW).single()
        assertEquals(readyAt, item.owedSince)
        assertEquals(10, item.daysOwed)
    }

    @Test
    fun owedSinceFallsBackToUpdatedThenCreatedWhenNoHistory() {
        val o = order(statusHistory = emptyList(), createdAt = NOW - 30 * DAY, updatedAt = NOW - 4 * DAY)
        val item = CollectionCalculator.collectibles(listOf(o), customers, NOW).single()
        assertEquals(NOW - 4 * DAY, item.owedSince)
    }

    @Test
    fun overdueAtSevenDaysNotAtSix() {
        val six = order(id = "six", statusHistory = listOf(StatusChange(OrderStatus.READY, NOW - 6 * DAY)))
        val seven = order(id = "seven", statusHistory = listOf(StatusChange(OrderStatus.READY, NOW - 7 * DAY)))
        val result = CollectionCalculator.collectibles(listOf(six, seven), customers, NOW).associateBy { it.orderId }
        assertFalse(result.getValue("six").isOverdue)
        assertTrue(result.getValue("seven").isOverdue)
    }

    @Test
    fun resolvesCustomerPhone() {
        val item = CollectionCalculator.collectibles(listOf(order()), customers, NOW).single()
        assertEquals("08030000000", item.customerPhone)
    }
}
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.domain.CollectionCalculatorTest"`
Expected: FAIL — `CollectionCalculator` unresolved.

- [ ] **Step 4: Write `CollectionCalculator.kt` (collectibles + private helpers)**

```kotlin
package com.danzucker.stitchpad.feature.collection.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus

/**
 * Single source of truth for "money to collect": orders that are done
 * (Ready or Delivered) but still owe a balance. Pure and time-injected so it
 * is trivially testable and shared by the dashboard card and the To-Collect list.
 */
object CollectionCalculator {

    const val OVERDUE_THRESHOLD_DAYS = 7
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun collectibles(
        orders: List<Order>,
        customersById: Map<String, Customer>,
        now: Long,
    ): List<CollectibleOrder> =
        orders
            .filter { it.status == OrderStatus.READY || it.status == OrderStatus.DELIVERED }
            .filter { it.balanceRemaining > 0.0 }
            .map { order ->
                val owedSince = owedSince(order)
                val daysOwed = daysBetween(owedSince, now)
                CollectibleOrder(
                    orderId = order.id,
                    customerId = order.customerId,
                    customerName = order.customerName,
                    customerPhone = customersById[order.customerId]?.phone.orEmpty(),
                    balanceRemaining = order.balanceRemaining,
                    owedSince = owedSince,
                    daysOwed = daysOwed,
                    isOverdue = daysOwed >= OVERDUE_THRESHOLD_DAYS,
                    status = order.status,
                )
            }

    /** The moment the garment first became collectible (Ready or Delivered). */
    private fun owedSince(order: Order): Long {
        val readyOrDelivered = order.statusHistory
            .filter { it.status == OrderStatus.READY || it.status == OrderStatus.DELIVERED }
            .minOfOrNull { it.changedAt }
        return readyOrDelivered
            ?: order.updatedAt.takeIf { it > 0L }
            ?: order.createdAt
    }

    private fun daysBetween(from: Long, to: Long): Int {
        if (to <= from) return 0
        return ((to - from) / MILLIS_PER_DAY).toInt()
    }
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.domain.CollectionCalculatorTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/domain/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/collection/domain/
git commit -m "feat(collection): CollectionCalculator collectibles + owedSince/overdue derivation"
```

---

### Task 2: `CollectionCalculator` — `summarize()`, `sorted()`, `filtered()`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionCalculator.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionCalculatorTest.kt` (extend)

**Interfaces:**
- Consumes: `CollectibleOrder`, `CollectionSort`, `CollectionFilter`, `CollectionSummary` (Task 1).
- Produces (used by Tasks 3, 6):
  - `CollectionCalculator.summarize(items): CollectionSummary`
  - `CollectionCalculator.sorted(items, sort): List<CollectibleOrder>` — overdue always ranks above non-overdue, then the chosen sort.
  - `CollectionCalculator.filtered(items, filter): List<CollectibleOrder>`

- [ ] **Step 1: Add failing tests** (append to `CollectionCalculatorTest.kt`)

```kotlin
    // --- summarize / sorted / filtered ---

    private fun item(
        id: String,
        balance: Double = 1_000.0,
        owedSince: Long = NOW - DAY,
        overdue: Boolean = false,
        name: String = "Ada",
        status: OrderStatus = OrderStatus.DELIVERED,
    ) = CollectibleOrder(
        orderId = id, customerId = "c-$id", customerName = name, customerPhone = "080",
        balanceRemaining = balance, owedSince = owedSince,
        daysOwed = if (overdue) 9 else 1, isOverdue = overdue, status = status,
    )

    @Test
    fun summarizeAggregatesTotalsAndOverdueCount() {
        val items = listOf(
            item("a", balance = 2_000.0, overdue = true),
            item("b", balance = 3_000.0, overdue = false),
        )
        val summary = CollectionCalculator.summarize(items)
        assertEquals(5_000.0, summary.totalOutstanding)
        assertEquals(2, summary.orderCount)
        assertEquals(1, summary.overdueCount)
    }

    @Test
    fun sortedFloatsOverdueThenAppliesSort() {
        val items = listOf(
            item("newNotOverdue", owedSince = NOW - DAY, overdue = false),
            item("oldOverdue", owedSince = NOW - 10 * DAY, overdue = true),
        )
        val sorted = CollectionCalculator.sorted(items, CollectionSort.OLDEST_OWED)
        assertEquals(listOf("oldOverdue", "newNotOverdue"), sorted.map { it.orderId })
    }

    @Test
    fun sortedByBiggestBalance() {
        val items = listOf(item("small", balance = 1_000.0), item("big", balance = 9_000.0))
        val sorted = CollectionCalculator.sorted(items, CollectionSort.BIGGEST_BALANCE)
        assertEquals(listOf("big", "small"), sorted.map { it.orderId })
    }

    @Test
    fun sortedByCustomerNameCaseInsensitive() {
        val items = listOf(item("z", name = "zoe"), item("a", name = "Ada"))
        val sorted = CollectionCalculator.sorted(items, CollectionSort.CUSTOMER_NAME)
        assertEquals(listOf("a", "z"), sorted.map { it.orderId })
    }

    @Test
    fun filteredByOverdueStatusAndCustomer() {
        val items = listOf(
            item("od", overdue = true, status = OrderStatus.DELIVERED),
            item("ready", overdue = false, status = OrderStatus.READY),
        )
        assertEquals(listOf("od"), CollectionCalculator.filtered(items, CollectionFilter.OverdueOnly).map { it.orderId })
        assertEquals(listOf("ready"), CollectionCalculator.filtered(items, CollectionFilter.ByStatus(OrderStatus.READY)).map { it.orderId })
        assertEquals(listOf("ready"), CollectionCalculator.filtered(items, CollectionFilter.ByCustomer("c-ready")).map { it.orderId })
        assertEquals(2, CollectionCalculator.filtered(items, CollectionFilter.None).size)
    }
```

- [ ] **Step 2: Run tests, verify the new ones fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.domain.CollectionCalculatorTest"`
Expected: FAIL — `summarize` / `sorted` / `filtered` unresolved.

- [ ] **Step 3: Add the functions to `CollectionCalculator`**

```kotlin
    fun summarize(items: List<CollectibleOrder>): CollectionSummary =
        CollectionSummary(
            totalOutstanding = items.sumOf { it.balanceRemaining },
            orderCount = items.size,
            overdueCount = items.count { it.isOverdue },
        )

    fun sorted(items: List<CollectibleOrder>, sort: CollectionSort): List<CollectibleOrder> {
        val bySort = when (sort) {
            CollectionSort.OLDEST_OWED -> compareBy<CollectibleOrder> { it.owedSince }
            CollectionSort.BIGGEST_BALANCE -> compareByDescending<CollectibleOrder> { it.balanceRemaining }
            CollectionSort.NEWEST -> compareByDescending<CollectibleOrder> { it.owedSince }
            CollectionSort.CUSTOMER_NAME -> compareBy<CollectibleOrder> { it.customerName.lowercase() }
        }
        return items.sortedWith(compareByDescending<CollectibleOrder> { it.isOverdue }.then(bySort))
    }

    fun filtered(items: List<CollectibleOrder>, filter: CollectionFilter): List<CollectibleOrder> =
        when (filter) {
            CollectionFilter.None -> items
            CollectionFilter.OverdueOnly -> items.filter { it.isOverdue }
            is CollectionFilter.ByStatus -> items.filter { it.status == filter.status }
            is CollectionFilter.ByCustomer -> items.filter { it.customerId == filter.customerId }
        }
```

- [ ] **Step 4: Run tests, verify all pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.domain.CollectionCalculatorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionCalculator.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/collection/domain/CollectionCalculatorTest.kt
git commit -m "feat(collection): summarize + overdue-first sort + filters on CollectionCalculator"
```

---

### Task 3: To-Collect MVI — State / Action / Event / ViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectState.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectAction.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectEvent.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectViewModelTest.kt`

**Interfaces:**
- Consumes: `CollectionCalculator`, `CollectibleOrder`, `CollectionSort`, `CollectionFilter`, `CollectionSummary` (Tasks 1-2); `OrderRepository.observeOrders(userId)`, `CustomerRepository.observeCustomers(userId)`, `AuthRepository.getCurrentUser()`.
- Produces (used by Task 4): `ToCollectState`, `ToCollectAction`, `ToCollectEvent`, `ToCollectViewModel(orderRepository, customerRepository, authRepository, nowMillis)`.

- [ ] **Step 1: Write State / Action / Event**

`ToCollectState.kt`:
```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.collection.domain.CollectibleOrder
import com.danzucker.stitchpad.feature.collection.domain.CollectionFilter
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort
import com.danzucker.stitchpad.feature.collection.domain.CollectionSummary

data class CustomerFilterOption(val id: String, val name: String)

data class ToCollectState(
    val items: List<CollectibleOrder> = emptyList(),
    val summary: CollectionSummary = CollectionSummary(0.0, 0, 0),
    val sort: CollectionSort = CollectionSort.OLDEST_OWED,
    val filter: CollectionFilter = CollectionFilter.None,
    val customerOptions: List<CustomerFilterOption> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
)
```
> Confirm the `UiText` import path by opening an existing State file (e.g. `NotificationsInboxState.kt`); it is `com.danzucker.stitchpad.core.presentation.UiText`.

`ToCollectAction.kt`:
```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import com.danzucker.stitchpad.feature.collection.domain.CollectionFilter
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort

sealed interface ToCollectAction {
    data object OnBackClick : ToCollectAction
    data class OnSortSelected(val sort: CollectionSort) : ToCollectAction
    data class OnFilterSelected(val filter: CollectionFilter) : ToCollectAction
    data class OnRowClick(val orderId: String) : ToCollectAction
    data class OnChaseClick(val orderId: String) : ToCollectAction
}
```

`ToCollectEvent.kt` (carries the domain objects so the Root builds the WhatsApp message with `WhatsAppMessageBuilder`, keeping `getString` out of the ViewModel):
```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order

sealed interface ToCollectEvent {
    data object NavigateBack : ToCollectEvent
    data class NavigateToOrderDetail(val orderId: String) : ToCollectEvent
    data class LaunchWhatsApp(val order: Order, val customer: Customer) : ToCollectEvent
}
```

- [ ] **Step 2: Write the failing ViewModel test** `ToCollectViewModelTest.kt`

```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DAY = 24L * 60L * 60L * 1000L
private const val NOW = 1_000L * DAY

@OptIn(ExperimentalCoroutinesApi::class)
class ToCollectViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        authRepository.currentUser = User(
            id = "u", email = "e@x.com", displayName = "Dan",
            businessName = null, phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0,
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun order(id: String, status: OrderStatus, total: Double, owedDaysAgo: Long) = Order(
        id = id, userId = "u", customerId = "c1", customerName = "Ada Obi",
        items = listOf(OrderItem(id = "i-$id", garmentType = GarmentType.AGBADA, description = "", price = total)),
        status = status, priority = OrderPriority.NORMAL,
        statusHistory = listOf(StatusChange(OrderStatus.READY, NOW - owedDaysAgo * DAY)),
        totalPrice = total, payments = emptyList(),
        deadline = null, notes = null, createdAt = NOW, updatedAt = NOW,
    )

    private fun TestScope.createViewModel(): ToCollectViewModel {
        val vm = ToCollectViewModel(orderRepository, customerRepository, authRepository, nowMillis = { NOW })
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun loadsCollectiblesOverdueFirstByDefault() = runTest {
        customerRepository.customersList = listOf(Customer(id = "c1", userId = "u", name = "Ada Obi", phone = "080"))
        orderRepository.ordersList = listOf(
            order("recent", OrderStatus.DELIVERED, 5_000.0, owedDaysAgo = 1),
            order("stale", OrderStatus.DELIVERED, 5_000.0, owedDaysAgo = 12),
        )
        val vm = createViewModel()
        assertEquals(listOf("stale", "recent"), vm.state.value.items.map { it.orderId })
        assertEquals(10_000.0, vm.state.value.summary.totalOutstanding)
        assertEquals(1, vm.state.value.summary.overdueCount)
    }

    @Test
    fun sortSelectionReordersWithoutRefetch() = runTest {
        customerRepository.customersList = listOf(Customer(id = "c1", userId = "u", name = "Ada Obi", phone = "080"))
        orderRepository.ordersList = listOf(
            order("small", OrderStatus.DELIVERED, 2_000.0, owedDaysAgo = 1),
            order("big", OrderStatus.DELIVERED, 9_000.0, owedDaysAgo = 1),
        )
        val vm = createViewModel()
        vm.onAction(ToCollectAction.OnSortSelected(CollectionSort.BIGGEST_BALANCE))
        assertEquals(listOf("big", "small"), vm.state.value.items.map { it.orderId })
    }

    @Test
    fun rowClickEmitsNavigateToOrderDetail() = runTest {
        customerRepository.customersList = listOf(Customer(id = "c1", userId = "u", name = "Ada Obi", phone = "080"))
        orderRepository.ordersList = listOf(order("o1", OrderStatus.DELIVERED, 5_000.0, owedDaysAgo = 1))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(ToCollectAction.OnRowClick("o1"))
            assertEquals(ToCollectEvent.NavigateToOrderDetail("o1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chaseClickEmitsLaunchWhatsAppWithOrderAndCustomer() = runTest {
        customerRepository.customersList = listOf(Customer(id = "c1", userId = "u", name = "Ada Obi", phone = "08030000000"))
        orderRepository.ordersList = listOf(order("o1", OrderStatus.DELIVERED, 5_000.0, owedDaysAgo = 1))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(ToCollectAction.OnChaseClick("o1"))
            val event = awaitItem()
            assertTrue(event is ToCollectEvent.LaunchWhatsApp)
            assertEquals("o1", event.order.id)
            assertEquals("08030000000", event.customer.phone)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
> Verify the `User(...)` constructor params against `core/domain/model/User.kt` (the `FakeAuthRepository` uses `id, email, displayName, businessName, phoneNumber, whatsappNumber, avatarColorIndex`). Adjust if the model has additional required fields.

- [ ] **Step 3: Run the test, verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.presentation.ToCollectViewModelTest"`
Expected: FAIL — `ToCollectViewModel` unresolved.

- [ ] **Step 4: Write `ToCollectViewModel.kt`**

```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.collection.domain.CollectibleOrder
import com.danzucker.stitchpad.feature.collection.domain.CollectionCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ToCollectViewModel(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private var hasLoaded = false
    private var allCollectibles: List<CollectibleOrder> = emptyList()
    private var ordersById: Map<String, Order> = emptyMap()
    private var customersById: Map<String, Customer> = emptyMap()

    private val _state = MutableStateFlow(ToCollectState())
    val state = _state
        .onStart {
            if (!hasLoaded) {
                hasLoaded = true
                observe()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ToCollectState())

    private val _events = Channel<ToCollectEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: ToCollectAction) {
        when (action) {
            ToCollectAction.OnBackClick ->
                viewModelScope.launch { _events.send(ToCollectEvent.NavigateBack) }
            is ToCollectAction.OnSortSelected -> {
                _state.update { it.copy(sort = action.sort) }
                recompute()
            }
            is ToCollectAction.OnFilterSelected -> {
                _state.update { it.copy(filter = action.filter) }
                recompute()
            }
            is ToCollectAction.OnRowClick ->
                viewModelScope.launch { _events.send(ToCollectEvent.NavigateToOrderDetail(action.orderId)) }
            is ToCollectAction.OnChaseClick -> onChaseClick(action.orderId)
        }
    }

    private fun observe() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            combine(
                orderRepository.observeOrders(uid),
                customerRepository.observeCustomers(uid),
            ) { ordersResult, customersResult -> ordersResult to customersResult }
                .collect { (ordersResult, customersResult) ->
                    val orders = (ordersResult as? Result.Success)?.data
                    val customers = (customersResult as? Result.Success)?.data
                    if (orders == null || customers == null) {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = UiText.DynamicString("Couldn't load orders"))
                        }
                        return@collect
                    }
                    ordersById = orders.associateBy { it.id }
                    customersById = customers.associateBy { it.id }
                    allCollectibles = CollectionCalculator.collectibles(orders, customersById, nowMillis())
                    _state.update { it.copy(isLoading = false, errorMessage = null) }
                    recompute()
                }
        }
    }

    private fun recompute() {
        val current = _state.value
        val filtered = CollectionCalculator.filtered(allCollectibles, current.filter)
        val sorted = CollectionCalculator.sorted(filtered, current.sort)
        val options = allCollectibles
            .distinctBy { it.customerId }
            .map { CustomerFilterOption(it.customerId, it.customerName) }
            .sortedBy { it.name.lowercase() }
        _state.update {
            it.copy(
                items = sorted,
                summary = CollectionCalculator.summarize(allCollectibles),
                customerOptions = options,
            )
        }
    }

    private fun onChaseClick(orderId: String) {
        val order = ordersById[orderId] ?: return
        val customer = customersById[order.customerId] ?: return
        viewModelScope.launch { _events.send(ToCollectEvent.LaunchWhatsApp(order, customer)) }
    }
}
```
> Replace `UiText.DynamicString("Couldn't load orders")` with the project's canonical error `UiText` (check `NotificationsInboxViewModel` — it uses `UiText.StringResourceText(Res.string.error_unknown)`; use that same resource so the error copy is localized). The literal is shown here only to keep the type concrete.

- [ ] **Step 5: Run the test, verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.collection.presentation.ToCollectViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/collection/presentation/
git commit -m "feat(collection): ToCollect MVI state + ViewModel (observe, sort/filter, chase)"
```

---

### Task 4: To-Collect Root + Screen (UI + previews)

Stateless list screen with a top bar, a summary header, sort menu, filter chips, rows (customer + owed/overdue + balance + chase), empty state, and previews. Mirrors `NotificationsInboxScreen` and the `PipelineSummaryRow` card pattern.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectRoot.kt`

**Interfaces:**
- Consumes: `ToCollectState`, `ToCollectAction`, `ToCollectEvent`, `ToCollectViewModel` (Task 3); `WhatsAppLauncher.launch(phone, message)`; `WhatsAppMessageBuilder.buildForOrder(order, customer)`; `formatPrice(Double)` from `core.sharing`.
- Produces (used by Task 5): `ToCollectRoot(onNavigateBack, onNavigateToOrderDetail, viewModel)`.

- [ ] **Step 1: Write `ToCollectScreen.kt`** (stateless — includes a row composable + previews)

```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.feature.collection.domain.CollectibleOrder
import com.danzucker.stitchpad.feature.collection.domain.CollectionFilter
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort
import com.danzucker.stitchpad.feature.collection.domain.CollectionSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToCollectScreen(
    state: ToCollectState,
    onAction: (ToCollectAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To collect") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ToCollectAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SummaryHeader(state.summary)
            FilterRow(state.filter, onAction)
            HorizontalDivider()
            if (state.items.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(DesignTokens.space4),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    items(state.items, key = { it.orderId }) { row ->
                        CollectibleRow(
                            row = row,
                            onClick = { onAction(ToCollectAction.OnRowClick(row.orderId)) },
                            onChase = { onAction(ToCollectAction.OnChaseClick(row.orderId)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(summary: CollectionSummary) {
    Column(Modifier.fillMaxWidth().padding(DesignTokens.space4)) {
        Text("You're owed", style = MaterialTheme.typography.labelMedium)
        Text(
            "₦${formatPrice(summary.totalOutstanding)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        val overdueSuffix = if (summary.overdueCount > 0) " · ${summary.overdueCount} overdue" else ""
        Text(
            "across ${summary.orderCount} orders$overdueSuffix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(filter: CollectionFilter, onAction: (ToCollectAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DesignTokens.space4),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        FilterChip(
            selected = filter == CollectionFilter.None,
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.None)) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == CollectionFilter.OverdueOnly,
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.OverdueOnly)) },
            label = { Text("Overdue") },
        )
        FilterChip(
            selected = filter == CollectionFilter.ByStatus(OrderStatus.DELIVERED),
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.ByStatus(OrderStatus.DELIVERED))) },
            label = { Text("Delivered") },
        )
        FilterChip(
            selected = filter == CollectionFilter.ByStatus(OrderStatus.READY),
            onClick = { onAction(ToCollectAction.OnFilterSelected(CollectionFilter.ByStatus(OrderStatus.READY))) },
            label = { Text("Ready") },
        )
    }
}

@Composable
private fun CollectibleRow(
    row: CollectibleOrder,
    onClick: () -> Unit,
    onChase: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.customerName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val owedLabel = if (row.isOverdue) "Overdue · owed ${row.daysOwed} days" else "owed ${row.daysOwed} days"
                Text(
                    owedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "₦${formatPrice(row.balanceRemaining)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onChase) { Text("Chase") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(DesignTokens.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("You're all paid up", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nothing to collect right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ToCollectScreenFilledPreview() {
    StitchPadTheme {
        ToCollectScreen(
            state = ToCollectState(
                isLoading = false,
                summary = CollectionSummary(48_500.0, 3, 1),
                items = listOf(
                    CollectibleOrder("o1", "c1", "Ada Obi", "080", 20_000.0, 0L, 12, true, OrderStatus.DELIVERED),
                    CollectibleOrder("o2", "c2", "Emeka N", "080", 28_500.0, 0L, 2, false, OrderStatus.READY),
                ),
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ToCollectScreenEmptyPreview() {
    StitchPadTheme {
        ToCollectScreen(state = ToCollectState(isLoading = false), onAction = {})
    }
}
```
> **Note:** verify every import resolves (icons artifact, `DesignTokens`, `StitchPadTheme`, `formatPrice`) by compiling. The customer-name filter dropdown (`state.customerOptions` + `CollectionFilter.ByCustomer`) is intentionally left for a small follow-up step in this task if time allows — the four chips above satisfy the overdue/status filters; add a customer dropdown menu button in `FilterRow` driven by `state.customerOptions` before marking the screen done.

- [ ] **Step 2: Write `ToCollectRoot.kt`** (stateful — builds the WhatsApp message and launches it)

```kotlin
package com.danzucker.stitchpad.feature.collection.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ToCollectRoot(
    onNavigateBack: () -> Unit,
    onNavigateToOrderDetail: (String) -> Unit,
    viewModel: ToCollectViewModel = koinViewModel(),
    whatsAppLauncher: WhatsAppLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ToCollectEvent.NavigateBack -> onNavigateBack()
            is ToCollectEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
            is ToCollectEvent.LaunchWhatsApp -> scope.launch {
                val message = WhatsAppMessageBuilder.buildForOrder(event.order, event.customer)
                whatsAppLauncher.launch(event.customer.phone, message)
            }
        }
    }

    ToCollectScreen(state = state, onAction = viewModel::onAction)
}
```
> Confirm import paths against `NotificationsInboxRoot.kt` / `DashboardScreen.kt`: `ObserveAsEvents` = `com.danzucker.stitchpad.util.ObserveAsEvents`, `koinViewModel` = `org.koin.compose.viewmodel.koinViewModel`, `koinInject` = `org.koin.compose.koinInject`, `WhatsAppLauncher` = `com.danzucker.stitchpad.core.sharing.WhatsAppLauncher`.

- [ ] **Step 3: Compile common + run detekt**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL (resolves any import-path drift).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/collection/presentation/ToCollectRoot.kt
git commit -m "feat(collection): To-Collect list screen + root (rows, filters, chase, empty state)"
```

---

### Task 5: DI + navigation wiring (reachable screen)

Registers the ViewModel and makes the screen navigable from the dashboard card tap.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ToCollectModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt` (add module to `modules(...)`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt` (add route)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` (add `composable<ToCollectRoute>` + a `onNavigateToToCollect` on the dashboard block)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` (add `onNavigateToToCollect` param to `DashboardRoot`, handle a `NavigateToToCollect` event)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardEvent.kt` (add event)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt` (route `OnOutstandingClick` to the new event)

**Interfaces:**
- Consumes: `ToCollectRoot` (Task 4), `OrderDetailRoute(orderId)` (existing).
- Produces (used by Task 6): `ToCollectRoute`; `DashboardEvent.NavigateToToCollect`; `DashboardRoot(..., onNavigateToToCollect)`.

- [ ] **Step 1: Create `ToCollectModule.kt`** (lambda form — the VM has a default `nowMillis` param)

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.collection.presentation.ToCollectViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val toCollectPresentationModule = module {
    viewModel { ToCollectViewModel(get(), get(), get()) }
}
```
> Check an existing module (e.g. `ReportsModule.kt`) for the exact `viewModel { }` import — it is `org.koin.core.module.dsl.viewModel`.

- [ ] **Step 2: Register the module** in `StitchPadApp.kt`

Add `toCollectPresentationModule` to the `modules( … )` list in `initKoin()` (alongside `dashboardPresentationModule`). Add the import `import com.danzucker.stitchpad.di.toCollectPresentationModule` if imports are explicit.

- [ ] **Step 3: Add the route** in `Routes.kt`

```kotlin
@Serializable
data object ToCollectRoute
```

- [ ] **Step 4: Add the dashboard event** in `DashboardEvent.kt`

```kotlin
data object NavigateToToCollect : DashboardEvent
```

- [ ] **Step 5: Route the existing outstanding action** in `DashboardViewModel.kt`

Change line 210 handler:
```kotlin
DashboardAction.OnOutstandingClick -> emitEvent(DashboardEvent.NavigateToToCollect)
```
(was `emitEvent(DashboardEvent.NavigateToOrders)`).

- [ ] **Step 6: Thread the callback through `DashboardRoot`** in `DashboardScreen.kt`

Add a param to `DashboardRoot`:
```kotlin
onNavigateToToCollect: () -> Unit,
```
And in the `ObserveAsEvents` / `handleDashboardEvent` mapping, handle:
```kotlin
DashboardEvent.NavigateToToCollect -> onNavigateToToCollect()
```
(mirror how `DashboardEvent.NavigateToOrders -> onNavigateToOrders()` is wired in `handleDashboardEvent`).

- [ ] **Step 7: Add the nav destination + wire the dashboard callback** in `MainScreen.kt` `MainNavGraph`

Add a new destination:
```kotlin
composable<ToCollectRoute> {
    ToCollectRoot(
        onNavigateBack = { navController.navigateUp() },
        onNavigateToOrderDetail = { orderId -> navController.navigate(OrderDetailRoute(orderId = orderId)) },
    )
}
```
And in the existing `composable<DashboardRoute> { DashboardRoot( … ) }` block, add:
```kotlin
onNavigateToToCollect = { navController.navigate(ToCollectRoute) },
```
Add imports for `ToCollectRoute` and `ToCollectRoot`.

- [ ] **Step 8: Build + detekt**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt`
Expected: BUILD SUCCESSFUL. (Compiler enforces the exhaustive event `when` and the new `DashboardRoot` param at its call site — fix the call site in `MainScreen.kt` if flagged.)

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ToCollectModule.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/
git commit -m "feat(collection): register DI + navigation; dashboard outstanding tap opens To-Collect"
```

---

### Task 6: Dashboard "You're owed" card + data from `CollectionCalculator`

Makes the dashboard show what the tailor is owed (now including Delivered), rendered as a card that taps into the list.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardState.kt` (add `outstandingOverdueCount`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt` (compute outstanding via `CollectionCalculator`)
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/YoureOwedCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` (render the card)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModelTest.kt` (extend)

**Interfaces:**
- Consumes: `CollectionCalculator.collectibles/summarize` (Tasks 1-2); `DashboardAction.OnOutstandingClick` (existing); `formatNaira` (existing, `feature/dashboard/presentation/DashboardFormat.kt`).
- Produces (used by Task 7): `DashboardState.outstandingOverdueCount`.

- [ ] **Step 1: Add the state field** in `DashboardState.kt`

After line 32 (`val outstandingOrderCount: Int = 0,`):
```kotlin
    val outstandingOverdueCount: Int = 0,
```

- [ ] **Step 2: Write the failing VM test** (append to `DashboardViewModelTest.kt`)

Add a test asserting a Delivered unpaid order now contributes to `outstandingAmount` and that overdue is counted. Use the file's existing `order(...)` helper conventions (match its signature — check the top of `DashboardViewModelTest.kt`). Skeleton:
```kotlin
    @Test
    fun deliveredUnpaidOrderCountsTowardOutstanding() = runTest {
        authRepository.currentUser = testUser() // however the file seeds the user
        customerRepository.customersList = listOf(/* customer c1 with phone */)
        orderRepository.ordersList = listOf(
            // a DELIVERED order, totalPrice 5000, no payments -> balance 5000,
            // statusHistory READY 10 days ago (overdue)
        )
        val vm = createViewModel() // use the file's existing factory
        // subscribe as the file's other tests do
        assertEquals(5_000.0, vm.state.value.outstandingAmount)
        assertEquals(1, vm.state.value.outstandingOrderCount)
        assertEquals(1, vm.state.value.outstandingOverdueCount)
    }
```
> Fill this in using the exact fixtures already present in `DashboardViewModelTest.kt` (its `order(...)`/user seeding/`createViewModel()` helpers). The point of the test: **before** the Step 3 change, a DELIVERED order contributes 0 (BucketCalculator excludes it); **after**, it contributes. Run it first to see it fail on the delivered case.

- [ ] **Step 3: Compute outstanding from `CollectionCalculator`** in `DashboardViewModel.kt`

Near line 504 (right after `val customersById = customers.associateBy { it.id }` / `BucketCalculator.compute(...)`), add:
```kotlin
val collectibles = CollectionCalculator.collectibles(orders, customersById, nowMillis())
val collectionSummary = CollectionCalculator.summarize(collectibles)
```
Then in the `_state.update { it.copy( … ) }` block, replace the outstanding lines (556-557) with:
```kotlin
outstandingAmount = collectionSummary.totalOutstanding,
outstandingOrderCount = collectionSummary.orderCount,
outstandingOverdueCount = collectionSummary.overdueCount,
```
Add import: `import com.danzucker.stitchpad.feature.collection.domain.CollectionCalculator`.

- [ ] **Step 4: Run the VM test, verify pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.presentation.DashboardViewModelTest"`
Expected: PASS (and existing dashboard tests still green — if any asserted the old delivered-excluded outstanding number, update them to the new semantics).

- [ ] **Step 5: Write `YoureOwedCard.kt`** (mirror `PipelineSummaryRow.kt`)

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.formatNaira
import com.danzucker.stitchpad.ui.theme.DesignTokens

@Composable
fun YoureOwedCard(
    amount: Double,
    orderCount: Int,
    overdueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("You're owed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text("₦${formatNaira(amount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val overdue = if (overdueCount > 0) " · $overdueCount overdue" else ""
                Text(
                    "across $orderCount orders$overdue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
```
> Open `PipelineSummaryRow.kt` and match its exact tokens: `DesignTokens.radiusLg`, `DesignTokens.space3`, the `outlineVariant` border, and the `.semantics { }` accessibility block — copy that semantics modifier onto the `Surface` so the card is a labelled button for screen readers. `formatNaira` is package-internal in `feature.dashboard.presentation` — the card is in that module so the import resolves.

- [ ] **Step 6: Render the card** in `DashboardScreen.kt`

Immediately after the focus card block (after line ~772), add:
```kotlin
if (state.outstandingOrderCount > 0) {
    YoureOwedCard(
        amount = state.outstandingAmount,
        orderCount = state.outstandingOrderCount,
        overdueCount = state.outstandingOverdueCount,
        onClick = { onAction(DashboardAction.OnOutstandingClick) },
    )
}
```
Add import for `YoureOwedCard`.

- [ ] **Step 7: Build, detekt, iOS compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.*"`
Expected: BUILD SUCCESSFUL, tests green.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/
git commit -m "feat(dashboard): You're-owed card sourced from CollectionCalculator (includes Delivered)"
```

---

### Task 7: Dashboard Focus-hero escalation (B)

When there's an overdue collectible, promote collecting into the Focus hero.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/DashboardUiState.kt` (new variant + `focusVariant` mapping)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/FocusResolver.kt` (escalation branch in `resolveUiState` + `resolveFocus`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt` (pass overdue count; route the CTA)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/FocusResolverTest.kt` (create or extend)

**Interfaces:**
- Consumes: `CollectionSummary.overdueCount` (Task 1); `DashboardUiState`, `FocusResolution`, `FocusVariant` (existing).
- Produces: `DashboardUiState.CollectionOverdue`.

- [ ] **Step 1: Add the `DashboardUiState` variant** in `DashboardUiState.kt`

```kotlin
data object CollectionOverdue : DashboardUiState
```
And add it to the exhaustive `DashboardUiState.focusVariant` mapping (lines 88-97):
```kotlin
DashboardUiState.CollectionOverdue -> FocusVariant.Earn
```

- [ ] **Step 2: Write/extend the failing `FocusResolverTest`**

```kotlin
    @Test
    fun escalatesToCollectionOverdueWhenOverdueCollectiblesExist() {
        val uiState = FocusResolver.resolveUiState(
            buckets = emptyBuckets(),         // no deadline overdue / ready
            nextBestActions = emptyList(),
            orders = listOf(anOrder()),       // non-empty so we're past BrandNew/FirstCustomer
            customers = listOf(aCustomer()),
            collectionOverdueCount = 2,       // NEW param
        )
        assertEquals(DashboardUiState.CollectionOverdue, uiState)
    }
```
> Use the fixtures already in the dashboard test sources; `emptyBuckets()`/`anOrder()` mirror helpers in `BucketCalculatorTest`/`FocusResolver` tests. The new `collectionOverdueCount` param is added in Step 3.

- [ ] **Step 3: Add the escalation branch** in `FocusResolver.kt`

Add a `collectionOverdueCount: Int` param to both `resolveUiState(...)` and `resolveFocus(...)`. In `resolveUiState`, insert the escalation **after** the BrandNew/FirstCustomer guards but **before** the `BusyDay` deadline check (so genuinely overdue money is the top priority once the workshop is established):
```kotlin
if (collectionOverdueCount > 0) return DashboardUiState.CollectionOverdue
```
In `resolveFocus`, add the `when` branch:
```kotlin
DashboardUiState.CollectionOverdue -> FocusResolution(
    variant = FocusVariant.Earn,
    headline = UiText.DynamicString("Money to collect"),
    supporting = UiText.DynamicString("$collectionOverdueCount overdue — chase payment today"),
    ctaLabel = UiText.DynamicString("Collect now"),
)
```
> Replace the `UiText.DynamicString(...)` copy with localized `Res.string.*` resources (mirror how `NbaActive`/`ReadyForPickup` branches build their `UiText` — add `dashboard_focus_collect_headline` etc. to strings, using positional `%1$d` args per the compose-resources rule). The `when` is exhaustive over the sealed interface, so the compiler forces this branch.

- [ ] **Step 4: Pass the count from the ViewModel** in `DashboardViewModel.kt`

At the `FocusResolver.resolveUiState(...)` and `resolveFocus(...)` call sites (lines ~506-515), pass `collectionOverdueCount = collectionSummary.overdueCount` (from Task 6 Step 3).

- [ ] **Step 5: Route the Focus CTA to the list** in `DashboardViewModel.kt`

Where `DashboardAction.OnFocusCtaClick` is handled, when `state.uiState == DashboardUiState.CollectionOverdue`, emit `DashboardEvent.NavigateToToCollect` (the event from Task 5). Follow the existing CTA-routing `when`/branch structure in the handler.

- [ ] **Step 6: Build, detekt, iOS compile, tests**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.*"`
Expected: BUILD SUCCESSFUL, tests green. (The exhaustive `when`s in `resolveFocus` and `focusVariant` mapping will fail to compile until the new variant is handled — that is the guard working.)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/
git commit -m "feat(dashboard): escalate collection into Focus hero when a debt is overdue"
```

---

### Task 8: Full verification + smoke-test doc

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-to-collect-design.md` (optional: append a "QA smoke test" section — per the project's QA convention every PR ships manual smoke steps).

- [ ] **Step 1: Full test + lint + both-platform compile**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64 :composeApp:assembleDebug
```
Expected: all green.

- [ ] **Step 2: Manual smoke test (Daniel is QA)** — record these steps in the PR:
  1. Seed/find an order, mark it **Delivered** with a remaining balance → it appears on the dashboard "You're owed" card and in the To-collect list.
  2. Open the list → default sort is oldest-owed, overdue rows on top with the Overdue badge; try each sort and each filter chip.
  3. Tap a row → lands on the order detail (record-payment lives there).
  4. Tap **Chase** → WhatsApp opens to that customer.
  5. Record a payment that clears the balance → the order leaves both the card and the list.
  6. With an order delivered/ready ≥ 7 days unpaid → the Focus hero escalates to "Money to collect".

- [ ] **Step 3: Commit + open PR** (per PR workflow — feature branch, Cursor + `codex review` before merge)

```bash
git add docs/superpowers/specs/2026-07-28-to-collect-design.md
git commit -m "docs(collection): QA smoke test steps for To-Collect"
```

---

## Self-Review notes (for the executor)

- **Spec coverage:** inclusion rule (T1), owedSince + 7-day overdue (T1), summarize/sort/filter (T2), MVI + chase + record-via-detail (T3-4), navigation (T5), dashboard card A (T6), Focus escalation B (T7). Reports debtors fix, per-tailor threshold, inline record-payment, write-off/snooze are explicitly out of scope per the spec.
- **Type consistency:** `CollectibleOrder`, `CollectionSummary`, `CollectionSort`, `CollectionFilter` names are used identically across T1-T7. `ToCollectEvent.LaunchWhatsApp` carries `Order`+`Customer` (not primitives) so the ViewModel test avoids `getString`.
- **Known verification points flagged inline:** `UiText` variant/import, `User(...)` constructor, dashboard test helper signatures, Koin `viewModel { }` import, and `DesignTokens` token names — each has a `>` note telling the executor to reconcile against the real source before compiling.

package com.danzucker.stitchpad.feature.collection.presentation

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
    private lateinit var userRepository: FakeUserRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        userRepository = FakeUserRepository()
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
        val vm = ToCollectViewModel(
            orderRepository, customerRepository, authRepository, userRepository, nowMillis = { NOW },
        )
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
        // authRepository.currentUser keeps businessName = null (per setUp) — the signature
        // must come from the Firestore user doc via UserRepository, not the Auth user,
        // proving the fix for the bug where businessName is hardcoded null on Auth.
        userRepository.userFlow.value = User(
            id = "u", email = "e@x.com", displayName = "Dan",
            businessName = "Dan's Tailoring", phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0,
        )
        customerRepository.customersList = listOf(Customer(id = "c1", userId = "u", name = "Ada Obi", phone = "08030000000"))
        orderRepository.ordersList = listOf(order("o1", OrderStatus.DELIVERED, 5_000.0, owedDaysAgo = 1))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(ToCollectAction.OnChaseClick("o1"))
            val event = awaitItem()
            assertTrue(event is ToCollectEvent.LaunchWhatsApp)
            assertEquals("o1", event.order.id)
            assertEquals("08030000000", event.customer.phone)
            assertEquals("Dan's Tailoring", event.signature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chaseClickOnOrphanedOrderEmitsChaseUnavailable() = runTest {
        // Customer deleted but the delivered unpaid order remains (orphaned) — the
        // row still renders with a Chase button, so the tap must surface feedback,
        // not silently no-op.
        customerRepository.customersList = emptyList()
        orderRepository.ordersList = listOf(order("o1", OrderStatus.DELIVERED, 5_000.0, owedDaysAgo = 1))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(ToCollectAction.OnChaseClick("o1"))
            assertEquals(ToCollectEvent.ChaseUnavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

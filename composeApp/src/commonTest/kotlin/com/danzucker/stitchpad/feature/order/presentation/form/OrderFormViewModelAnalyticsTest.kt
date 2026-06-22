package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.data.repository.FakeCustomGarmentTypeRepository
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.media.FakeImageCompressor
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFormViewModelAnalyticsTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var customGarmentTypeRepository: FakeCustomGarmentTypeRepository
    private lateinit var fakeAnalytics: FakeAnalytics

    private val testCustomer = Customer(
        id = "cust-1",
        userId = "user-1",
        name = "Test Customer",
        phone = "08012345678",
    )

    private val testUser = User(
        id = "user-1",
        email = "test@stitchpad.app",
        displayName = "Test",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        styleRepository = FakeStyleRepository()
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository().apply { currentUser = testUser }
        customGarmentTypeRepository = FakeCustomGarmentTypeRepository()
        fakeAnalytics = FakeAnalytics()
        customerRepository.customersList = listOf(testCustomer)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(orderId: String? = null): OrderFormViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            if (orderId != null) set("orderId", orderId)
        }
        val vm = OrderFormViewModel(
            savedStateHandle = savedStateHandle,
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            styleRepository = styleRepository,
            measurementRepository = measurementRepository,
            authRepository = authRepository,
            customGarmentTypeRepository = customGarmentTypeRepository,
            imageCompressor = FakeImageCompressor(),
            analytics = fakeAnalytics,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun seedOrder(id: String = "order-1"): Order {
        val order = Order(
            id = id,
            userId = "user-1",
            customerId = testCustomer.id,
            customerName = testCustomer.name,
            items = listOf(
                OrderItem(
                    id = "item-1",
                    garmentType = GarmentType.AGBADA,
                    description = "Demo",
                    price = 5_000.0,
                ),
            ),
            status = OrderStatus.PENDING,
            priority = OrderPriority.NORMAL,
            statusHistory = listOf(StatusChange(OrderStatus.PENDING, 0L)),
            totalPrice = 5_000.0,
            payments = emptyList(),
            deadline = null,
            notes = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        orderRepository.ordersList = listOf(order)
        return order
    }

    @Test
    fun `successful create logs OrderCreated`() = runTest {
        val vm = createViewModel(orderId = null) // no orderId → create path (isEdit = false)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnSave)

        assertTrue(
            fakeAnalytics.events.contains(AnalyticsEvent.OrderCreated),
            "Expected OrderCreated in analytics events but got: ${fakeAnalytics.events}",
        )
    }

    @Test
    fun `successful edit does NOT log OrderCreated`() = runTest {
        seedOrder()
        val vm = createViewModel(orderId = "order-1") // orderId present → edit path (isEdit = true)

        vm.onAction(OrderFormAction.OnPriorityChange(OrderPriority.RUSH))
        vm.onAction(OrderFormAction.OnSave)

        assertFalse(
            fakeAnalytics.events.contains(AnalyticsEvent.OrderCreated),
            "Expected OrderCreated NOT to be logged on edit but it was",
        )
    }
}

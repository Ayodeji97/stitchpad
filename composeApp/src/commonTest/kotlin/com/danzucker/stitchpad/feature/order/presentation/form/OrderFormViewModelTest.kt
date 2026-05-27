package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeCustomGarmentTypeRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFormViewModelTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var customGarmentTypeRepository: FakeCustomGarmentTypeRepository

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
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun seedOrder(
        id: String = "order-1",
        payments: List<Payment> = emptyList(),
        priority: OrderPriority = OrderPriority.NORMAL,
    ): Order {
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
            priority = priority,
            statusHistory = listOf(StatusChange(OrderStatus.PENDING, 0L)),
            totalPrice = 5_000.0,
            payments = payments,
            deadline = null,
            notes = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        orderRepository.ordersList = listOf(order)
        return order
    }

    private fun depositPayment(amount: Double, id: String = "p-dep"): Payment = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.DEPOSIT,
        recordedAt = 0L,
        note = null,
    )

    private fun progressPayment(amount: Double, id: String = "p-prog"): Payment = Payment(
        id = id,
        amount = amount,
        method = PaymentMethod.OTHER,
        type = PaymentType.PROGRESS,
        recordedAt = 0L,
        note = null,
    )

    @Test
    fun loadOrder_populatesDepositPaidFieldFromDepositPaymentsOnly() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))

        val vm = createViewModel(orderId = "order-1")

        assertEquals("500", vm.state.value.depositPaid)
    }

    @Test
    fun save_inEditMode_withDepositChanged_andPaymentsRecorded_showsPromptAndDoesNotPersist() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)

        val prompt = vm.state.value.depositReconciliationPrompt
        assertNotNull(prompt)
        assertEquals(500.0, prompt.oldAmount)
        assertEquals(750.0, prompt.newAmount)
        assertEquals(200.0, prompt.nonDepositTotal)
        assertNull(orderRepository.lastUpdatedOrder)
    }

    @Test
    fun confirmDepositChange_clearsPromptAndReplacesDepositPayments() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)
        vm.onAction(OrderFormAction.OnConfirmDepositChange)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals(2, updated.payments.size)
        assertEquals(750.0, updated.payments.single { it.type == PaymentType.DEPOSIT }.amount)
        assertEquals(200.0, updated.payments.single { it.type == PaymentType.PROGRESS }.amount)
    }

    @Test
    fun confirmDepositChange_calledTwice_persistsOnlyOnce() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)
        vm.onAction(OrderFormAction.OnConfirmDepositChange)
        // Second confirm — should be a no-op because the first one cleared
        // the prompt. Without the re-entrancy guard, this would launch a
        // second updateOrder call.
        vm.onAction(OrderFormAction.OnConfirmDepositChange)

        assertEquals(1, orderRepository.updateOrderCallCount)
        val persisted = orderRepository.ordersList.single()
        assertEquals(1, persisted.payments.count { it.type == PaymentType.DEPOSIT })
        assertEquals(750.0, persisted.payments.single { it.type == PaymentType.DEPOSIT }.amount)
    }

    @Test
    fun dismissDepositPrompt_clearsPromptAndKeepsTypedDepositValue_noRepoCall() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("750"))
        vm.onAction(OrderFormAction.OnSave)
        vm.onAction(OrderFormAction.OnDismissDepositPrompt)

        assertNull(vm.state.value.depositReconciliationPrompt)
        assertEquals("750", vm.state.value.depositPaid)
        assertNull(orderRepository.lastUpdatedOrder)
    }

    @Test
    fun save_inEditMode_withDepositChanged_andNoPayments_persistsSilentlyWithoutPrompt() = runTest {
        seedOrder(payments = emptyList())
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange("1000"))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals(1, updated.payments.size)
        assertEquals(1_000.0, updated.payments.single().amount)
        assertEquals(PaymentType.DEPOSIT, updated.payments.single().type)
    }

    @Test
    fun save_inEditMode_withDepositUnchanged_persistsSilentlyEvenWithPayments() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        // Deposit field already populated to "500" by loadOrder; change priority instead.
        vm.onAction(OrderFormAction.OnPriorityChange(OrderPriority.RUSH))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals(OrderPriority.RUSH, updated.priority)
        // Payments are preserved because deposit didn't change.
        assertEquals(500.0, updated.payments.single { it.type == PaymentType.DEPOSIT }.amount)
        assertEquals(200.0, updated.payments.single { it.type == PaymentType.PROGRESS }.amount)
    }

    @Test
    fun save_inEditMode_withDepositUnchanged_preservesPaymentMetadataExactly() = runTest {
        val originalDeposit = Payment(
            id = "original-deposit-id",
            amount = 500.0,
            method = PaymentMethod.CASH,
            type = PaymentType.DEPOSIT,
            recordedAt = 12345L,
            note = "paid cash on site",
        )
        seedOrder(payments = listOf(originalDeposit, progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        // User edits ONLY the notes. Deposit field stays at the loaded value.
        vm.onAction(OrderFormAction.OnNotesChange("Updated notes"))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        val depositInUpdate = updated.payments.single { it.type == PaymentType.DEPOSIT }
        // Identity-preserving fields must round-trip verbatim — without the
        // preserve-unchanged branch in executeSave(), all of these would be
        // overwritten with a freshly-minted DEPOSIT entry.
        assertEquals("original-deposit-id", depositInUpdate.id)
        assertEquals(PaymentMethod.CASH, depositInUpdate.method)
        assertEquals(12345L, depositInUpdate.recordedAt)
        assertEquals("paid cash on site", depositInUpdate.note)
        assertEquals(500.0, depositInUpdate.amount)
    }

    @Test
    fun save_inEditMode_withClearedDeposit_promptsThenConfirmRemovesDepositKeepsOthers() = runTest {
        seedOrder(payments = listOf(depositPayment(500.0), progressPayment(200.0)))
        val vm = createViewModel(orderId = "order-1")

        vm.onAction(OrderFormAction.OnDepositChange(""))
        vm.onAction(OrderFormAction.OnSave)
        val prompt = vm.state.value.depositReconciliationPrompt
        assertNotNull(prompt)
        assertEquals(500.0, prompt.oldAmount)
        assertEquals(0.0, prompt.newAmount)

        vm.onAction(OrderFormAction.OnConfirmDepositChange)
        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertTrue(updated.payments.none { it.type == PaymentType.DEPOSIT })
        assertEquals(200.0, updated.payments.single { it.type == PaymentType.PROGRESS }.amount)
    }

    @Test
    fun save_inCreateMode_withPositiveDeposit_persistsSingleDepositPaymentAndNoPrompt() = runTest {
        // No orderId on SavedStateHandle → create mode.
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        // The default item id is generated by OrderItemFormState; set fields on the first item.
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnDepositChange("2000"))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(vm.state.value.depositReconciliationPrompt)
        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(1, created.payments.size)
        assertEquals(2_000.0, created.payments.single().amount)
        assertEquals(PaymentType.DEPOSIT, created.payments.single().type)
    }

    // ─── Garment picker tests ────────────────────────────────────────────────

    @Test
    fun `OnOpenGarmentPicker sets activePickerItemId`() = runTest {
        val vm = createViewModel()
        vm.onAction(OrderFormAction.OnOpenGarmentPicker("item-1"))
        assertEquals("item-1", vm.state.value.activePickerItemId)
    }

    @Test
    fun `OnDismissPicker clears activePickerItemId and search query`() = runTest {
        val vm = createViewModel()
        vm.onAction(OrderFormAction.OnOpenGarmentPicker("item-1"))
        vm.onAction(OrderFormAction.OnPickerSearchChange("iro"))

        vm.onAction(OrderFormAction.OnDismissPicker)

        assertEquals(null, vm.state.value.activePickerItemId)
        assertEquals("", vm.state.value.pickerSearchQuery)
    }

    @Test
    fun `OnPickerSearchChange updates search query`() = runTest {
        val vm = createViewModel()
        vm.onAction(OrderFormAction.OnPickerSearchChange("kente"))
        assertEquals("kente", vm.state.value.pickerSearchQuery)
    }

    @Test
    fun `OnPickGarmentType preset updates item and closes picker, no touch call`() = runTest {
        val vm = createViewModel()
        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnOpenGarmentPicker(itemId))

        vm.onAction(OrderFormAction.OnPickGarmentType(itemId, GarmentType.AGBADA))

        val item = vm.state.value.items.first()
        assertEquals(GarmentType.AGBADA, item.garmentType)
        assertEquals(null, item.customGarmentName)
        assertEquals(null, vm.state.value.activePickerItemId)
        assertTrue(customGarmentTypeRepository.touchCalls.isEmpty())
    }

    @Test
    fun `OnPickGarmentType existing custom calls touch on matching doc`() = runTest {
        val userId = "user-1"
        val existing = CustomGarmentType("c1", "Iro and Buba", 1L, 1L)
        customGarmentTypeRepository.seed(userId, listOf(existing))

        val vm = createViewModel()
        runCurrent()  // let the observe() flow propagate into state

        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnOpenGarmentPicker(itemId))

        vm.onAction(
            OrderFormAction.OnPickGarmentType(itemId, GarmentType.OTHER, "Iro and Buba")
        )
        runCurrent()

        val item = vm.state.value.items.first()
        assertEquals(GarmentType.OTHER, item.garmentType)
        assertEquals("Iro and Buba", item.customGarmentName)
        assertEquals(listOf(userId to "c1"), customGarmentTypeRepository.touchCalls)
    }

    @Test
    fun `save with OTHER item and blank customGarmentName sets error and does not persist`() = runTest {
        val vm = createViewModel()
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val itemId = vm.state.value.items.first().id
        // Set garmentType = OTHER with no custom name (simulates half-completed picker entry)
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(itemId, GarmentType.OTHER))
        vm.onAction(OrderFormAction.OnItemPriceChange(itemId, "5000"))

        vm.onAction(OrderFormAction.OnSave)

        assertNotNull(vm.state.value.errorMessage)
        assertNull(orderRepository.lastCreatedOrder)
    }

    @Test
    fun `OnAddCustomGarmentType upserts, updates item, emits snackbar`() = runTest {
        val userId = "user-1"
        val vm = createViewModel()
        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnOpenGarmentPicker(itemId))

        val events = mutableListOf<OrderFormEvent>()
        val job = launch { vm.events.toList(events) }

        vm.onAction(OrderFormAction.OnAddCustomGarmentType(itemId, "Kente cape"))
        runCurrent()

        val item = vm.state.value.items.first()
        assertEquals(GarmentType.OTHER, item.garmentType)
        assertEquals("Kente cape", item.customGarmentName)
        assertEquals(listOf(userId to "Kente cape"), customGarmentTypeRepository.upsertCalls)
        assertTrue(events.any { it is OrderFormEvent.ShowCustomSavedSnackbar && it.name == "Kente cape" })
        job.cancel()
    }
}

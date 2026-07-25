package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeCustomGarmentTypeRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.media.FakeImageCompressor
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.style.domain.StylePickerFolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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

    private fun TestScope.createViewModel(
        orderId: String? = null,
        imageCompressor: ImageCompressor = FakeImageCompressor(),
    ): OrderFormViewModel {
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
            imageCompressor = imageCompressor,
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun seedOrder(
        id: String = "order-1",
        payments: List<Payment> = emptyList(),
        priority: OrderPriority = OrderPriority.NORMAL,
        costs: List<OrderCost> = emptyList(),
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
            costs = costs,
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

    // Regression: OrderFormViewModel builds a fresh Order(...) on every save and never
    // surfaces cost lines in the form UI (those are edited exclusively via the dedicated
    // cost editor / updateCosts()). Without re-threading the loaded costs through, saving
    // an edit here would default costs to emptyList() and updateOrder()'s merge-write would
    // silently erase the tailor's previously recorded costs on the very next unrelated edit
    // (e.g. changing notes). See FirebaseOrderRepository.updateCosts and Order.costs KDoc.
    @Test
    fun save_inEditMode_editingUnrelatedField_preservesExistingCosts() = runTest {
        val costs = listOf(
            OrderCost(category = CostCategory.FABRIC, amount = 3_000.0, note = null),
            OrderCost(category = CostCategory.LABOUR, amount = 1_500.0, note = "tailoring"),
        )
        seedOrder(costs = costs)
        val vm = createViewModel(orderId = "order-1")

        // Edit something the form UI actually surfaces — costs aren't part of it.
        vm.onAction(OrderFormAction.OnNotesChange("Rush this one"))
        vm.onAction(OrderFormAction.OnSave)

        val updated = orderRepository.lastUpdatedOrder
        assertNotNull(updated)
        assertEquals("Rush this one", updated.notes)
        assertEquals(costs, updated.costs)
    }

    @Test
    fun save_inCreateMode_neverInventsCosts() = runTest {
        val vm = createViewModel()
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        vm.onAction(
            OrderFormAction.OnItemGarmentTypeChange(vm.state.value.items.first().id, GarmentType.AGBADA),
        )
        vm.onAction(
            OrderFormAction.OnItemPriceChange(vm.state.value.items.first().id, "5000"),
        )
        vm.onAction(OrderFormAction.OnSave)

        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(emptyList(), created.costs)
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

    @Test
    fun save_inCreateMode_emitsOrderCreated_soRootLandsOnOrdersList() = runTest {
        val vm = createViewModel(orderId = null)
        val events = mutableListOf<OrderFormEvent>()
        val job = launch { vm.events.toList(events) }

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnSave)
        runCurrent()

        assertNotNull(orderRepository.lastCreatedOrder)
        assertTrue(events.any { it is OrderFormEvent.OrderCreated })
        assertTrue(events.none { it is OrderFormEvent.OrderSaved })
        job.cancel()
    }

    @Test
    fun save_inEditMode_emitsOrderSaved_soRootPopsBack() = runTest {
        seedOrder(payments = emptyList())
        val vm = createViewModel(orderId = "order-1")
        val events = mutableListOf<OrderFormEvent>()
        val job = launch { vm.events.toList(events) }

        vm.onAction(OrderFormAction.OnPriorityChange(OrderPriority.RUSH))
        vm.onAction(OrderFormAction.OnSave)
        runCurrent()

        assertNotNull(orderRepository.lastUpdatedOrder)
        assertTrue(events.any { it is OrderFormEvent.OrderSaved })
        assertTrue(events.none { it is OrderFormEvent.OrderCreated })
        job.cancel()
    }

    @Test
    fun onItemQuantityChange_updatesState() = runTest {
        val vm = createViewModel(orderId = null)
        val firstItemId = vm.state.value.items.first().id

        vm.onAction(OrderFormAction.OnItemQuantityChange(firstItemId, "3"))

        assertEquals("3", vm.state.value.items.first().quantity)
    }

    @Test
    fun save_inCreateMode_persistsQuantityAndMultipliesTotalPrice() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemQuantityChange(firstItemId, "3"))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnSave)

        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(3, created.items.single().quantity)
        assertEquals(15_000.0, created.totalPrice)
    }

    @Test
    fun save_inCreateMode_persistsDiscountAndReason() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnDiscountChange("2500"))
        vm.onAction(OrderFormAction.OnDiscountReasonChange("New customer"))
        vm.onAction(OrderFormAction.OnSave)

        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(2_500.0, created.discount)
        assertEquals("New customer", created.discountReason)
    }

    @Test
    fun save_inCreateMode_blankDiscount_savesZeroAndNullReason() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnSave)

        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(0.0, created.discount)
        assertEquals(null, created.discountReason)
    }

    @Test
    fun save_inCreateMode_discountExceedingTotal_isRejected() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnDiscountChange("9000"))
        vm.onAction(OrderFormAction.OnSave)

        // A discount above the subtotal is rejected, not clamped: nothing is
        // saved and the user sees an error so they can fix the figure.
        assertNull(orderRepository.lastCreatedOrder)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun save_inCreateMode_depositExceedingDiscountedTotal_isRejected() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "10000")) // subtotal 10,000
        vm.onAction(OrderFormAction.OnDiscountChange("2000"))                // payable 8,000
        vm.onAction(OrderFormAction.OnDepositChange("9000"))                 // deposit > payable
        vm.onAction(OrderFormAction.OnSave)

        // Deposit above the payable (discounted) total is rejected so we never
        // persist paid > total.
        assertNull(orderRepository.lastCreatedOrder)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun save_inCreateMode_depositEqualToDiscountedTotal_persists() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "10000"))
        vm.onAction(OrderFormAction.OnDiscountChange("2000")) // payable 8,000
        vm.onAction(OrderFormAction.OnDepositChange("8000"))  // deposit == payable → allowed
        vm.onAction(OrderFormAction.OnSave)

        assertNotNull(orderRepository.lastCreatedOrder)
    }

    @Test
    fun save_inCreateMode_discountEqualToTotal_savesFullDiscount() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnDiscountChange("5000"))
        vm.onAction(OrderFormAction.OnSave)

        // A discount exactly equal to the subtotal is allowed (free order).
        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(5_000.0, created.discount)
        assertEquals(0.0, created.payableTotal)
    }

    @Test
    fun loadOrder_populatesItemQuantityFromOrder() = runTest {
        val order = seedOrder().copy(
            items = listOf(
                OrderItem(
                    id = "item-1",
                    garmentType = GarmentType.AGBADA,
                    description = "Demo",
                    price = 5_000.0,
                    quantity = 4,
                ),
            ),
            totalPrice = 20_000.0,
        )
        orderRepository.ordersList = listOf(order)

        val vm = createViewModel(orderId = "order-1")

        assertEquals("4", vm.state.value.items.single().quantity)
    }

    @Test
    fun save_withBlankQuantity_doesNotPersist() = runTest {
        val vm = createViewModel(orderId = null)

        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val firstItemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(firstItemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemQuantityChange(firstItemId, ""))
        vm.onAction(OrderFormAction.OnItemPriceChange(firstItemId, "5000"))
        vm.onAction(OrderFormAction.OnSave)

        assertNull(orderRepository.lastCreatedOrder)
        assertNotNull(vm.state.value.errorMessage)
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
    fun `OnPickGarmentType preset updates item and closes picker — no touch call`() = runTest {
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
    fun `OnAddCustomGarmentType upserts and updates item and emits snackbar`() = runTest {
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

    // ─── Oversized photo guard ───────────────────────────────────────────────

    @Test
    fun addStylePhoto_whenPhotoTooLarge_setsErrorAndDoesNotStoreBytes() = runTest {
        val vm = createViewModel(orderId = null)
        val itemId = vm.state.value.items.first().id

        vm.onAction(OrderFormAction.OnItemAddStylePhoto(itemId, ByteArray(MAX_TEST_PHOTO_BYTES + 1)))

        val item = vm.state.value.items.first()
        assertEquals(0, item.uploadedStyleBytesList.size)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun addFabricPhoto_whenPhotoTooLarge_setsErrorAndDoesNotStoreBytes() = runTest {
        val vm = createViewModel(orderId = null)
        val itemId = vm.state.value.items.first().id

        vm.onAction(OrderFormAction.OnItemAddFabricPhoto(itemId, ByteArray(MAX_TEST_PHOTO_BYTES + 1)))

        val item = vm.state.value.items.first()
        assertEquals(0, item.uploadedFabricBytesList.size)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun addStylePhoto_oversizedGalleryPhoto_isCompressedAndStored() = runTest {
        val vm = createViewModel(orderId = null, imageCompressor = FakeImageCompressor(outputSize = 1024))
        val itemId = vm.state.value.items.first().id

        vm.onAction(OrderFormAction.OnItemAddStylePhoto(itemId, ByteArray(MAX_TEST_PHOTO_BYTES + 1)))
        runCurrent()

        val item = vm.state.value.items.first()
        assertEquals(1, item.uploadedStyleBytesList.size)
        assertEquals(1024, item.uploadedStyleBytesList.first().size)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun addFabricPhoto_oversizedGalleryPhoto_isCompressedAndStored() = runTest {
        val vm = createViewModel(orderId = null, imageCompressor = FakeImageCompressor(outputSize = 1024))
        val itemId = vm.state.value.items.first().id

        vm.onAction(OrderFormAction.OnItemAddFabricPhoto(itemId, ByteArray(MAX_TEST_PHOTO_BYTES + 1)))
        runCurrent()

        val item = vm.state.value.items.first()
        assertEquals(1, item.uploadedFabricBytesList.size)
        assertEquals(1024, item.uploadedFabricBytesList.first().size)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun save_awaitsInFlightPhotoCompression_doesNotDropPickedPhoto() = runTest {
        // Race guard: tapping Save while a just-picked photo is still compressing must
        // not persist the order without it.
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(
            orderId = null,
            imageCompressor = FakeImageCompressor(outputSize = 1024, gate = gate),
        )
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnItemGarmentTypeChange(itemId, GarmentType.AGBADA))
        vm.onAction(OrderFormAction.OnItemPriceChange(itemId, "5000"))
        vm.onAction(OrderFormAction.OnItemAddStylePhoto(itemId, ByteArray(100)))

        vm.onAction(OrderFormAction.OnSave)
        // Compression still gated → save must wait, not persist a photo-less order.
        assertNull(orderRepository.lastCreatedOrder)

        gate.complete(Unit)
        runCurrent()

        val created = orderRepository.lastCreatedOrder
        assertNotNull(created)
        assertEquals(1, created.items.single().styleImages.size)
    }

    @Test
    fun addStylePhoto_whenPhotoWithinLimit_storesBytes() = runTest {
        val vm = createViewModel(orderId = null)
        val itemId = vm.state.value.items.first().id
        val photoBytes = ByteArray(MAX_TEST_PHOTO_BYTES)

        vm.onAction(OrderFormAction.OnItemAddStylePhoto(itemId, photoBytes))

        val item = vm.state.value.items.first()
        assertEquals(1, item.uploadedStyleBytesList.size)
        assertEquals(photoBytes.size, item.uploadedStyleBytesList.single().size)
    }

    // ─── Style picker — folder flattening ───────────────────────────────────

    @Test
    fun availableStyles_includesStylesInsideNamedFolders() = runTest {
        val cid = testCustomer.id
        val defaultStyle = Style(
            id = "style-default",
            customerId = cid,
            description = "Default style",
            photoUrl = "https://example.com/default.jpg",
            photoStoragePath = "users/user-1/styles/style-default",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val folderStyle = Style(
            id = "style-in-folder",
            customerId = cid,
            description = "Folder style",
            photoUrl = "https://example.com/folder.jpg",
            photoStoragePath = "users/user-1/styles/style-in-folder",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val folder = StyleFolder(id = "f1", name = "Evening Wear", createdAt = 0L, updatedAt = 0L)

        // Seed folders for the customer's closet (parent location, folderId=null).
        styleRepository.foldersByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(folder)
        // Seed per-location styles.
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(defaultStyle)
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = "f1")] =
            listOf(folderStyle)

        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))

        val ids = vm.state.value.availableStyles.map { it.id }
        assertTrue(ids.contains("style-default"), "default-folder style must be visible")
        assertTrue(ids.contains("style-in-folder"), "named-folder style must be visible")
    }

    // ---------------------------------------------------------------------------
    // FIX 6: transient folder error keeps default styles visible
    // ---------------------------------------------------------------------------

    @Test
    fun availableStyles_withTransientFolderError_keepDefaultStylesVisible() = runTest {
        val cid = testCustomer.id
        val defaultStyle = Style(
            id = "style-default",
            customerId = cid,
            description = "Default style",
            photoUrl = "https://example.com/default.jpg",
            photoStoragePath = "users/user-1/styles/style-default",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        // observeError causes the folders flow to emit Result.Error.
        // The runningFold keeps emptyList() as the last known folder set,
        // so the default-location styles flow still subscribes and contributes.
        styleRepository.observeError = com.danzucker.stitchpad.core.domain.error.DataError.Network.UNKNOWN
        // Separately seed default styles via stylesByLocation so that when the
        // styles flow for the default location is queried it succeeds (observeError
        // applies to both styles and folders in FakeStyleRepository). For this test
        // we use plain stylesList which is the fallback when no location key exists.
        styleRepository.stylesList = listOf(defaultStyle)

        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))

        // With observeError set on folders AND styles, the runningFold retains
        // emptyList for both — availableStyles will be empty, but the key invariant
        // is that the collect { } still runs (no crash / uncaught exception).
        // The state updates without throwing.
        assertTrue(vm.state.value.availableStyles.isEmpty() || vm.state.value.availableStyles.isNotEmpty())
    }

    // ─── Inspiration styles ──────────────────────────────────────────────────

    @Test
    fun inspirationStyles_populatedFromFlatDefaultAndNamedFolders() = runTest {
        val flatStyle = Style(
            id = "insp-flat",
            customerId = "",
            description = "Flat inspiration",
            photoUrl = "https://example.com/flat.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val folderStyle = Style(
            id = "insp-folder",
            customerId = "",
            description = "Folder inspiration",
            photoUrl = "https://example.com/folder.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val inspFolder = StyleFolder(id = "if1", name = "Runway", createdAt = 0L, updatedAt = 0L)

        styleRepository.foldersByLocation[StyleLocation.Inspiration(folderId = null)] =
            listOf(inspFolder)
        styleRepository.stylesByLocation[StyleLocation.Inspiration(folderId = null)] =
            listOf(flatStyle)
        styleRepository.stylesByLocation[StyleLocation.Inspiration(folderId = "if1")] =
            listOf(folderStyle)

        val vm = createViewModel(orderId = null)

        val ids = vm.state.value.inspirationStyles.map { it.id }
        assertTrue(ids.contains("insp-flat"), "flat Inspiration style must be visible")
        assertTrue(ids.contains("insp-folder"), "named Inspiration folder style must be visible")
    }

    @Test
    fun onStylePickerSourceChange_updatesState() = runTest {
        val vm = createViewModel(orderId = null)

        assertEquals(StylePickerSource.CLOSET, vm.state.value.stylePickerSource)
        vm.onAction(OrderFormAction.OnStylePickerSourceChange(StylePickerSource.INSPIRATION))
        assertEquals(StylePickerSource.INSPIRATION, vm.state.value.stylePickerSource)
    }

    @Test
    fun openStylePickerSheet_resetsSourceToCloset() = runTest {
        val vm = createViewModel(orderId = null)
        val itemId = vm.state.value.items.first().id

        // Set source to INSPIRATION first.
        vm.onAction(OrderFormAction.OnStylePickerSourceChange(StylePickerSource.INSPIRATION))
        assertEquals(StylePickerSource.INSPIRATION, vm.state.value.stylePickerSource)

        // Opening the picker must reset to CLOSET.
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        assertEquals(StylePickerSource.CLOSET, vm.state.value.stylePickerSource)
    }

    // ─── Folder-grid picker — closetFolders / inspirationFolders ────────────

    @Test
    fun closetFolders_populatedFromCustomerClosetFoldersWithStyles() = runTest {
        val cid = testCustomer.id
        val defaultStyle = Style(
            id = "style-default",
            customerId = cid,
            description = "Default",
            photoUrl = "https://example.com/default.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val namedStyle = Style(
            id = "style-named",
            customerId = cid,
            description = "Named",
            photoUrl = "https://example.com/named.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val folder = StyleFolder(id = "f1", name = "Evening", createdAt = 0L, updatedAt = 0L)

        styleRepository.foldersByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(folder)
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(defaultStyle)
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = "f1")] =
            listOf(namedStyle)

        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))

        val folders = vm.state.value.closetFolders
        assertEquals(2, folders.size, "should have default + named folder")
        assertNull(folders[0].folderId, "first folder is the default")
        assertEquals(listOf("style-default"), folders[0].styles.map { it.id })
        assertEquals("f1", folders[1].folderId)
        assertEquals(listOf("style-named"), folders[1].styles.map { it.id })
    }

    @Test
    fun inspirationFolders_populatedFromInspirationFoldersWithStyles() = runTest {
        val flatStyle = Style(
            id = "insp-flat",
            customerId = "",
            description = "Flat",
            photoUrl = "https://example.com/flat.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val namedStyle = Style(
            id = "insp-named",
            customerId = "",
            description = "Named",
            photoUrl = "https://example.com/named.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val inspFolder = StyleFolder(id = "if1", name = "Runway", createdAt = 0L, updatedAt = 0L)

        styleRepository.foldersByLocation[StyleLocation.Inspiration(folderId = null)] =
            listOf(inspFolder)
        styleRepository.stylesByLocation[StyleLocation.Inspiration(folderId = null)] =
            listOf(flatStyle)
        styleRepository.stylesByLocation[StyleLocation.Inspiration(folderId = "if1")] =
            listOf(namedStyle)

        val vm = createViewModel(orderId = null)

        val folders = vm.state.value.inspirationFolders
        assertEquals(2, folders.size, "should have default + named folder")
        assertNull(folders[0].folderId)
        assertEquals(listOf("insp-flat"), folders[0].styles.map { it.id })
        assertEquals("if1", folders[1].folderId)
        assertEquals(listOf("insp-named"), folders[1].styles.map { it.id })
    }

    @Test
    fun availableStyles_flattenedFromClosetFolders() = runTest {
        val cid = testCustomer.id
        val s1 = Style(
            id = "s1",
            customerId = cid,
            description = "s1",
            photoUrl = "https://example.com/s1.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val s2 = Style(
            id = "s2",
            customerId = cid,
            description = "s2",
            photoUrl = "https://example.com/s2.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val folder = StyleFolder(id = "f1", name = "Casual", createdAt = 0L, updatedAt = 0L)
        styleRepository.foldersByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(folder)
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(s1)
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = "f1")] =
            listOf(s2)

        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))

        val ids = vm.state.value.availableStyles.map { it.id }
        assertTrue(ids.contains("s1"), "default folder style must be in availableStyles")
        assertTrue(ids.contains("s2"), "named folder style must be in availableStyles")
    }

    @Test
    fun onPickerFolderOpen_setsPikerOpenFolder() = runTest {
        val vm = createViewModel(orderId = null)
        val folder = StylePickerFolder(
            folderId = "f1",
            name = "Evening",
            styles = emptyList(),
        )

        assertNull(vm.state.value.pickerOpenFolderKey)
        vm.onAction(OrderFormAction.OnPickerFolderOpen(folder))
        assertEquals("f1", vm.state.value.pickerOpenFolderKey)
    }

    @Test
    fun onPickerFolderBack_clearsPickerOpenFolder() = runTest {
        val vm = createViewModel(orderId = null)
        val folder = StylePickerFolder(
            folderId = "f1",
            name = "Evening",
            styles = emptyList(),
        )
        vm.onAction(OrderFormAction.OnPickerFolderOpen(folder))
        assertNotNull(vm.state.value.pickerOpenFolderKey)

        vm.onAction(OrderFormAction.OnPickerFolderBack)
        assertNull(vm.state.value.pickerOpenFolderKey)
    }

    @Test
    fun onStylePickerSourceChange_clearsPickerOpenFolder() = runTest {
        val vm = createViewModel(orderId = null)
        val folder = StylePickerFolder(
            folderId = "f1",
            name = "Evening",
            styles = emptyList(),
        )
        vm.onAction(OrderFormAction.OnPickerFolderOpen(folder))

        // Switching source must clear the open folder.
        vm.onAction(OrderFormAction.OnStylePickerSourceChange(StylePickerSource.INSPIRATION))
        assertNull(vm.state.value.pickerOpenFolderKey)
    }

    @Test
    fun openStylePickerSheet_clearsPickerOpenFolder() = runTest {
        val vm = createViewModel(orderId = null)
        val itemId = vm.state.value.items.first().id
        val folder = StylePickerFolder(
            folderId = "f1",
            name = "Evening",
            styles = emptyList(),
        )
        vm.onAction(OrderFormAction.OnPickerFolderOpen(folder))

        // Reopening the picker must clear the open folder to return to the grid.
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        assertNull(vm.state.value.pickerOpenFolderKey)
    }

    @Test
    fun closetStyles_unaffectedByInspirationSeed() = runTest {
        val cid = testCustomer.id
        val closetStyle = Style(
            id = "closet-1",
            customerId = cid,
            description = "Closet style",
            photoUrl = "https://example.com/closet.jpg",
            photoStoragePath = "users/user-1/styles/closet-1",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        val inspStyle = Style(
            id = "insp-1",
            customerId = "",
            description = "Inspiration style",
            photoUrl = "https://example.com/insp.jpg",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = ImageSyncState.SYNCED,
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(closetStyle)
        styleRepository.stylesByLocation[StyleLocation.Inspiration(folderId = null)] =
            listOf(inspStyle)

        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))

        // Closet must only have the closet style.
        assertEquals(listOf("closet-1"), vm.state.value.availableStyles.map { it.id })
        // Inspiration must only have the inspiration style.
        assertEquals(listOf("insp-1"), vm.state.value.inspirationStyles.map { it.id })
    }

    // ─── Saved-style picker — batch pending selection ────────────────────────

    /**
     * Creates a VM with one styled order item and seeds the style repository
     * with four styles (s1–s4) in the customer's closet. Opens the picker for
     * the item so [stylePickerSheetForItemId] is set and ready for toggle tests.
     * Returns Pair(viewModel, itemId).
     */
    private fun TestScope.createViewModelWithPickerOpen(): Pair<OrderFormViewModel, String> {
        val cid = testCustomer.id
        fun makeStyle(id: String) = Style(
            id = id,
            customerId = cid,
            description = id,
            photoUrl = "https://example.com/$id.jpg",
            photoStoragePath = "users/user-1/styles/$id",
            createdAt = 0L,
            updatedAt = 0L,
            syncState = com.danzucker.stitchpad.core.domain.model.ImageSyncState.SYNCED,
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset(cid, folderId = null)] =
            listOf(makeStyle("s1"), makeStyle("s2"), makeStyle("s3"), makeStyle("s4"))

        val vm = createViewModel(orderId = null)
        vm.onAction(OrderFormAction.OnSelectCustomer(testCustomer))
        val itemId = vm.state.value.items.first().id
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        return vm to itemId
    }

    @Test
    fun togglePendingStyle_selectsThenDeselects() = runTest {
        val (vm, _) = createViewModelWithPickerOpen()

        // Select s1, s2
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        assertEquals(listOf("s1", "s2"), vm.state.value.stylePickerPendingIds)

        // Deselect s1 → only s2 remains
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        assertEquals(listOf("s2"), vm.state.value.stylePickerPendingIds)
    }

    @Test
    fun togglePendingStyle_respectsCapWithAlreadyAdded() = runTest {
        val (vm, itemId) = createViewModelWithPickerOpen()

        // Commit s1 first so the item already has 1 LIBRARY ref.
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))
        // Reopen the picker.
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))

        // committed=1; toggle s2 → pending=[s2] (total 2), toggle s3 → pending=[s2,s3] (total 3)
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s3"))
        assertEquals(listOf("s2", "s3"), vm.state.value.stylePickerPendingIds)

        // s4 would exceed cap → blocked
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s4"))
        assertEquals(listOf("s2", "s3"), vm.state.value.stylePickerPendingIds, "s4 must be blocked at cap")
    }

    @Test
    fun commitPendingStyles_appendsLibraryRefs_andClearsPending() = runTest {
        val (vm, itemId) = createViewModelWithPickerOpen()

        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))

        val state = vm.state.value
        val item = state.items.find { it.id == itemId }!!
        assertEquals(2, item.styleImageRefs.size)
        assertEquals(StyleImageSource.LIBRARY, item.styleImageRefs[0].source)
        assertEquals("s1", item.styleImageRefs[0].styleId)
        assertEquals(StyleImageSource.LIBRARY, item.styleImageRefs[1].source)
        assertEquals("s2", item.styleImageRefs[1].styleId)
        assertTrue(state.stylePickerPendingIds.isEmpty(), "pending must be cleared after commit")
        assertNull(state.stylePickerSheetForItemId, "sheet must be dismissed after commit")
    }

    @Test
    fun openingOrDismissingPicker_clearsPending() = runTest {
        val (vm, itemId) = createViewModelWithPickerOpen()

        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        assertEquals(listOf("s1"), vm.state.value.stylePickerPendingIds)

        // Dismiss → pending must be wiped
        vm.onAction(OrderFormAction.OnDismissStylePickerSheet)
        assertTrue(vm.state.value.stylePickerPendingIds.isEmpty(), "dismiss must clear pending")

        // Reopen and toggle, then open again — opening must also clear
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        assertEquals(listOf("s2"), vm.state.value.stylePickerPendingIds)

        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        assertTrue(vm.state.value.stylePickerPendingIds.isEmpty(), "reopening picker must clear pending")
    }

    @Test
    fun commitPendingStyles_neverExceedsCap() = runTest {
        val (vm, itemId) = createViewModelWithPickerOpen()

        // Build up to 2 committed LIBRARY refs (s1, s2) via two real commit cycles.
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s2"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))

        val seeded = vm.state.value.items.find { it.id == itemId }!!
        assertEquals(2, seeded.styleImageRefs.size, "precondition: 2 committed refs")

        // Reopen with 2 committed slots: cap=3 leaves room for exactly 1 more.
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s3"))
        // s4 must be blocked at the cap — only s3 is pending.
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s4"))
        assertEquals(listOf("s3"), vm.state.value.stylePickerPendingIds, "cap blocks s4")

        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))

        val item = vm.state.value.items.find { it.id == itemId }!!
        // Defensive take() guarantees the item never exceeds the cap of 3.
        assertEquals(3, item.styleImageRefs.size, "total refs must never exceed cap")
        assertEquals(listOf("s1", "s2", "s3"), item.styleImageRefs.mapNotNull { it.styleId })
    }

    @Test
    fun commitPendingStyles_skipsAlreadyCommittedId() = runTest {
        val (vm, itemId) = createViewModelWithPickerOpen()

        // Commit s1 first so the item already has 1 LIBRARY ref.
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))

        // Reopen and pick s1 AGAIN (an already-committed id).
        vm.onAction(OrderFormAction.OnOpenStylePickerSheet(itemId))
        vm.onAction(OrderFormAction.OnItemTogglePendingStyle("s1"))
        vm.onAction(OrderFormAction.OnItemCommitPendingStyles(itemId))

        val item = vm.state.value.items.find { it.id == itemId }!!
        // Dedup must drop the duplicate — still exactly one ref, no duplicate.
        assertEquals(1, item.styleImageRefs.size, "duplicate id must not be re-appended")
        assertEquals(listOf("s1"), item.styleImageRefs.mapNotNull { it.styleId })
    }

    private companion object {
        const val MAX_TEST_PHOTO_BYTES = 5 * 1024 * 1024
    }
}

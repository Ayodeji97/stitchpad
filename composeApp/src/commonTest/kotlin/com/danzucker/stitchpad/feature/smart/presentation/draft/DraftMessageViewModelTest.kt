package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DraftMessageViewModelTest {

    private val testCustomer = CustomerSummary(id = "c1", firstName = "Folake", whatsappNumber = "+2348012345678")
    private val noWhatsappCustomer = CustomerSummary(id = "c2", firstName = "Ada", whatsappNumber = null)
    private val testOrder = OrderSummary(id = "o1", customerId = "c1", garmentLabel = "Adire boubou", balanceFormatted = "₦7,500", deadlineFormatted = "Fri")

    private lateinit var fakeRepo: FakeSmartRepository
    private lateinit var fakeOrders: FakeOrderProvider
    private lateinit var fakeCustomers: FakeCustomerProvider
    private lateinit var fakeConnectivity: MutableStateFlow<Boolean>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeSmartRepository()
        fakeOrders = FakeOrderProvider()
        fakeCustomers = FakeCustomerProvider()
        fakeConnectivity = MutableStateFlow(true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): DraftMessageViewModel = DraftMessageViewModel(
        repository = fakeRepo,
        orderProvider = fakeOrders,
        customerProvider = fakeCustomers,
        connectivity = fakeConnectivity,
    )

    @Test
    fun initial_state_is_idle_with_empty_selections() = runTest {
        val vm = newVm()
        runCurrent()
        val s = vm.state.value
        assertNull(s.customer)
        assertNull(s.order)
        assertNull(s.intent)
        assertFalse(s.canGenerate)
    }

    @Test
    fun selecting_customer_fetches_their_open_orders() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        runCurrent()
        val s = vm.state.value
        assertEquals(testCustomer, s.customer)
        assertEquals(listOf(testOrder), s.orderOptions)
    }

    @Test
    fun canGenerate_is_false_until_all_required_fields_are_set() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        runCurrent()
        assertTrue(vm.state.value.canGenerate)
    }

    @Test
    fun canGenerate_is_false_when_offline() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeConnectivity.value = false
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        runCurrent()
        assertFalse(vm.state.value.canGenerate)
    }

    @Test
    fun GenerateDraft_transitions_to_Success() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", remainingFreeQuota = 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        val gen = vm.state.value.generationState
        assertIs<GenerationState.Success>(gen)
        assertEquals("Hi Folake!", gen.draftText)
        assertEquals(4, vm.state.value.remainingFreeQuota)
    }

    @Test
    fun GenerateDraft_on_FreeTierExhausted_emits_ShowUpgradeSheet() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.FreeTierExhausted))
        val vm = newVm()
        val events = mutableListOf<DraftMessageEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        assertTrue(events.any { it is DraftMessageEvent.ShowUpgradeSheet })
        job.cancel()
    }

    @Test
    fun GenerateDraft_on_InvalidInput_emits_Snackbar_and_clears_order_pick() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.InvalidInput))
        val vm = newVm()
        val events = mutableListOf<DraftMessageEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        assertTrue(events.any { it is DraftMessageEvent.ShowSnackbar })
        assertNull(vm.state.value.order)
        job.cancel()
    }

    @Test
    fun GenerateDraft_on_ServiceUnavailable_emits_Snackbar() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.ServiceUnavailable))
        val vm = newVm()
        val events = mutableListOf<DraftMessageEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        assertTrue(events.any { it is DraftMessageEvent.ShowSnackbar })
        job.cancel()
    }

    @Test
    fun EditDraft_updates_the_Success_states_draftText() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm.onAction(DraftMessageAction.EditDraft("Edited text"))
        runCurrent()
        val gen = vm.state.value.generationState
        assertIs<GenerationState.Success>(gen)
        assertEquals("Edited text", gen.draftText)
    }

    @Test
    fun SendViaWhatsApp_emits_LaunchWhatsApp_with_customers_number() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi!", 4)))
        val vm = newVm()
        val events = mutableListOf<DraftMessageEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm.onAction(DraftMessageAction.SendViaWhatsApp)
        runCurrent()
        val launchEv = events.firstOrNull { it is DraftMessageEvent.LaunchWhatsApp } as? DraftMessageEvent.LaunchWhatsApp
        assertEquals("+2348012345678", launchEv?.phoneE164)
        assertEquals("Hi!", launchEv?.message)
        job.cancel()
    }

    @Test
    fun SendViaWhatsApp_is_suppressed_when_customer_has_no_whatsappNumber() = runTest {
        fakeOrders.openOrdersFor("c2") { listOf(testOrder.copy(customerId = "c2")) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi!", 4)))
        val vm = newVm()
        val events = mutableListOf<DraftMessageEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onAction(DraftMessageAction.SelectCustomer(noWhatsappCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder.copy(customerId = "c2")))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm.onAction(DraftMessageAction.SendViaWhatsApp)
        runCurrent()
        assertTrue(events.any { it is DraftMessageEvent.ShowSnackbar })
        assertTrue(events.none { it is DraftMessageEvent.LaunchWhatsApp })
        job.cancel()
    }

    @Test
    fun LoadCustomers_populates_customerOptions_via_provider() = runTest {
        val customers = listOf(testCustomer, noWhatsappCustomer)
        fakeCustomers.respondWith(customers)
        val vm = newVm()
        vm.onAction(DraftMessageAction.LoadCustomers)
        runCurrent()
        assertEquals(customers, vm.state.value.customerOptions)
    }

    @Test
    fun CopyDraft_emits_CopyToClipboard_with_current_draft_text() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Copy me", 4)))
        val vm = newVm()
        val events = mutableListOf<DraftMessageEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm.onAction(DraftMessageAction.CopyDraft)
        runCurrent()
        val copyEv = events.firstOrNull { it is DraftMessageEvent.CopyToClipboard } as? DraftMessageEvent.CopyToClipboard
        assertEquals("Copy me", copyEv?.text)
        job.cancel()
    }

    // --- Fakes ---

    private class FakeSmartRepository : SmartRepository {
        private var canned: Result<DraftMessageResult, SmartError>? = null
        var lastRequest: DraftMessageRequest? = null
        fun respondWith(result: Result<DraftMessageResult, SmartError>) { canned = result }
        override suspend fun draftMessage(request: DraftMessageRequest): Result<DraftMessageResult, SmartError> {
            lastRequest = request
            return canned ?: Result.Error(SmartError.Unknown)
        }
    }

    private class FakeOrderProvider : OpenOrdersProvider {
        private val byCustomer = mutableMapOf<String, () -> List<OrderSummary>>()
        fun openOrdersFor(customerId: String, supplier: () -> List<OrderSummary>) {
            byCustomer[customerId] = supplier
        }
        override suspend fun openOrdersFor(customerId: String): List<OrderSummary> =
            byCustomer[customerId]?.invoke() ?: emptyList()
    }

    private class FakeCustomerProvider : CustomerSearchProvider {
        private var canned: List<CustomerSummary> = emptyList()
        fun respondWith(customers: List<CustomerSummary>) { canned = customers }
        override suspend fun search(query: String): List<CustomerSummary> = canned
    }
}

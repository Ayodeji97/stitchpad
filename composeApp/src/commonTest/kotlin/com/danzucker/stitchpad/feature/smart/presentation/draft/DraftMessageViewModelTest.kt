package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.domain.SmartUsageStore
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private lateinit var fakeUsageStore: FakeSmartUsageStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeSmartRepository()
        fakeOrders = FakeOrderProvider()
        fakeCustomers = FakeCustomerProvider()
        fakeConnectivity = MutableStateFlow(true)
        fakeUsageStore = FakeSmartUsageStore()
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
        usageStore = fakeUsageStore,
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

    @Test
    fun SelectCustomer_with_single_open_order_auto_selects_it() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        runCurrent()
        assertEquals(testOrder, vm.state.value.order)
    }

    @Test
    fun SelectCustomer_with_multiple_orders_does_not_auto_select() = runTest {
        val orderA = testOrder
        val orderB = testOrder.copy(id = "o2", garmentLabel = "Adire shirt")
        fakeOrders.openOrdersFor("c1") { listOf(orderA, orderB) }
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        runCurrent()
        assertNull(vm.state.value.order)
        assertEquals(listOf(orderA, orderB), vm.state.value.orderOptions)
    }

    @Test
    fun successful_generate_publishes_remaining_quota_to_usage_store() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi!", remainingFreeQuota = 3)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        assertEquals(3, fakeUsageStore.remainingFreeQuota.value)
    }

    @Test
    fun initial_state_seeds_remaining_quota_from_usage_store() = runTest {
        fakeUsageStore.update(2)
        val vm = newVm()
        runCurrent()
        assertEquals(2, vm.state.value.remainingFreeQuota)
    }

    @Test
    fun rapid_customer_switch_does_not_let_stale_order_load_overwrite_state() = runTest {
        // Race: select(A) starts loading A's orders → select(B) starts loading
        // B's orders → A's slow load completes last. Without the cancel +
        // freshness guard, A's orders would land on state.orderOptions while
        // state.customer is already B, and the next Generate would fail with
        // invalid_input on the server.
        val customerA = testCustomer
        val customerB = CustomerSummary(id = "c-other", firstName = "Ada", whatsappNumber = "+2348099999999")
        val ordersA = listOf(testOrder)
        val ordersB = listOf(testOrder.copy(id = "o-b", customerId = "c-other"))

        val deferredA = CompletableDeferred<List<OrderSummary>>()
        val deferredB = CompletableDeferred<List<OrderSummary>>()
        val controllableOrders = object : OpenOrdersProvider {
            override suspend fun openOrdersFor(customerId: String): List<OrderSummary> =
                if (customerId == customerA.id) deferredA.await() else deferredB.await()
        }
        val vm = DraftMessageViewModel(
            repository = fakeRepo,
            orderProvider = controllableOrders,
            customerProvider = fakeCustomers,
            connectivity = fakeConnectivity,
            usageStore = fakeUsageStore,
        )

        vm.onAction(DraftMessageAction.SelectCustomer(customerA))
        runCurrent()
        vm.onAction(DraftMessageAction.SelectCustomer(customerB))
        runCurrent()
        // A's load was kicked off first but completes last — this is the
        // ordering that triggers the race in production.
        deferredB.complete(ordersB)
        runCurrent()
        deferredA.complete(ordersA)
        runCurrent()

        assertEquals(customerB, vm.state.value.customer)
        assertEquals(ordersB, vm.state.value.orderOptions)
    }

    @Test
    fun zero_cached_quota_flags_isOutOfFreeDrafts_without_hard_blocking_Generate() = runTest {
        // The cache is process-local and doesn't track month rollover, so a
        // hard client gate would trap a user whose server counter has reset
        // into the next month. The inline helper tells them what's likely
        // about to happen; the server is the source of truth on the tap.
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeUsageStore.update(0)
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        runCurrent()
        assertTrue(vm.state.value.canGenerate)
        assertTrue(vm.state.value.isOutOfFreeDrafts)
    }

    @Test
    fun late_draft_result_is_discarded_when_inputs_changed_mid_flight() = runTest {
        // Privacy guard for the still-Generating window: pickers stay
        // editable, so a draft for the previous customer must not land in
        // state after the user has already moved on.
        val otherCustomer = CustomerSummary(id = "c-other", firstName = "Ada", whatsappNumber = "+2348099999999")
        val otherOrder = testOrder.copy(id = "o-other", customerId = "c-other")
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeOrders.openOrdersFor("c-other") { listOf(otherOrder) }
        val deferred = CompletableDeferred<Result<DraftMessageResult, SmartError>>()
        val slowRepo = object : SmartRepository {
            override suspend fun draftMessage(request: DraftMessageRequest):
                Result<DraftMessageResult, SmartError> = deferred.await()
        }
        val vm = DraftMessageViewModel(
            repository = slowRepo,
            orderProvider = fakeOrders,
            customerProvider = fakeCustomers,
            connectivity = fakeConnectivity,
            usageStore = fakeUsageStore,
        )
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        assertIs<GenerationState.Generating>(vm.state.value.generationState)

        // User switches customer while the draft is still in flight.
        vm.onAction(DraftMessageAction.SelectCustomer(otherCustomer))
        runCurrent()

        // The original draft (for the original customer) returns now.
        deferred.complete(Result.Success(DraftMessageResult("Hi Folake!", 4)))
        runCurrent()

        // Must NOT land — Generate state cleared, no stale draft installed.
        assertEquals(GenerationState.Idle, vm.state.value.generationState)
    }

    @Test
    fun switching_customer_after_a_successful_draft_clears_the_stale_text() = runTest {
        // Privacy guard: without this clear, SendViaWhatsApp would dispatch
        // the previous customer's draft to the newly selected customer's
        // WhatsApp number.
        val otherCustomer = CustomerSummary(id = "c-other", firstName = "Ada", whatsappNumber = "+2348099999999")
        val otherOrder = testOrder.copy(id = "o-other", customerId = "c-other")
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeOrders.openOrdersFor("c-other") { listOf(otherOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        assertIs<GenerationState.Success>(vm.state.value.generationState)

        vm.onAction(DraftMessageAction.SelectCustomer(otherCustomer))
        runCurrent()
        assertEquals(GenerationState.Idle, vm.state.value.generationState)
    }

    @Test
    fun changing_intent_or_language_or_notes_after_success_clears_the_stale_draft() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", 4)))

        // Intent change
        val vm1 = newVm()
        vm1.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm1.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm1.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm1.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm1.onAction(DraftMessageAction.SelectIntent(DraftIntent.PickupReady))
        runCurrent()
        assertEquals(GenerationState.Idle, vm1.state.value.generationState)

        // Language change
        val vm2 = newVm()
        vm2.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm2.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm2.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm2.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm2.onAction(DraftMessageAction.ToggleLanguage(DraftLanguage.Pidgin))
        runCurrent()
        assertEquals(GenerationState.Idle, vm2.state.value.generationState)

        // Notes change
        val vm3 = newVm()
        vm3.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm3.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm3.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm3.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()
        vm3.onAction(DraftMessageAction.UpdateCustomNotes("be polite please"))
        runCurrent()
        assertEquals(GenerationState.Idle, vm3.state.value.generationState)
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

    private class FakeSmartUsageStore : SmartUsageStore {
        private val state = MutableStateFlow<Int?>(null)
        override val remainingFreeQuota: StateFlow<Int?> = state
        override fun update(remaining: Int?) {
            state.value = remaining
        }
    }
}

package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DraftMessageViewModelAnalyticsTest {

    private val testCustomer = CustomerSummary(id = "c1", firstName = "Folake", whatsappNumber = "+2348012345678")
    private val testOrder = OrderSummary(id = "o1", customerId = "c1", garmentLabel = "Adire boubou", balanceFormatted = "₦7,500", deadlineFormatted = "Fri")

    private lateinit var fakeRepo: FakeSmartRepository
    private lateinit var fakeOrders: FakeOrderProvider
    private lateinit var fakeCustomers: FakeCustomerProvider
    private lateinit var fakeConnectivity: MutableStateFlow<Boolean>
    private lateinit var fakeUsageStore: FakeSmartUsageStore
    private lateinit var fakeAnalytics: FakeAnalytics

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeSmartRepository()
        fakeOrders = FakeOrderProvider()
        fakeCustomers = FakeCustomerProvider()
        fakeConnectivity = MutableStateFlow(true)
        fakeUsageStore = FakeSmartUsageStore()
        fakeAnalytics = FakeAnalytics()
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
        analytics = fakeAnalytics,
    )

    @Test
    fun successful_generate_logs_ai_feature_used_event() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", remainingFreeQuota = 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()

        assertTrue(fakeAnalytics.events.contains(AnalyticsEvent.AiFeatureUsed("draft_message")))
    }

    @Test
    fun failed_generate_does_not_log_ai_feature_used_event() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.ServiceUnavailable))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        runCurrent()

        assertFalse(fakeAnalytics.events.any { it is AnalyticsEvent.AiFeatureUsed })
    }

    // --- Fakes (mirror DraftMessageViewModelTest) ---

    private class FakeSmartRepository : SmartRepository {
        private var canned: Result<DraftMessageResult, SmartError>? = null
        fun respondWith(result: Result<DraftMessageResult, SmartError>) { canned = result }
        override suspend fun draftMessage(request: DraftMessageRequest): Result<DraftMessageResult, SmartError> =
            canned ?: Result.Error(SmartError.Unknown)
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
        override suspend fun search(query: String): List<CustomerSummary> = emptyList()
    }

    private class FakeSmartUsageStore : SmartUsageStore {
        private val state = MutableStateFlow<Int?>(null)
        override val remainingFreeQuota: StateFlow<Int?> = state
        override fun update(remaining: Int?) { state.value = remaining }
    }
}

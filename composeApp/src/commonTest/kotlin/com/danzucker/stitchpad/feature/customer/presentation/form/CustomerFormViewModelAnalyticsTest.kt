package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
class CustomerFormViewModelAnalyticsTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var fakeAnalytics: FakeAnalytics

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        fakeAnalytics = FakeAnalytics()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(customerId: String? = null): CustomerFormViewModel {
        val args = if (customerId != null) mapOf("customerId" to customerId) else emptyMap()
        val vm = CustomerFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            customerRepository = customerRepository,
            authRepository = authRepository,
            emailValidator = FakePatternValidator(shouldMatch = true),
            entitlements = FakeEntitlementsProvider(),
            analytics = fakeAnalytics,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private class FakeEntitlementsProvider : EntitlementsProvider {
        private val entitlements = UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = 15,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = false,
        )
        private val _flow = MutableStateFlow(entitlements)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = entitlements
        override suspend fun awaitHydrated(): UserEntitlements = entitlements
    }

    @Test
    fun `successful create logs CustomerCreated`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel() // no customerId → create path
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertTrue(
            fakeAnalytics.events.contains(AnalyticsEvent.CustomerCreated),
            "Expected CustomerCreated in analytics events but got: ${fakeAnalytics.events}",
        )
    }

    @Test
    fun `successful edit does NOT log CustomerCreated`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = Customer(
            id = "customer-123",
            userId = "test-uid",
            name = "Old Name",
            phone = "+2340000000000",
        )
        val viewModel = createViewModel(customerId = "customer-123") // edit path
        viewModel.onAction(CustomerFormAction.OnNameChange("New Name"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertFalse(
            fakeAnalytics.events.contains(AnalyticsEvent.CustomerCreated),
            "Expected CustomerCreated NOT to be logged on edit but it was",
        )
    }
}

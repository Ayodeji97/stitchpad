package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerFormViewModelCelebrationTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var celebrationPrefs: FakeOnboardingPreferences
    private lateinit var celebrations: CelebrationController

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        celebrationPrefs = FakeOnboardingPreferences()
        celebrations = CelebrationController(
            preferences = celebrationPrefs,
            analytics = FakeAnalytics(),
            authUserIds = emptyFlow(),
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        )
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
            analytics = FakeAnalytics(),
            celebrations = celebrations,
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
    fun `plain create with measurements next OFF triggers FirstCustomer`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnToggleAddMeasurementsNext)
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertEquals(
            Milestone.FirstCustomer("Adaeze", addingMeasurementsNext = false),
            celebrations.current.value,
        )
    }

    @Test
    fun `default create with measurements next ON carries addingMeasurementsNext`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertEquals(
            Milestone.FirstCustomer("Adaeze", addingMeasurementsNext = true),
            celebrations.current.value,
        )
    }

    @Test
    fun `second create does NOT re-trigger`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm1 = createViewModel()
        vm1.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm1.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm1.onAction(CustomerFormAction.OnSaveClick)
        celebrations.current.value?.let { celebrations.dismiss(it) }

        val vm2 = createViewModel()
        vm2.onAction(CustomerFormAction.OnNameChange("Bola Ade"))
        vm2.onAction(CustomerFormAction.OnPhoneChange("+2348012345679"))
        vm2.onAction(CustomerFormAction.OnSaveClick)

        assertNull(celebrations.current.value)
    }

    @Test
    fun `upgrade path - create with pre-existing customers does NOT trigger`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            Customer(id = "existing-1", userId = "test-uid", name = "Old Client", phone = "+2340000000001"),
        )
        val vm = createViewModel()
        vm.onAction(CustomerFormAction.OnNameChange("Adaeze Obi"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertNull(celebrations.current.value)
    }

    @Test
    fun `edit does NOT trigger celebration`() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = Customer(
            id = "customer-123",
            userId = "test-uid",
            name = "Old Name",
            phone = "+2340000000000",
        )
        val vm = createViewModel(customerId = "customer-123")
        vm.onAction(CustomerFormAction.OnNameChange("New Name"))
        vm.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        vm.onAction(CustomerFormAction.OnSaveClick)

        assertNull(celebrations.current.value)
    }
}

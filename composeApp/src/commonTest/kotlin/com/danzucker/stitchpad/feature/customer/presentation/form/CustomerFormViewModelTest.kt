package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerFormViewModelTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var emailValidator: FakePatternValidator

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        emailValidator = FakePatternValidator(shouldMatch = true)
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
            emailValidator = emailValidator,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun TestScope.createViewModelWithValidator(
        customerId: String? = null,
        validator: FakePatternValidator,
    ): CustomerFormViewModel {
        val args = if (customerId != null) mapOf("customerId" to customerId) else emptyMap()
        val vm = CustomerFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            customerRepository = customerRepository,
            authRepository = authRepository,
            emailValidator = validator,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    // --- Initial state ---

    @Test
    fun initialState_createMode_allFieldsEmpty() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.state.value
        assertEquals("", state.name)
        assertEquals("", state.phone)
        assertEquals("", state.email)
        assertEquals("", state.address)
        assertEquals("", state.notes)
        assertEquals(DeliveryPreference.PICKUP, state.deliveryPreference)
        assertFalse(state.isEditMode)
        assertFalse(state.isLoading)
        assertNull(state.nameError)
        assertNull(state.phoneError)
        assertNull(state.emailError)
        assertNull(state.errorMessage)
    }

    @Test
    fun initialState_editMode_whenCustomerIdProvided() = runTest {
        val viewModel = createViewModel(customerId = "customer-123")
        assertTrue(viewModel.state.value.isEditMode)
    }

    // --- Field changes ---

    @Test
    fun onNameChange_updatesState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        assertEquals("Ade Fashions", viewModel.state.value.name)
    }

    @Test
    fun onNameChange_clearsNameError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)  // blank name → nameError set
        assertNotNull(viewModel.state.value.nameError)

        viewModel.onAction(CustomerFormAction.OnNameChange("Ade"))
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun onPhoneChange_updatesState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        assertEquals("+2348012345678", viewModel.state.value.phone)
    }

    @Test
    fun onPhoneChange_clearsPhoneError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("abc"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)  // invalid phone → phoneError set
        assertNotNull(viewModel.state.value.phoneError)

        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        assertNull(viewModel.state.value.phoneError)
    }

    @Test
    fun onEmailChange_updatesState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnEmailChange("ade@gmail.com"))
        assertEquals("ade@gmail.com", viewModel.state.value.email)
    }

    @Test
    fun onEmailChange_clearsEmailError() = runTest {
        val viewModel = createViewModelWithValidator(validator = FakePatternValidator(shouldMatch = false))
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnEmailChange("bad"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)  // invalid email → emailError set
        assertNotNull(viewModel.state.value.emailError)

        viewModel.onAction(CustomerFormAction.OnEmailChange("ade@gmail.com"))
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun onAddressChange_updatesState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnAddressChange("Lagos, Nigeria"))
        assertEquals("Lagos, Nigeria", viewModel.state.value.address)
    }

    @Test
    fun onNotesChange_updatesState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNotesChange("Prefers evening pickup"))
        assertEquals("Prefers evening pickup", viewModel.state.value.notes)
    }

    @Test
    fun onDeliveryPreferenceChange_updatesState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnDeliveryPreferenceChange(DeliveryPreference.DELIVERY))
        assertEquals(DeliveryPreference.DELIVERY, viewModel.state.value.deliveryPreference)
    }

    // --- Blur validation ---

    @Test
    fun onNameBlur_withBlankName_doesNotSetError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameBlur)
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun onPhoneBlur_withBlankPhone_doesNotSetError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneBlur)
        assertNull(viewModel.state.value.phoneError)
    }

    @Test
    fun onPhoneBlur_withInvalidPhone_setsPhoneError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneChange("abc123"))
        viewModel.onAction(CustomerFormAction.OnPhoneBlur)
        assertNotNull(viewModel.state.value.phoneError)
    }

    @Test
    fun onPhoneBlur_withNoDigits_setsPhoneError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+---"))
        viewModel.onAction(CustomerFormAction.OnPhoneBlur)
        assertNotNull(viewModel.state.value.phoneError)
    }

    @Test
    fun onPhoneBlur_withValidPhone_doesNotSetError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+234 801 234 5678"))
        viewModel.onAction(CustomerFormAction.OnPhoneBlur)
        assertNull(viewModel.state.value.phoneError)
    }

    @Test
    fun onEmailBlur_withBlankEmail_doesNotSetError() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnEmailBlur)
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun onEmailBlur_withInvalidEmail_setsEmailError() = runTest {
        val viewModel = createViewModelWithValidator(validator = FakePatternValidator(shouldMatch = false))
        viewModel.onAction(CustomerFormAction.OnEmailChange("not-an-email"))
        viewModel.onAction(CustomerFormAction.OnEmailBlur)
        assertNotNull(viewModel.state.value.emailError)
    }

    @Test
    fun onEmailBlur_withValidEmail_doesNotSetError() = runTest {
        val viewModel = createViewModel()  // validator returns true
        viewModel.onAction(CustomerFormAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(CustomerFormAction.OnEmailBlur)
        assertNull(viewModel.state.value.emailError)
    }

    // --- Save validation ---

    @Test
    fun save_withBlankName_setsNameError_andDoesNotCallRepository() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertNotNull(viewModel.state.value.nameError)
        assertNull(customerRepository.lastCreatedCustomer)
    }

    @Test
    fun save_withBlankPhone_setsPhoneError_andDoesNotCallRepository() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)  // blank phone is invalid

        assertNotNull(viewModel.state.value.phoneError)
        assertNull(customerRepository.lastCreatedCustomer)
    }

    @Test
    fun save_withInvalidPhone_setsPhoneError_andDoesNotCallRepository() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("abc"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertNotNull(viewModel.state.value.phoneError)
        assertNull(customerRepository.lastCreatedCustomer)
    }

    @Test
    fun save_withInvalidEmail_setsEmailError_andDoesNotCallRepository() = runTest {
        val viewModel = createViewModelWithValidator(validator = FakePatternValidator(shouldMatch = false))
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnEmailChange("bad-email"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertNotNull(viewModel.state.value.emailError)
        assertNull(customerRepository.lastCreatedCustomer)
    }

    @Test
    fun save_withBlankEmail_isValidAndDoesNotSetEmailError() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        // email left blank — should be treated as optional
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertNull(viewModel.state.value.emailError)
        assertNotNull(customerRepository.lastCreatedCustomer)
        assertNull(customerRepository.lastCreatedCustomer?.email)  // blank → stored as null
    }

    // --- Save: create mode ---

    @Test
    fun save_createMode_callsCreateCustomer_andNavigatesBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        val created = customerRepository.lastCreatedCustomer
        assertNotNull(created)
        assertEquals("Ade Fashions", created.name)
        assertEquals("+2348012345678", created.phone)
        assertEquals("ade@gmail.com", created.email)
        assertNull(customerRepository.lastUpdatedCustomer)

        assertIs<CustomerFormEvent.NavigateBack>(viewModel.events.first())
    }

    // --- Save: edit mode ---

    @Test
    fun save_editMode_callsUpdateCustomer_andNavigatesBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = Customer(
            id = "customer-123",
            userId = "test-uid",
            name = "Old Name",
            phone = "+2340000000000",
        )
        val viewModel = createViewModel(customerId = "customer-123")
        viewModel.onAction(CustomerFormAction.OnNameChange("New Name"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        val updated = customerRepository.lastUpdatedCustomer
        assertNotNull(updated)
        assertEquals("New Name", updated.name)
        assertNull(customerRepository.lastCreatedCustomer)

        assertIs<CustomerFormEvent.NavigateBack>(viewModel.events.first())
    }

    // --- Save: error paths ---

    @Test
    fun save_withRepositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.UNKNOWN
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertNotNull(viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun save_withNoAuthUser_doesNotCallRepository() = runTest {
        // authRepository has no current user (no signUpWithEmail called)
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade Fashions"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)

        assertNull(customerRepository.lastCreatedCustomer)
        assertFalse(viewModel.state.value.isLoading)
    }

    // --- Load customer (edit mode) ---

    @Test
    fun loadCustomer_populatesStateFromRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.storedCustomer = Customer(
            id = "customer-123",
            userId = "test-uid",
            name = "Ade Fashions",
            phone = "+2348012345678",
            email = "ade@gmail.com",
            address = "Lagos, Nigeria",
            deliveryPreference = DeliveryPreference.DELIVERY,
            notes = "Prefers evening pickup",
        )
        val viewModel = createViewModel(customerId = "customer-123")

        val state = viewModel.state.value
        assertEquals("Ade Fashions", state.name)
        assertEquals("+2348012345678", state.phone)
        assertEquals("ade@gmail.com", state.email)
        assertEquals("Lagos, Nigeria", state.address)
        assertEquals(DeliveryPreference.DELIVERY, state.deliveryPreference)
        assertEquals("Prefers evening pickup", state.notes)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadCustomer_onRepositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.UNKNOWN
        val viewModel = createViewModel(customerId = "customer-123")

        assertNotNull(viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun loadCustomer_withNoAuthUser_doesNotLoadAndResetsLoading() = runTest {
        // No user — auth check fails early in loadCustomer
        val viewModel = createViewModel(customerId = "customer-123")

        assertEquals("", viewModel.state.value.name)
        assertFalse(viewModel.state.value.isLoading)
    }

    // --- Navigation ---

    @Test
    fun onNavigateBack_emitsNavigateBackEvent() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNavigateBack)

        assertIs<CustomerFormEvent.NavigateBack>(viewModel.events.first())
    }

    // --- Error dismiss ---

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.UNKNOWN
        val viewModel = createViewModel()
        viewModel.onAction(CustomerFormAction.OnNameChange("Ade"))
        viewModel.onAction(CustomerFormAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(CustomerFormAction.OnSaveClick)
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.onAction(CustomerFormAction.OnErrorDismiss)
        assertNull(viewModel.state.value.errorMessage)
    }
}

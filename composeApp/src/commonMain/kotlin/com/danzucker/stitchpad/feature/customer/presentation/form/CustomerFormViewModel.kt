package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_invalid_email
import stitchpad.composeapp.generated.resources.error_name_required
import stitchpad.composeapp.generated.resources.error_phone_invalid
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CustomerFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator,
    private val entitlements: EntitlementsProvider,
    private val analytics: Analytics,
    private val celebrations: CelebrationController,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(CustomerFormState(isEditMode = customerId != null))

    private val _events = Channel<CustomerFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId != null) loadCustomer(customerId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CustomerFormState(isEditMode = customerId != null)
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: CustomerFormAction) {
        when (action) {
            is CustomerFormAction.OnNameChange ->
                _state.update { it.copy(name = action.name, nameError = null) }
            is CustomerFormAction.OnPhoneChange ->
                _state.update { it.copy(phone = action.phone, phoneError = null) }
            is CustomerFormAction.OnEmailChange ->
                _state.update { it.copy(email = action.email, emailError = null) }
            is CustomerFormAction.OnAddressChange ->
                _state.update { it.copy(address = action.address) }
            CustomerFormAction.OnNameBlur ->
                if (_state.value.name.isNotBlank()) validateName()
            CustomerFormAction.OnPhoneBlur ->
                if (_state.value.phone.isNotBlank()) validatePhone()
            CustomerFormAction.OnEmailBlur ->
                if (_state.value.email.isNotBlank()) validateEmail()
            CustomerFormAction.OnSaveClick -> save()
            CustomerFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(CustomerFormEvent.NavigateBack) }
            }
            CustomerFormAction.OnToggleAddMeasurementsNext ->
                _state.update { it.copy(addMeasurementsNext = !it.addMeasurementsNext) }
            CustomerFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun loadCustomer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            when (val result = customerRepository.getCustomer(userId, id)) {
                is Result.Success -> {
                    val c = result.data
                    _state.update {
                        it.copy(
                            name = c.name,
                            phone = c.phone,
                            email = c.email ?: "",
                            address = c.address ?: "",
                            createdAt = c.createdAt,
                            isLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toCustomerUiText())
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    @OptIn(ExperimentalUuidApi::class)
    private fun save() {
        // Re-entrancy guard: SaveButton's enabled=!isLoading propagates through
        // StateFlow→Compose only after one recomposition (~1 frame on low-end
        // Android), so a fast double-tap could otherwise queue two coroutines,
        // each minting a distinct UUID and writing a duplicate customer doc.
        if (_state.value.isLoading) return
        val nameValid = validateName()
        val phoneValid = validatePhone()
        val emailValid = validateEmail()
        if (!nameValid || !phoneValid || !emailValid) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            // Snapshot BEFORE the create: createCustomer enqueues its Firestore
            // write via OfflineWriteDispatcher and returns immediately, so a
            // post-create read races the background set(). Reading the (cached)
            // snapshot first is deterministic with respect to our own write.
            val isFirstCustomerCandidate = customerId == null && isCustomerListKnownEmpty(userId)
            val s = _state.value
            val newId = customerId ?: Uuid.random().toString()
            val customer = Customer(
                id = newId,
                userId = userId,
                name = s.name.trim(),
                phone = s.phone.trim(),
                email = s.email.trim().ifBlank { null },
                address = s.address.trim().ifBlank { null },
                createdAt = s.createdAt
            )
            val result = if (customerId != null) {
                customerRepository.updateCustomer(userId, customer)
            } else {
                customerRepository.createCustomer(userId, customer)
            }
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> {
                    if (customerId == null) {
                        analytics.logEvent(AnalyticsEvent.CustomerCreated)
                        if (isFirstCustomerCandidate) {
                            celebrations.trigger(
                                userId = userId,
                                milestone = Milestone.FirstCustomer(customer.name.substringBefore(' ')),
                            )
                        }
                    }
                    _events.send(postSaveEvent(s, newId))
                }
                is Result.Error -> {
                    if (result.error == DataError.Network.CAP_REACHED) {
                        // Cap-reached is the only error with a dedicated upgrade-pitch
                        // bottom sheet; everything else routes through the generic
                        // snackbar via errorMessage. activeCount == cap here by
                        // definition (we just failed because we're AT the cap).
                        val cap = entitlements.current().customerCap
                        _events.send(
                            CustomerFormEvent.ShowCapReachedSheet(
                                activeCount = cap,
                                customerCap = cap,
                            )
                        )
                    } else {
                        _state.update {
                            it.copy(errorMessage = result.error.toCustomerUiText())
                        }
                    }
                }
            }
        }
    }

    /**
     * The one-shot flag alone can't distinguish a genuinely first customer
     * from an existing user's next create after updating to this release
     * (the flag never existed before this version), so also require the list
     * to be empty going into the create. Read errors return false — better
     * to skip a celebration than to congratulate a veteran on their "first".
     */
    private suspend fun isCustomerListKnownEmpty(userId: String): Boolean {
        val customers = customerRepository.observeCustomers(userId).first()
        return customers is Result.Success && customers.data.isEmpty()
    }

    /**
     * Navigation event for a successful save. Edits pop back to the launch
     * point; a fresh create lands on the Customers list (or chains into the
     * measurement form when the tailor opted to add measurements next) rather
     * than returning to wherever the form was opened, e.g. the dashboard FAB.
     */
    private fun postSaveEvent(state: CustomerFormState, newId: String): CustomerFormEvent = when {
        state.isEditMode -> CustomerFormEvent.NavigateBack
        state.addMeasurementsNext -> CustomerFormEvent.NavigateToNewCustomerMeasurement(newId)
        else -> CustomerFormEvent.NavigateToCustomerList
    }

    private fun validateName(): Boolean {
        if (_state.value.name.isBlank()) {
            _state.update {
                it.copy(nameError = UiText.StringResourceText(Res.string.error_name_required))
            }
            return false
        }
        return true
    }

    private fun isValidPhoneChar(c: Char) = c.isDigit() || c == '+' || c == ' ' || c == '-'

    private fun validatePhone(): Boolean {
        val phone = _state.value.phone.trim()
        val isValid = phone.isNotBlank() && phone.any { it.isDigit() } && phone.all { isValidPhoneChar(it) }
        if (!isValid) {
            _state.update {
                it.copy(phoneError = UiText.StringResourceText(Res.string.error_phone_invalid))
            }
        }
        return isValid
    }

    private fun validateEmail(): Boolean {
        val email = _state.value.email.trim()
        val isValid = email.isBlank() || emailValidator.matches(email)
        if (!isValid) {
            _state.update {
                it.copy(emailError = UiText.StringResourceText(Res.string.error_invalid_email))
            }
        }
        return isValid
    }
}

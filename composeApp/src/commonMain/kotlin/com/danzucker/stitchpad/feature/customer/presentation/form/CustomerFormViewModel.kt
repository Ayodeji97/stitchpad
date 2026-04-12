package com.danzucker.stitchpad.feature.customer.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.PatternValidator
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import com.danzucker.stitchpad.navigation.CustomerFormRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_invalid_email
import stitchpad.composeapp.generated.resources.error_name_required
import stitchpad.composeapp.generated.resources.error_phone_invalid

class CustomerFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val emailValidator: PatternValidator
) : ViewModel() {

    private val route: CustomerFormRoute = savedStateHandle.toRoute()
    private val customerId: String? = route.customerId

    private val _state = MutableStateFlow(CustomerFormState(isEditMode = customerId != null))
    val state = _state.asStateFlow()

    private val _events = Channel<CustomerFormEvent>()
    val events = _events.receiveAsFlow()

    init {
        if (customerId != null) loadCustomer(customerId)
    }

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
            is CustomerFormAction.OnDeliveryPreferenceChange ->
                _state.update { it.copy(deliveryPreference = action.preference) }
            is CustomerFormAction.OnNotesChange ->
                _state.update { it.copy(notes = action.notes) }
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
                            deliveryPreference = c.deliveryPreference,
                            notes = c.notes ?: "",
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

    private fun save() {
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
            val s = _state.value
            val customer = Customer(
                id = customerId ?: "",
                userId = userId,
                name = s.name.trim(),
                phone = s.phone.trim(),
                email = s.email.trim().ifBlank { null },
                address = s.address.trim().ifBlank { null },
                deliveryPreference = s.deliveryPreference,
                notes = s.notes.trim().ifBlank { null }
            )
            val result = if (customerId != null) {
                customerRepository.updateCustomer(userId, customer)
            } else {
                customerRepository.createCustomer(userId, customer)
            }
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> _events.send(CustomerFormEvent.NavigateBack)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toCustomerUiText())
                }
            }
        }
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
        val isValid = phone.isNotBlank() && phone.all { isValidPhoneChar(it) }
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

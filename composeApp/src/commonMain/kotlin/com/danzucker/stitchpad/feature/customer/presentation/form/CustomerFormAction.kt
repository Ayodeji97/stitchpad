package com.danzucker.stitchpad.feature.customer.presentation.form

import com.danzucker.stitchpad.core.domain.model.Country

sealed interface CustomerFormAction {
    data object OnCountryPickerOpen : CustomerFormAction
    data object OnCountryPickerDismiss : CustomerFormAction
    data class OnCountrySelected(val country: Country) : CustomerFormAction
    data class OnCountrySearchChange(val query: String) : CustomerFormAction
    data class OnNameChange(val name: String) : CustomerFormAction
    data class OnPhoneChange(val phone: String) : CustomerFormAction
    data class OnEmailChange(val email: String) : CustomerFormAction
    data class OnAddressChange(val address: String) : CustomerFormAction
    data object OnNameBlur : CustomerFormAction
    data object OnPhoneBlur : CustomerFormAction
    data object OnEmailBlur : CustomerFormAction
    data object OnSaveClick : CustomerFormAction
    data object OnNavigateBack : CustomerFormAction
    data object OnErrorDismiss : CustomerFormAction
    data object OnToggleAddMeasurementsNext : CustomerFormAction
}

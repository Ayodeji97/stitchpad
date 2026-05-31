package com.danzucker.stitchpad.feature.customer.presentation.form

sealed interface CustomerFormAction {
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

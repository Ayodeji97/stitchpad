package com.danzucker.stitchpad.feature.customer.presentation.form

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerFormState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val createdAt: Long = 0L,
    val nameError: UiText? = null,
    val phoneError: UiText? = null,
    val emailError: UiText? = null,
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null,
    val addMeasurementsNext: Boolean = true,
    val isCountryPickerOpen: Boolean = false,
    val countrySearchQuery: String = "",
    val isPhoneCountrySelected: Boolean = false,
    /** Defaults to the tailor country; selecting a customer prefix never updates the workshop. */
    val country: Country = Country.DEFAULT,
)

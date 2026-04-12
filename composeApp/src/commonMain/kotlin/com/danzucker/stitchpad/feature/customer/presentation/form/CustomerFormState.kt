package com.danzucker.stitchpad.feature.customer.presentation.form

import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerFormState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val deliveryPreference: DeliveryPreference = DeliveryPreference.PICKUP,
    val notes: String = "",
    val createdAt: Long = 0L,
    val nameError: UiText? = null,
    val phoneError: UiText? = null,
    val emailError: UiText? = null,
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null
)

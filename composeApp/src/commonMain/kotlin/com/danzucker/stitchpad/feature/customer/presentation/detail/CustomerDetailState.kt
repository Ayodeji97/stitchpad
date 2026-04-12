package com.danzucker.stitchpad.feature.customer.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerDetailState(
    val customer: Customer? = null,
    val measurements: List<Measurement> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val measurementToDelete: Measurement? = null,
    val errorMessage: UiText? = null
)

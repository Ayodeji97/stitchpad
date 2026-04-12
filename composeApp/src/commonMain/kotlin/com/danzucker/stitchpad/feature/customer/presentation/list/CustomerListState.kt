package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerListState(
    val customers: List<Customer> = emptyList(),
    val searchQuery: String = "",
    val deliveryFilter: DeliveryPreference? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val customerToDelete: Customer? = null,
    val errorMessage: UiText? = null
)

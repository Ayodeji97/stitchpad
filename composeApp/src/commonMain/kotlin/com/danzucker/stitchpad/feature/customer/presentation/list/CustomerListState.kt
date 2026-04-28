package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerListState(
    val customers: List<Customer> = emptyList(),
    val searchQuery: String = "",
    val deliveryFilter: DeliveryPreference? = null,
    val isLoading: Boolean = true,
    /**
     * True once the orders flow has emitted at least once, so [customerToDeleteActiveOrderCount]
     * and the in-VM `activeOrderCountByCustomerId` cache are trustworthy. Until then,
     * [CustomerListAction.OnConfirmDelete] is treated as a no-op with a snackbar to avoid
     * deleting customers whose active-order count we haven't observed yet.
     */
    val ordersLoaded: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val customerToDelete: Customer? = null,
    /** Active (non-delivered) order count for [customerToDelete]. > 0 blocks deletion. */
    val customerToDeleteActiveOrderCount: Int = 0,
    val errorMessage: UiText? = null
)

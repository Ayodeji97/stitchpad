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
     * True once the orders flow has emitted [com.danzucker.stitchpad.core.domain.error.Result.Success]
     * at least once, so [customerToDeleteActiveOrderCount] and the in-VM
     * `activeOrderCountByCustomerId` cache are trustworthy. Until then,
     * [CustomerListAction.OnConfirmDelete] is treated as a no-op with a snackbar to avoid
     * orphaning a customer's non-delivered orders by deleting on a stale (empty) count.
     */
    val ordersLoaded: Boolean = false,
    /**
     * True when the orders flow has emitted [com.danzucker.stitchpad.core.domain.error.Result.Error]
     * before any successful snapshot — typically offline / permission / transient failure. Distinct
     * from [ordersLoaded] so the delete dialog can surface a specific "couldn't load order data"
     * snackbar rather than the generic "still loading" one. Cleared on the next successful emission.
     */
    val ordersLoadFailed: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val customerToDelete: Customer? = null,
    /** Active (non-delivered) order count for [customerToDelete]. > 0 blocks deletion. */
    val customerToDeleteActiveOrderCount: Int = 0,
    val errorMessage: UiText? = null
)

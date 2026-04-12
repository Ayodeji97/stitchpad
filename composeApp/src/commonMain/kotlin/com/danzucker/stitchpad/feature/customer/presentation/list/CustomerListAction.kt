package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference

sealed interface CustomerListAction {
    data class OnSearchQueryChange(val query: String) : CustomerListAction
    data class OnDeliveryFilterChange(val filter: DeliveryPreference?) : CustomerListAction
    data class OnCustomerClick(val customer: Customer) : CustomerListAction
    data class OnDeleteCustomerClick(val customer: Customer) : CustomerListAction
    data object OnAddCustomerClick : CustomerListAction
    data object OnConfirmDelete : CustomerListAction
    data object OnDismissDeleteDialog : CustomerListAction
    data object OnErrorDismiss : CustomerListAction
}

package com.danzucker.stitchpad.feature.customer.presentation.list

import com.danzucker.stitchpad.core.domain.model.Customer

sealed interface CustomerListAction {
    data class OnSearchQueryChange(val query: String) : CustomerListAction
    data class OnCustomerClick(val customer: Customer) : CustomerListAction
    data class OnDeleteCustomerClick(val customer: Customer) : CustomerListAction
    data object OnAddCustomerClick : CustomerListAction
    data object OnConfirmDelete : CustomerListAction
    data object OnDismissDeleteDialog : CustomerListAction
    data object OnErrorDismiss : CustomerListAction
    data class OpenSwapSheetFor(val lockedCustomerId: String) : CustomerListAction
    data object DismissSwapSheet : CustomerListAction
    data class ConfirmSwap(val lockedCustomerId: String, val activeCustomerIdToDemote: String) : CustomerListAction
}

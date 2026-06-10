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

    /** Tapped the ⋮ overflow icon on an active customer row. */
    data class OnOverflowClick(val customer: Customer) : CustomerListAction

    /** Sheet dismissed by swipe-down, backdrop tap, or system back. */
    data object DismissActionsSheet : CustomerListAction

    /** Tapped the sheet header (avatar + name + phone + chevron). Routes to detail. */
    data class OnViewCustomerFromSheet(val customerId: String) : CustomerListAction

    /** Tapped "Edit" in the actions sheet. Routes directly to the customer form (edit mode). */
    data class OnEditCustomerFromRow(val customerId: String) : CustomerListAction

    /** Tapped "New measurement" in the actions sheet. Routes directly to the measurement form. */
    data class OnAddMeasurementFromRow(val customerId: String) : CustomerListAction

    /** Tapped "New order" in the actions sheet. Routes to the order form with customer pre-selected. */
    data class OnNewOrderFromRow(val customerId: String) : CustomerListAction

    /** Tapped "Message on WhatsApp" in the actions sheet (PTSP-32). */
    data class OnMessageWhatsApp(val customer: Customer) : CustomerListAction
}

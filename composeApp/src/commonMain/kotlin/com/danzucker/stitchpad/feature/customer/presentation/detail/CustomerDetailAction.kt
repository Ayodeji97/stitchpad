package com.danzucker.stitchpad.feature.customer.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Measurement

sealed interface CustomerDetailAction {
    data object OnEditCustomerClick : CustomerDetailAction
    data object OnAddMeasurementClick : CustomerDetailAction
    data class OnMeasurementClick(val measurement: Measurement) : CustomerDetailAction
    data class OnDeleteMeasurementClick(val measurement: Measurement) : CustomerDetailAction
    data object OnConfirmDelete : CustomerDetailAction
    data object OnDismissDeleteDialog : CustomerDetailAction

    // PTSP-31: top-bar overflow menu + delete-customer flow.
    data object OnOverflowClick : CustomerDetailAction
    data object OnDismissOverflow : CustomerDetailAction
    data object OnDeleteCustomerClick : CustomerDetailAction
    data object OnConfirmDeleteCustomer : CustomerDetailAction
    data object OnDismissDeleteCustomerDialog : CustomerDetailAction
    data object OnViewStylesClick : CustomerDetailAction
    data object OnUpgradeClick : CustomerDetailAction
    data object OnNavigateBack : CustomerDetailAction
    data object OnErrorDismiss : CustomerDetailAction
}

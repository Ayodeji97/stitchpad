package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType

sealed interface OrderDetailAction {
    // Navigation
    data object OnBackClick : OrderDetailAction
    data object OnEditClick : OrderDetailAction
    data object OnCustomerClick : OrderDetailAction

    // Top-bar overflow
    data object OnOverflowMenuToggle : OrderDetailAction
    data object OnDuplicateClick : OrderDetailAction

    // Delete
    data object OnDeleteClick : OrderDetailAction
    data object OnConfirmDelete : OrderDetailAction
    data object OnDismissDeleteDialog : OrderDetailAction

    // Archive
    data object OnArchiveClick : OrderDetailAction
    data object OnConfirmArchive : OrderDetailAction
    data object OnDismissArchiveDialog : OrderDetailAction

    // Status sheet
    data object OnUpdateStatusClick : OrderDetailAction
    data class OnSelectStatusTransition(val transition: StatusTransition) : OrderDetailAction
    data object OnDismissStatusSheet : OrderDetailAction

    data object OnBalanceWarningRecordPayment : OrderDetailAction
    data object OnBalanceWarningProceed : OrderDetailAction
    data object OnBalanceWarningDismiss : OrderDetailAction

    // Sharing
    data object OnShareClick : OrderDetailAction
    data object OnShareAsImageClick : OrderDetailAction
    data object OnShareAsPdfClick : OrderDetailAction
    data object OnDismissShareSheet : OrderDetailAction

    // Record payment
    data object OnRecordPaymentClick : OrderDetailAction
    data class OnPaymentAmountChange(val digits: String) : OrderDetailAction
    data class OnPaymentMethodSelect(val method: PaymentMethod) : OrderDetailAction
    data class OnPaymentTypeSelect(val type: PaymentType) : OrderDetailAction
    data object OnMarkPaidInFull : OrderDetailAction
    data object OnConfirmRecordPayment : OrderDetailAction
    data object OnDismissRecordPayment : OrderDetailAction
    data object OnPaymentHistoryToggle : OrderDetailAction

    // Notes
    data object OnNotesEditClick : OrderDetailAction
    data class OnNotesDraftChange(val text: String) : OrderDetailAction
    data object OnNotesSaveClick : OrderDetailAction
    data object OnNotesCancelClick : OrderDetailAction

    // Customer reach-out
    data object OnWhatsAppClick : OrderDetailAction
    data object OnCallClick : OrderDetailAction
    data object OnSendReminderClick : OrderDetailAction
    data object OnAddStyleClick : OrderDetailAction
    data object OnAddPhoneClick : OrderDetailAction

    // Styles
    data class OnSelectStyle(val styleId: String) : OrderDetailAction
    data object OnCreateNewStyleClick : OrderDetailAction
    data object OnDismissStylePickerSheet : OrderDetailAction

    // Measurements
    data object OnLinkMeasurementsClick : OrderDetailAction
    data class OnSelectMeasurement(val measurementId: String) : OrderDetailAction
    data object OnCreateNewMeasurementClick : OrderDetailAction
    data object OnDismissMeasurementPickerSheet : OrderDetailAction

    // Deadline
    data object OnSetDeadlineClick : OrderDetailAction
    data class OnDeadlineSelected(val epochMillis: Long) : OrderDetailAction
    data object OnDismissDatePickerDialog : OrderDetailAction

    // Misc
    data object OnErrorDismiss : OrderDetailAction
}

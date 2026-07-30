package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.sharing.ReceiptDocumentType
import com.danzucker.stitchpad.feature.order.presentation.form.StylePickerSource
import com.danzucker.stitchpad.feature.style.domain.StylePickerFolder

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

    /** Manually pick Invoice vs Deposit Receipt when both are meaningful (partial-paid orders). */
    data class OnDocumentTypeChoice(val choice: ReceiptDocumentType) : OrderDetailAction

    /** From the PaymentRecorded snackbar action — open share sheet for the freshly-paid order. */
    data object OnShareReceiptFromSnackbar : OrderDetailAction

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

    // Costs
    data object OnEditCostsClick : OrderDetailAction
    data class OnCostDraftChange(val category: CostCategory, val digits: String) : OrderDetailAction
    data object OnSaveCosts : OrderDetailAction
    data object OnDismissCostsEditor : OrderDetailAction
    data object OnCostsExpandToggle : OrderDetailAction

    // Customer reach-out
    data object OnWhatsAppClick : OrderDetailAction
    data object OnCallClick : OrderDetailAction
    data object OnSendReminderClick : OrderDetailAction
    data class OnAddStyleClick(val itemId: String) : OrderDetailAction
    data class OnAddStylePhoto(val itemId: String, val photoBytes: ByteArray) : OrderDetailAction
    data class OnRemoveStyleImage(val itemId: String, val index: Int) : OrderDetailAction
    data object OnAddFabricClick : OrderDetailAction
    data class OnAddFabricPhoto(val itemId: String, val photoBytes: ByteArray) : OrderDetailAction
    data class OnRemoveFabricImage(val itemId: String, val index: Int) : OrderDetailAction
    data object OnAddFabricNameClick : OrderDetailAction
    data class OnFabricNameDraftChange(val text: String) : OrderDetailAction
    data object OnSaveFabricName : OrderDetailAction
    data object OnDismissFabricNameDialog : OrderDetailAction
    data object OnAddPhoneClick : OrderDetailAction

    // Styles
    data class OnStylePickerSourceChange(val source: StylePickerSource) : OrderDetailAction
    data class OnPickerFolderOpen(val folder: StylePickerFolder) : OrderDetailAction
    data object OnPickerFolderBack : OrderDetailAction
    data class OnItemTogglePendingStyle(val styleId: String) : OrderDetailAction
    data class OnItemCommitPendingStyles(val itemId: String) : OrderDetailAction
    data class OnCreateNewStyleClick(val itemId: String) : OrderDetailAction
    data object OnDismissStylePickerSheet : OrderDetailAction

    // Measurements
    data object OnLinkMeasurementsClick : OrderDetailAction
    data class OnSelectMeasurement(val measurementId: String) : OrderDetailAction
    data object OnCreateNewMeasurementClick : OrderDetailAction
    data object OnDismissMeasurementPickerSheet : OrderDetailAction
    data object OnViewMeasurementClick : OrderDetailAction
    data object OnDismissMeasurementDetailSheet : OrderDetailAction
    data object OnViewFullMeasurementClick : OrderDetailAction

    // Deadline
    data object OnSetDeadlineClick : OrderDetailAction
    data class OnDeadlineSelected(val epochMillis: Long) : OrderDetailAction
    data object OnDismissDatePickerDialog : OrderDetailAction

    // Misc
    data object OnErrorDismiss : OrderDetailAction
}

/**
 * Actions an active staff member must never be able to trigger (Slice 6c): anything
 * touching money — prices, payments, costs, receipts/sharing — or customer contact —
 * call, WhatsApp, payment reminder, add-phone. [OrderDetailViewModel.onAction] early-returns
 * on these when the session is active-staff, so no state mutates and no event fires even
 * if a stray tap reaches the VM. Status advance, notes, styles, measurements, deadline,
 * delete/archive and plain sheet-dismisses are intentionally NOT restricted.
 */
@Suppress("CyclomaticComplexMethod")
internal fun OrderDetailAction.isStaffRestricted(): Boolean = when (this) {
    // Share / receipt
    OrderDetailAction.OnShareClick,
    OrderDetailAction.OnShareAsImageClick,
    OrderDetailAction.OnShareAsPdfClick,
    is OrderDetailAction.OnDocumentTypeChoice,
    OrderDetailAction.OnShareReceiptFromSnackbar,
    // Payment
    OrderDetailAction.OnRecordPaymentClick,
    is OrderDetailAction.OnPaymentAmountChange,
    is OrderDetailAction.OnPaymentMethodSelect,
    is OrderDetailAction.OnPaymentTypeSelect,
    OrderDetailAction.OnMarkPaidInFull,
    OrderDetailAction.OnConfirmRecordPayment,
    OrderDetailAction.OnPaymentHistoryToggle,
    OrderDetailAction.OnBalanceWarningRecordPayment,
    OrderDetailAction.OnBalanceWarningProceed,
    OrderDetailAction.OnBalanceWarningDismiss,
    // Costs
    OrderDetailAction.OnEditCostsClick,
    is OrderDetailAction.OnCostDraftChange,
    OrderDetailAction.OnSaveCosts,
    OrderDetailAction.OnDismissCostsEditor,
    OrderDetailAction.OnCostsExpandToggle,
    // Customer contact
    OrderDetailAction.OnWhatsAppClick,
    OrderDetailAction.OnCallClick,
    OrderDetailAction.OnSendReminderClick,
    OrderDetailAction.OnAddPhoneClick,
    // Create / edit / destroy — staff can't create or edit orders; edit and
    // duplicate open the order form (which shows and edits money).
    OrderDetailAction.OnEditClick,
    OrderDetailAction.OnDuplicateClick,
    OrderDetailAction.OnArchiveClick,
    OrderDetailAction.OnDeleteClick,
    -> true

    else -> false
}

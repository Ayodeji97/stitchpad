package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.ReceiptDocumentType
import com.danzucker.stitchpad.feature.order.presentation.form.StylePickerSource
import com.danzucker.stitchpad.feature.style.domain.StylePickerFolder

data class OrderDetailState(
    val order: Order? = null,
    val user: User? = null,
    val customer: Customer? = null,
    val measurement: Measurement? = null,
    val styles: Map<String, Style> = emptyMap(),
    val isLoading: Boolean = true,

    val showMeasurementPickerSheet: Boolean = false,
    val availableMeasurements: List<Measurement> = emptyList(),
    /** Read-only quick-view of the linked measurement (opened by tapping the card). */
    val showMeasurementDetailSheet: Boolean = false,
    /** Custom-field id -> label, so custom measurement values can render by name. */
    val customFieldLabels: Map<String, String> = emptyMap(),

    val showStylePickerSheet: Boolean = false,
    val stylePickerItemId: String? = null,
    val availableStyles: List<Style> = emptyList(),
    /** Folders with their styles for the order customer's closet (saved-style picker). */
    val closetFolders: List<StylePickerFolder> = emptyList(),
    /** Folders with their styles for the shared Inspiration library. */
    val inspirationFolders: List<StylePickerFolder> = emptyList(),
    /** Which source is active in the picker's Closet/Inspiration toggle. */
    val stylePickerSource: StylePickerSource = StylePickerSource.CLOSET,
    /**
     * Key ([StylePickerFolder.key]) of the folder drilled into in the picker.
     * Null = show the folder grid (or the default-folder styles when there are no
     * named folders). A key (not a snapshot) so the drilled-in view resolves the
     * LIVE folder from the current list each render.
     */
    val pickerOpenFolderKey: String? = null,
    /** In-progress (uncommitted) saved-style picks for the open picker, in tap order. */
    val stylePickerPendingIds: List<String> = emptyList(),
    val showFabricSourceSheet: Boolean = false,
    val fabricSourceItemIndex: Int? = null,
    val isUploadingFabric: Boolean = false,
    val fabricNameItemIndex: Int? = null,

    // Dialogs / sheets
    val showDeleteDialog: Boolean = false,
    val showStatusSheet: Boolean = false,
    val showBalanceWarningDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    /**
     * Optional override for the document type when sharing. Only meaningful
     * when the order has a partial-paid state (both Invoice and Deposit
     * Receipt views are valid). `null` (the default) lets [ReceiptFormatter]
     * pick the natural one from `order.payments` + `balanceRemaining`. Reset
     * to `null` whenever the sheet closes.
     */
    val documentTypeChoice: ReceiptDocumentType? = null,
    val showRecordPaymentDialog: Boolean = false,
    val showArchiveDialog: Boolean = false,
    val showOverflowMenu: Boolean = false,
    val showDatePickerDialog: Boolean = false,
    val showFabricNameDialog: Boolean = false,
    val fabricNameDraft: String = "",

    // Payment dialog state
    val paymentAmountInput: String = "",
    val wasPaymentCapped: Boolean = false,
    val paymentMethodSelection: PaymentMethod = PaymentMethod.TRANSFER,
    val paymentTypeSelection: PaymentType = PaymentType.DEPOSIT,
    val isPaymentHistoryExpanded: Boolean = true,

    // Notes editor
    val isEditingNotes: Boolean = false,
    val notesDraft: String = "",

    // Status sheet
    val selectedNewStatus: OrderStatus? = null,
    val selectedNewSubStatus: OrderSubStatus? = null,

    val errorMessage: UiText? = null,
)

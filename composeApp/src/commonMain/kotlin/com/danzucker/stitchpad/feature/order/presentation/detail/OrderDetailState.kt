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

data class OrderDetailState(
    val order: Order? = null,
    val user: User? = null,
    val customer: Customer? = null,
    val measurement: Measurement? = null,
    val style: Style? = null,
    val isLoading: Boolean = true,

    val showMeasurementPickerSheet: Boolean = false,
    val availableMeasurements: List<Measurement> = emptyList(),

    val showStylePickerSheet: Boolean = false,
    val availableStyles: List<Style> = emptyList(),

    // Dialogs / sheets
    val showDeleteDialog: Boolean = false,
    val showStatusSheet: Boolean = false,
    val showBalanceWarningDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    val showRecordPaymentDialog: Boolean = false,
    val showArchiveDialog: Boolean = false,
    val showOverflowMenu: Boolean = false,
    val showDatePickerDialog: Boolean = false,

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

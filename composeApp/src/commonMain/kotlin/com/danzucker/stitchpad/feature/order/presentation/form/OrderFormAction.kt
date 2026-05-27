package com.danzucker.stitchpad.feature.order.presentation.form

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderPriority

sealed interface OrderFormAction {
    // Navigation
    data object OnNextStep : OrderFormAction
    data object OnPreviousStep : OrderFormAction
    data object OnNavigateBack : OrderFormAction

    // Step 1 - Customer
    data class OnSelectCustomer(val customer: Customer) : OrderFormAction
    data class OnCustomerSearchChange(val query: String) : OrderFormAction

    // Step 2 - Items
    data object OnAddItem : OrderFormAction
    data class OnRemoveItem(val itemId: String) : OrderFormAction
    data class OnItemGarmentTypeChange(val itemId: String, val type: GarmentType?) : OrderFormAction
    data class OnItemDescriptionChange(val itemId: String, val description: String) : OrderFormAction
    data class OnItemPriceChange(val itemId: String, val price: String) : OrderFormAction
    data class OnItemMeasurementChange(val itemId: String, val measurementId: String?) : OrderFormAction
    data class OnItemFabricNameChange(val itemId: String, val fabricName: String) : OrderFormAction

    // PTSP-11 multi-image actions — STYLE

    /** User picked a style from the saved-styles picker sheet. Appends a LIBRARY ref. */
    data class OnItemPickSavedStyle(val itemId: String, val styleId: String) : OrderFormAction

    /** User uploaded a new style photo (camera or gallery). Appends to uploadedStyleBytesList. */
    data class OnItemAddStylePhoto(val itemId: String, val photoBytes: ByteArray) : OrderFormAction

    /** Remove a saved-ref or a session-uploaded style at the combined-list index. */
    data class OnItemRemoveStyleImage(val itemId: String, val index: Int) : OrderFormAction
    data class OnItemStyleDescriptionChange(val itemId: String, val description: String) : OrderFormAction
    data class OnItemSaveStyleToGalleryToggle(val itemId: String, val value: Boolean) : OrderFormAction
    data class OnOpenStylePickerSheet(val itemId: String) : OrderFormAction
    data object OnDismissStylePickerSheet : OrderFormAction

    // PTSP-11 multi-image actions — FABRIC

    /** User uploaded a new fabric photo (camera or gallery). Appends to uploadedFabricBytesList. */
    data class OnItemAddFabricPhoto(val itemId: String, val photoBytes: ByteArray) : OrderFormAction

    /** Remove a saved-ref or a session-uploaded fabric at the combined-list index. */
    data class OnItemRemoveFabricImage(val itemId: String, val index: Int) : OrderFormAction

    // Step 3 - Details
    data class OnDeadlineChange(val deadline: Long?) : OrderFormAction
    data class OnPriorityChange(val priority: OrderPriority) : OrderFormAction
    data class OnDepositChange(val deposit: String) : OrderFormAction
    data class OnNotesChange(val notes: String) : OrderFormAction

    // Save
    data object OnSave : OrderFormAction
    data object OnErrorDismiss : OrderFormAction
}

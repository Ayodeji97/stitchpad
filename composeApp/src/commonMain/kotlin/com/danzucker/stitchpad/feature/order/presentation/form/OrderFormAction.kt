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
    data class OnItemGarmentTypeChange(val itemId: String, val type: GarmentType) : OrderFormAction
    data class OnItemDescriptionChange(val itemId: String, val description: String) : OrderFormAction
    data class OnItemPriceChange(val itemId: String, val price: String) : OrderFormAction
    data class OnItemStyleChange(val itemId: String, val styleId: String?) : OrderFormAction
    data class OnItemMeasurementChange(val itemId: String, val measurementId: String?) : OrderFormAction
    data class OnItemFabricPhotoPicked(val itemId: String, val photoBytes: ByteArray) : OrderFormAction
    data class OnItemFabricPhotoRemoved(val itemId: String) : OrderFormAction

    // Step 3 - Details
    data class OnDeadlineChange(val deadline: Long?) : OrderFormAction
    data class OnPriorityChange(val priority: OrderPriority) : OrderFormAction
    data class OnDepositChange(val deposit: String) : OrderFormAction
    data class OnNotesChange(val notes: String) : OrderFormAction

    // Save
    data object OnSave : OrderFormAction
    data object OnErrorDismiss : OrderFormAction
}

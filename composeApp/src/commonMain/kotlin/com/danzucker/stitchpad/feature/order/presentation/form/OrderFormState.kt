package com.danzucker.stitchpad.feature.order.presentation.form

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.presentation.UiText
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class OrderFormState(
    val currentStep: Int = 1,
    val isEditMode: Boolean = false,
    // Step 1 - Customer
    val customers: List<Customer> = emptyList(),
    val customerSearchQuery: String = "",
    val selectedCustomer: Customer? = null,
    // Step 2 - Items
    val items: List<OrderItemFormState> = listOf(OrderItemFormState()),
    val availableStyles: List<Style> = emptyList(),
    val availableMeasurements: List<Measurement> = emptyList(),
    // Step 3 - Details
    val deadline: Long? = null,
    val priority: OrderPriority = OrderPriority.NORMAL,
    val depositPaid: String = "",
    val notes: String = "",
    // General
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null
)

data class OrderItemFormState
@OptIn(ExperimentalUuidApi::class)
constructor(
    val id: String = Uuid.random().toString(),
    val garmentType: GarmentType? = null,
    val description: String = "",
    val price: String = "",
    val styleId: String? = null,
    val measurementId: String? = null,
    val fabricPhotoBytes: ByteArray? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null
)

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
    /** Item id whose Style picker sheet is currently visible. Null = no sheet. */
    val stylePickerSheetForItemId: String? = null,
    // Step 3 - Details
    val deadline: Long? = null,
    val priority: OrderPriority = OrderPriority.NORMAL,
    val depositPaid: String = "",
    val notes: String = "",
    val depositReconciliationPrompt: DepositPrompt? = null,
    // General
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null
)

/**
 * State driving the AlertDialog that gates a deposit-changing save when
 * the order already has at least one recorded payment. Non-null means
 * the dialog is visible; [oldAmount] and [newAmount] are integer-naira
 * doubles surfaced in the dialog body.
 *
 * The dialog itself is rendered by `OrderFormScreen.kt` — wired in a
 * separate UI commit per the PTSP-14 plan.
 */
data class DepositPrompt(
    val oldAmount: Double,
    val newAmount: Double,
    val nonDepositTotal: Double,
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
    val fabricPhotoStoragePath: String? = null,
    val fabricName: String = "",
    // PTSP-9 style image
    val stylePhotoBytes: ByteArray? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val styleDescription: String = "",
    /** When true (default), save() creates a new Style entity. When false, the image lives on the OrderItem only. */
    val saveStyleToGallery: Boolean = true,
)

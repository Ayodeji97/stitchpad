package com.danzucker.stitchpad.feature.order.presentation.form

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
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
    val measurementId: String? = null,
    val fabricName: String = "",
    // PTSP-11 — multi-image lists
    /** Already-saved style refs loaded from an edit. New picks/uploads append to this. */
    val styleImageRefs: List<StyleImageRef> = emptyList(),
    /** Newly-uploaded style bytes this session, not yet committed. */
    val uploadedStyleBytesList: List<ByteArray> = emptyList(),
    /** Storage paths queued for deletion on next successful save (uploaded refs the user removed). */
    val pendingStyleStorageDeletions: List<String> = emptyList(),
    /** Already-saved fabric refs loaded from an edit. New uploads append to this. */
    val fabricImageRefs: List<FabricImageRef> = emptyList(),
    /** Newly-uploaded fabric bytes this session, not yet committed. */
    val uploadedFabricBytesList: List<ByteArray> = emptyList(),
    /** Storage paths queued for deletion on next successful save. */
    val pendingFabricStorageDeletions: List<String> = emptyList(),
    /** Shared description for ALL newly-uploaded styles this session. Optional. */
    val styleDescription: String = "",
    /** When true, uploaded styles become Style entities in the customer's gallery. */
    val saveStyleToGallery: Boolean = true,
)

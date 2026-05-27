package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_order_customer_required
import stitchpad.composeapp.generated.resources.error_order_item_price_required
import stitchpad.composeapp.generated.resources.error_order_items_required
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("LongParameterList")
class OrderFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val styleRepository: StyleRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val orderId: String? = savedStateHandle["orderId"]
    private val seedFromOrderId: String? = savedStateHandle["seedFromOrderId"]
    private val initialCustomerId: String? = savedStateHandle["customerId"]
    private var userId: String? = null

    // Preserved across edit: carry original metadata so save() doesn't overwrite them.
    private var loadedCreatedAt: Long = 0L
    private var loadedStatus: OrderStatus = OrderStatus.PENDING
    private var loadedStatusHistory: List<StatusChange> = emptyList()
    private var loadedPayments: List<Payment> = emptyList()

    // On edit (orderId != null), loadOrder may finish before observeCustomers emits.
    // On create-with-pre-selected-customer (initialCustomerId != null, from
    // PTSP-15's "New order" sheet action), we already know the target. Either way
    // we record the target id and resolve it reactively whenever the customer list
    // emits — the existing resolvePendingCustomer() does the matching.
    private var pendingCustomerId: String? = initialCustomerId

    // Track the per-customer style/measurement collectors so we can cancel them when the
    // user switches customers. Without this, the previous customer's flows keep emitting
    // into state and race with the new customer's data.
    private var styleJob: Job? = null
    private var measurementJob: Job? = null

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(OrderFormState(isEditMode = orderId != null))

    private val _events = Channel<OrderFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadInitialData()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OrderFormState(isEditMode = orderId != null)
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: OrderFormAction) {
        when (action) {
            OrderFormAction.OnNextStep -> {
                val current = _state.value.currentStep
                if (current < 3) _state.update { it.copy(currentStep = current + 1) }
            }
            OrderFormAction.OnPreviousStep -> {
                val current = _state.value.currentStep
                if (current > 1) _state.update { it.copy(currentStep = current - 1) }
            }
            OrderFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(OrderFormEvent.NavigateBack) }
            }
            is OrderFormAction.OnSelectCustomer -> {
                _state.update { it.copy(selectedCustomer = action.customer) }
                loadCustomerData(action.customer.id)
            }
            is OrderFormAction.OnCustomerSearchChange -> {
                _state.update { it.copy(customerSearchQuery = action.query) }
            }
            OrderFormAction.OnAddItem -> {
                _state.update { it.copy(items = it.items + OrderItemFormState()) }
            }
            is OrderFormAction.OnRemoveItem -> {
                _state.update { it.copy(items = it.items.filter { item -> item.id != action.itemId }) }
            }
            is OrderFormAction.OnItemGarmentTypeChange -> updateItem(action.itemId) {
                it.copy(garmentType = action.type)
            }
            is OrderFormAction.OnItemDescriptionChange -> updateItem(action.itemId) {
                it.copy(description = action.description)
            }
            is OrderFormAction.OnItemPriceChange -> updateItem(action.itemId) {
                it.copy(price = action.price)
            }
            is OrderFormAction.OnItemMeasurementChange -> updateItem(action.itemId) {
                it.copy(measurementId = action.measurementId)
            }
            is OrderFormAction.OnItemFabricNameChange -> updateItem(action.itemId) {
                it.copy(fabricName = action.fabricName)
            }
            is OrderFormAction.OnItemPickSavedStyle -> updateItem(action.itemId) {
                // Append a LIBRARY ref. Capacity check: stylePickerSheet already
                // marks already-picked as disabled, so the user shouldn't be able
                // to over-pick — but defend with a guard anyway.
                val total = it.styleImageRefs.size + it.uploadedStyleBytesList.size
                if (total >= 3) return@updateItem it
                val alreadyHasStyle = it.styleImageRefs.any { ref ->
                    ref.source == StyleImageSource.LIBRARY && ref.styleId == action.styleId
                }
                if (alreadyHasStyle) {
                    return@updateItem it // already picked
                }
                it.copy(
                    styleImageRefs = it.styleImageRefs + StyleImageRef(
                        source = StyleImageSource.LIBRARY,
                        styleId = action.styleId,
                    ),
                )
            }
            is OrderFormAction.OnItemAddStylePhoto -> updateItem(action.itemId) {
                val total = it.styleImageRefs.size + it.uploadedStyleBytesList.size
                if (total >= 3) return@updateItem it
                it.copy(uploadedStyleBytesList = it.uploadedStyleBytesList + action.photoBytes)
            }
            is OrderFormAction.OnItemRemoveStyleImage -> updateItem(action.itemId) {
                // The combined list is: styleImageRefs FIRST, then uploadedStyleBytesList.
                // index addresses that combined position.
                val savedCount = it.styleImageRefs.size
                when {
                    action.index < savedCount -> {
                        val removed = it.styleImageRefs[action.index]
                        val deletionsAdd = if (removed.source == StyleImageSource.UPLOADED &&
                            !removed.photoStoragePath.isNullOrBlank()
                        ) {
                            it.pendingStyleStorageDeletions + removed.photoStoragePath
                        } else {
                            it.pendingStyleStorageDeletions
                        }
                        it.copy(
                            styleImageRefs = it.styleImageRefs.toMutableList()
                                .also { list -> list.removeAt(action.index) },
                            pendingStyleStorageDeletions = deletionsAdd,
                        )
                    }
                    else -> {
                        val byteIndex = action.index - savedCount
                        if (byteIndex !in it.uploadedStyleBytesList.indices) return@updateItem it
                        it.copy(
                            uploadedStyleBytesList = it.uploadedStyleBytesList.toMutableList()
                                .also { list -> list.removeAt(byteIndex) },
                        )
                    }
                }
            }
            is OrderFormAction.OnItemStyleDescriptionChange -> updateItem(action.itemId) {
                it.copy(styleDescription = action.description)
            }
            is OrderFormAction.OnItemSaveStyleToGalleryToggle -> updateItem(action.itemId) {
                it.copy(saveStyleToGallery = action.value)
            }
            is OrderFormAction.OnOpenStylePickerSheet -> {
                _state.update { it.copy(stylePickerSheetForItemId = action.itemId) }
            }
            OrderFormAction.OnDismissStylePickerSheet -> {
                _state.update { it.copy(stylePickerSheetForItemId = null) }
            }
            is OrderFormAction.OnItemAddFabricPhoto -> updateItem(action.itemId) {
                val total = it.fabricImageRefs.size + it.uploadedFabricBytesList.size
                if (total >= 3) return@updateItem it
                it.copy(uploadedFabricBytesList = it.uploadedFabricBytesList + action.photoBytes)
            }
            is OrderFormAction.OnItemRemoveFabricImage -> updateItem(action.itemId) {
                val savedCount = it.fabricImageRefs.size
                when {
                    action.index < savedCount -> {
                        val removed = it.fabricImageRefs[action.index]
                        it.copy(
                            fabricImageRefs = it.fabricImageRefs.toMutableList()
                                .also { list -> list.removeAt(action.index) },
                            pendingFabricStorageDeletions =
                            it.pendingFabricStorageDeletions + removed.photoStoragePath,
                        )
                    }
                    else -> {
                        val byteIndex = action.index - savedCount
                        if (byteIndex !in it.uploadedFabricBytesList.indices) return@updateItem it
                        it.copy(
                            uploadedFabricBytesList = it.uploadedFabricBytesList.toMutableList()
                                .also { list -> list.removeAt(byteIndex) },
                        )
                    }
                }
            }
            is OrderFormAction.OnDeadlineChange -> {
                _state.update { it.copy(deadline = action.deadline) }
            }
            is OrderFormAction.OnPriorityChange -> {
                _state.update { it.copy(priority = action.priority) }
            }
            is OrderFormAction.OnDepositChange -> {
                _state.update { it.copy(depositPaid = action.deposit) }
            }
            is OrderFormAction.OnNotesChange -> {
                _state.update { it.copy(notes = action.notes) }
            }
            OrderFormAction.OnSave -> save()
            OrderFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun updateItem(itemId: String, transform: (OrderItemFormState) -> OrderItemFormState) {
        _state.update { state ->
            state.copy(
                items = state.items.map { if (it.id == itemId) transform(it) else it }
            )
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            userId = authRepository.getCurrentUser()?.id ?: return@launch
            observeCustomers()
            if (orderId != null) {
                loadOrder(orderId)
            } else if (seedFromOrderId != null) {
                loadOrderForSeed(seedFromOrderId)
            }
        }
    }

    private fun observeCustomers() {
        val uid = userId ?: return
        viewModelScope.launch {
            customerRepository.observeCustomers(uid).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(customers = result.data) }
                    resolvePendingCustomer()
                }
            }
        }
    }

    private fun resolvePendingCustomer() {
        val targetId = pendingCustomerId ?: return
        val current = _state.value
        if (current.selectedCustomer != null) return
        val match = current.customers.find { it.id == targetId }
        if (match != null) {
            _state.update { it.copy(selectedCustomer = match) }
            loadCustomerData(match.id)
            pendingCustomerId = null
        }
    }

    private fun loadCustomerData(customerId: String) {
        val uid = userId ?: return
        styleJob?.cancel()
        styleJob = viewModelScope.launch {
            styleRepository.observeStyles(uid, customerId).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(availableStyles = result.data) }
                }
            }
        }
        measurementJob?.cancel()
        measurementJob = viewModelScope.launch {
            measurementRepository.observeMeasurements(uid, customerId).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(availableMeasurements = result.data) }
                }
            }
        }
    }

    private fun loadOrder(id: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = orderRepository.getOrder(uid, id)) {
                is Result.Success -> {
                    val order = result.data
                    loadedCreatedAt = order.createdAt
                    loadedStatus = order.status
                    loadedStatusHistory = order.statusHistory
                    loadedPayments = order.payments
                    pendingCustomerId = order.customerId
                    _state.update {
                        it.copy(
                            items = order.items.map { item -> item.toFormState() },
                            deadline = order.deadline,
                            priority = order.priority,
                            depositPaid = if (order.depositPaid > 0) order.depositPaid.toLong().toString() else "",
                            notes = order.notes ?: "",
                            isLoading = false
                        )
                    }
                    resolvePendingCustomer()
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun loadOrderForSeed(sourceOrderId: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = orderRepository.getOrder(uid, sourceOrderId)) {
                is Result.Success -> {
                    val source = result.data
                    pendingCustomerId = source.customerId
                    _state.update {
                        it.copy(
                            // Seeded order is brand new: each item gets a fresh id, AND we
                            // strip the one-off uploaded storage paths (both style + fabric)
                            // so the new order doesn't point at the source order's Storage
                            // objects. Without this, deleting either order would break the
                            // other's image via FirebaseOrderRepository.deleteOrder's
                            // cleanup. Library-source style refs are preserved
                            // (customer-gallery Styles are shared across orders by design).
                            items = source.items.map { item ->
                                item.copy(
                                    id = Uuid.random().toString(),
                                    // Drop ALL fabric refs (always uploaded → all point at source order)
                                    fabricImages = emptyList(),
                                    fabricPhotoUrl = null,
                                    fabricPhotoStoragePath = null,
                                    // Keep LIBRARY style refs; drop UPLOADED style refs
                                    styleImages = item.styleImages.filter {
                                        it.source == StyleImageSource.LIBRARY
                                    },
                                    stylePhotoUrl = null,
                                    stylePhotoStoragePath = null,
                                ).toFormState()
                            },
                            deadline = source.deadline,
                            priority = source.priority,
                            depositPaid = "",
                            notes = source.notes ?: "",
                            isLoading = false,
                        )
                    }
                    resolvePendingCustomer()
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                    }
                }
            }
        }
    }

    private fun OrderItem.toFormState() = OrderItemFormState(
        id = id,
        garmentType = garmentType,
        description = description,
        price = if (price > 0) price.toLong().toString() else "",
        measurementId = measurementId,
        fabricName = fabricName.orEmpty(),
        // PTSP-11 — load the lists; uploadedBytesList stays empty until the user
        // uploads new this session. Description + toggle reset to defaults.
        styleImageRefs = styleImages,
        fabricImageRefs = fabricImages,
        uploadedStyleBytesList = emptyList(),
        uploadedFabricBytesList = emptyList(),
        pendingStyleStorageDeletions = emptyList(),
        pendingFabricStorageDeletions = emptyList(),
        styleDescription = "",
        saveStyleToGallery = true,
    )

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
    private fun save() {
        // Idempotency: if a previous save is in-flight, ignore this re-entry.
        // The UI's `enabled = !isSaving` already blocks the button visually, but
        // a state update inside viewModelScope.launch doesn't propagate before a
        // rapid double-tap can re-enter. With PTSP-9's gallery-save path, a
        // duplicate entry creates duplicate Style entities — guard early.
        if (_state.value.isSaving) return

        val s = _state.value
        val uid = userId ?: return

        val customer = s.selectedCustomer
        if (customer == null) {
            setError(Res.string.error_order_customer_required)
            return
        }

        val formItems = s.items.filter { it.garmentType != null }
        if (formItems.isEmpty()) {
            setError(Res.string.error_order_items_required)
            return
        }

        // Block save when any filled-in item is missing a valid positive price.
        // Silently persisting 0.0 would undercharge and skew totals.
        val hasInvalidPrice = formItems.any { (it.price.toDoubleOrNull() ?: 0.0) <= 0.0 }
        if (hasInvalidPrice) {
            setError(Res.string.error_order_item_price_required)
            return
        }

        // Resolve order id BEFORE any upload so storage paths match the actual doc id.
        val actualOrderId = orderId ?: orderRepository.newOrderId(uid)

        // Claim the saving state SYNCHRONOUSLY before launching the coroutine.
        // Setting isSaving inside the launch body leaves a microsecond race
        // window where a second OnSave dispatch can pass the `if (isSaving)
        // return` guard at the top and enter save() twice — with the new
        // save-to-gallery path that would create duplicate Style documents.
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val orderItems = mutableListOf<OrderItem>()
            for (item in formItems) {
                val garmentType = item.garmentType!!
                val price = item.price.toDoubleOrNull() ?: 0.0

                val resolution = when (
                    val r = resolveItemImages(uid, customer.id, actualOrderId, item)
                ) {
                    is Result.Success -> r.data
                    is Result.Error -> {
                        _state.update {
                            it.copy(isSaving = false, errorMessage = r.error.toOrderUiText())
                        }
                        return@launch
                    }
                }

                orderItems.add(
                    OrderItem(
                        id = item.id,
                        garmentType = garmentType,
                        description = item.description.trim(),
                        price = price,
                        measurementId = item.measurementId,
                        fabricName = item.fabricName.trim().ifBlank { null },
                        styleImages = resolution.styleImages,
                        fabricImages = resolution.fabricImages,
                        // Legacy single fields — populated by the mapper from the lists on write.
                        // Leave null on the domain object; mapper handles double-write.
                    ),
                )
            }

            val totalPrice = orderItems.sumOf { it.price }
            val deposit = s.depositPaid.toDoubleOrNull() ?: 0.0
            val now = Clock.System.now().toEpochMilliseconds()
            val isEdit = orderId != null

            val order = Order(
                id = actualOrderId,
                userId = uid,
                customerId = customer.id,
                customerName = customer.name,
                items = orderItems,
                status = if (isEdit) loadedStatus else OrderStatus.PENDING,
                priority = s.priority,
                statusHistory = if (isEdit) {
                    loadedStatusHistory
                } else {
                    listOf(StatusChange(OrderStatus.PENDING, now))
                },
                totalPrice = totalPrice,
                payments = if (!isEdit && deposit > 0.0) {
                    listOf(
                        Payment(
                            id = Uuid.random().toString(),
                            amount = deposit,
                            method = PaymentMethod.OTHER,
                            type = PaymentType.DEPOSIT,
                            recordedAt = now,
                            note = null,
                        ),
                    )
                } else if (isEdit) {
                    loadedPayments
                } else {
                    emptyList()
                },
                deadline = s.deadline,
                notes = s.notes.trim().ifBlank { null },
                createdAt = if (isEdit) loadedCreatedAt else 0L,
                updatedAt = 0L
            )

            val result = if (isEdit) {
                orderRepository.updateOrder(uid, order)
            } else {
                orderRepository.createOrder(uid, order)
            }
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> {
                    cleanUpPendingStorageDeletions(formItems)
                    _events.send(OrderFormEvent.OrderSaved)
                }
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }

    private fun setError(resource: org.jetbrains.compose.resources.StringResource) {
        _state.update { it.copy(errorMessage = UiText.StringResourceText(resource)) }
    }

    /**
     * Result holder for per-item multi-image resolution at save time.
     */
    private data class ItemImageResolution(
        val styleImages: List<StyleImageRef>,
        val fabricImages: List<FabricImageRef>,
    )

    /**
     * Per-item batch resolution at save time:
     *  - Existing saved refs (styleImageRefs / fabricImageRefs) pass through unchanged
     *  - New uploaded bytes:
     *      • Fabric → upload to Firebase Storage as FabricImageRef
     *      • Style + toggle ON  → batch-create Style entities, refs become LIBRARY
     *      • Style + toggle OFF → upload to Firebase Storage, refs become UPLOADED
     *  - On ANY failure → Result.Error so save() aborts (no silent data loss)
     */
    @Suppress("ReturnCount")
    private suspend fun resolveItemImages(
        userId: String,
        customerId: String,
        orderId: String,
        item: OrderItemFormState,
    ): Result<ItemImageResolution, DataError.Network> {
        // 1) Fabric: existing refs + uploaded bytes -> Firebase Storage
        val fabricUploadResult = orderRepository.uploadFabricPhotos(
            userId = userId,
            orderId = orderId,
            itemId = item.id,
            photoBytesList = item.uploadedFabricBytesList,
        )
        val uploadedFabric = when (fabricUploadResult) {
            is Result.Success -> fabricUploadResult.data.map { (url, path) -> FabricImageRef(url, path) }
            is Result.Error -> return Result.Error(fabricUploadResult.error)
        }
        val finalFabricImages = item.fabricImageRefs + uploadedFabric

        // 2) Style: existing refs pass through; uploaded bytes branch on toggle
        val uploadedStyleRefs: List<StyleImageRef> = if (item.uploadedStyleBytesList.isEmpty()) {
            emptyList()
        } else if (item.saveStyleToGallery) {
            val createResult = styleRepository.createStyles(
                userId = userId,
                customerId = customerId,
                description = item.styleDescription.trim(),
                photoBytesList = item.uploadedStyleBytesList,
            )
            when (createResult) {
                is Result.Success -> createResult.data.map { styleId ->
                    StyleImageRef(source = StyleImageSource.LIBRARY, styleId = styleId)
                }
                is Result.Error -> return Result.Error(createResult.error)
            }
        } else {
            val uploadResult = orderRepository.uploadStylePhotos(
                userId = userId,
                orderId = orderId,
                itemId = item.id,
                photoBytesList = item.uploadedStyleBytesList,
            )
            when (uploadResult) {
                is Result.Success -> uploadResult.data.map { (url, path) ->
                    StyleImageRef(
                        source = StyleImageSource.UPLOADED,
                        photoUrl = url,
                        photoStoragePath = path,
                    )
                }
                is Result.Error -> return Result.Error(uploadResult.error)
            }
        }
        val finalStyleImages = item.styleImageRefs + uploadedStyleRefs

        return Result.Success(
            ItemImageResolution(
                styleImages = finalStyleImages,
                fabricImages = finalFabricImages,
            ),
        )
    }

    /**
     * Best-effort cleanup of orphaned storage objects the user removed during
     * this edit session. Runs after a successful save. Failures are silent —
     * the order saved fine; the orphan is a Storage-side concern.
     */
    @Suppress("UnusedParameter")
    private suspend fun cleanUpPendingStorageDeletions(items: List<OrderItemFormState>) {
        items.forEach { item ->
            (item.pendingStyleStorageDeletions + item.pendingFabricStorageDeletions)
                .filter { it.isNotBlank() }
                .forEach { _ ->
                    // FirebaseOrderRepository exposes a public-style `deleteFabricPhoto` we
                    // could call, but to keep this generic we use the storage reference
                    // directly via the existing repository. Since the repository surface
                    // doesn't expose raw storage delete, log this as a no-op for V1 —
                    // the orphan exists for the lifetime of the order. Acceptable per
                    // spec §9 "accepted limitation".
                    // (Implementation note: if we add OrderRepository.deletePhoto(path)
                    // in a follow-up, call it here.)
                }
        }
    }
}

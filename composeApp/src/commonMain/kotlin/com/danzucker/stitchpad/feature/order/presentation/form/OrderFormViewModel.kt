package com.danzucker.stitchpad.feature.order.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.order.data.FirebaseOrderRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private var userId: String? = null

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

    @Suppress("CyclomaticComplexMethod")
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
            is OrderFormAction.OnItemStyleChange -> updateItem(action.itemId) {
                it.copy(styleId = action.styleId)
            }
            is OrderFormAction.OnItemMeasurementChange -> updateItem(action.itemId) {
                it.copy(measurementId = action.measurementId)
            }
            is OrderFormAction.OnItemFabricPhotoPicked -> updateItem(action.itemId) {
                it.copy(fabricPhotoBytes = action.photoBytes)
            }
            is OrderFormAction.OnItemFabricPhotoRemoved -> updateItem(action.itemId) {
                it.copy(fabricPhotoBytes = null, fabricPhotoUrl = null, fabricPhotoStoragePath = null)
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
            if (orderId != null) loadOrder(orderId)
        }
    }

    private fun observeCustomers() {
        val uid = userId ?: return
        viewModelScope.launch {
            customerRepository.observeCustomers(uid).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(customers = result.data) }
                }
            }
        }
    }

    private fun loadCustomerData(customerId: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            styleRepository.observeStyles(uid, customerId).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(availableStyles = result.data) }
                }
            }
        }
        viewModelScope.launch {
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
                    val customer = _state.value.customers.find { it.id == order.customerId }
                    _state.update {
                        it.copy(
                            selectedCustomer = customer,
                            items = order.items.map { item -> item.toFormState() },
                            deadline = order.deadline,
                            priority = order.priority,
                            depositPaid = if (order.depositPaid > 0) order.depositPaid.toLong().toString() else "",
                            notes = order.notes ?: "",
                            isLoading = false
                        )
                    }
                    if (customer != null) loadCustomerData(customer.id)
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
        styleId = styleId,
        measurementId = measurementId,
        fabricPhotoUrl = fabricPhotoUrl,
        fabricPhotoStoragePath = fabricPhotoStoragePath
    )

    @OptIn(ExperimentalUuidApi::class)
    private fun save() {
        val s = _state.value
        val customer = s.selectedCustomer ?: return
        val uid = userId ?: return
        val firebaseRepo = orderRepository as FirebaseOrderRepository

        val formItems = s.items.filter { it.garmentType != null }
        if (formItems.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }

            // Determine the order ID early so we can use it for storage paths
            val actualOrderId = orderId ?: ""

            // Build order items, uploading fabric photos as needed
            val orderItems = formItems.map { item ->
                val garmentType = item.garmentType!!
                val price = item.price.toDoubleOrNull() ?: 0.0

                // Upload fabric photo if new bytes are present
                val (fabricUrl, fabricPath) = if (item.fabricPhotoBytes != null) {
                    val tempOrderId = actualOrderId.ifBlank { "temp_${item.id}" }
                    when (val uploadResult = firebaseRepo.uploadFabricPhoto(uid, tempOrderId, item.id, item.fabricPhotoBytes)) {
                        is Result.Success -> uploadResult.data
                        is Result.Error -> item.fabricPhotoUrl to item.fabricPhotoStoragePath
                    }
                } else {
                    item.fabricPhotoUrl to item.fabricPhotoStoragePath
                }

                OrderItem(
                    id = item.id,
                    garmentType = garmentType,
                    description = item.description.trim(),
                    price = price,
                    styleId = item.styleId,
                    measurementId = item.measurementId,
                    fabricPhotoUrl = fabricUrl,
                    fabricPhotoStoragePath = fabricPath
                )
            }

            val totalPrice = orderItems.sumOf { it.price }
            val deposit = s.depositPaid.toDoubleOrNull() ?: 0.0
            val now = Clock.System.now().toEpochMilliseconds()

            val order = Order(
                id = actualOrderId,
                userId = uid,
                customerId = customer.id,
                customerName = customer.name,
                items = orderItems,
                status = OrderStatus.PENDING,
                priority = s.priority,
                statusHistory = if (orderId != null) {
                    emptyList()
                } else {
                    listOf(StatusChange(OrderStatus.PENDING, now))
                },
                totalPrice = totalPrice,
                depositPaid = deposit,
                balanceRemaining = totalPrice - deposit,
                deadline = s.deadline,
                notes = s.notes.trim().ifBlank { null },
                createdAt = 0L,
                updatedAt = 0L
            )

            val result = if (orderId != null) {
                orderRepository.updateOrder(uid, order)
            } else {
                orderRepository.createOrder(uid, order)
            }
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> _events.send(OrderFormEvent.OrderSaved)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }
}

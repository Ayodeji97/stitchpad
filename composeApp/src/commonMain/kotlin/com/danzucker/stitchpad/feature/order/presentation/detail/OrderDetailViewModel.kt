package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.ReceiptData
import com.danzucker.stitchpad.core.sharing.ReceiptFormatter
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayNameAsync
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.receipt_share_error
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository,
    private val receiptSharer: OrderReceiptSharer,
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private var hasStartedObserving = false
    private var measurementsJob: Job? = null
    private var loadedMeasurementsCustomerId: String? = null
    private var styleJob: Job? = null
    private var loadedStyleId: String? = null
    private val _state = MutableStateFlow(OrderDetailState())

    private val _events = Channel<OrderDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasStartedObserving) {
                hasStartedObserving = true
                observeOrder()
                loadUser()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OrderDetailState(),
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun onAction(action: OrderDetailAction) {
        when (action) {
            // Navigation
            OrderDetailAction.OnBackClick ->
                viewModelScope.launch { _events.send(OrderDetailEvent.NavigateBack) }
            OrderDetailAction.OnEditClick ->
                viewModelScope.launch { _events.send(OrderDetailEvent.NavigateToOrderForm(orderId)) }
            OrderDetailAction.OnCustomerClick -> {
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch { _events.send(OrderDetailEvent.NavigateToCustomerDetail(customerId)) }
            }

            // Top-bar overflow
            OrderDetailAction.OnOverflowMenuToggle ->
                _state.update { it.copy(showOverflowMenu = !it.showOverflowMenu) }
            OrderDetailAction.OnDuplicateClick -> {
                _state.update { it.copy(showOverflowMenu = false) }
                viewModelScope.launch { _events.send(OrderDetailEvent.NavigateToCreateOrder(orderId)) }
            }

            // Delete
            OrderDetailAction.OnDeleteClick ->
                _state.update { it.copy(showOverflowMenu = false, showDeleteDialog = true) }
            OrderDetailAction.OnConfirmDelete -> deleteOrder()
            OrderDetailAction.OnDismissDeleteDialog ->
                _state.update { it.copy(showDeleteDialog = false) }

            // Archive
            OrderDetailAction.OnArchiveClick ->
                _state.update { it.copy(showOverflowMenu = false, showArchiveDialog = true) }
            OrderDetailAction.OnConfirmArchive -> archiveOrder()
            OrderDetailAction.OnDismissArchiveDialog ->
                _state.update { it.copy(showArchiveDialog = false) }

            // Status sheet
            OrderDetailAction.OnUpdateStatusClick ->
                _state.update { it.copy(showStatusSheet = true) }
            is OrderDetailAction.OnSelectStatusTransition ->
                handleStatusTransition(action.transition)
            OrderDetailAction.OnDismissStatusSheet ->
                _state.update {
                    it.copy(
                        showStatusSheet = false,
                        // Defensive clear: if the sheet's onDismissRequest fires after
                        // handleStatusTransition has already populated these (rare iOS sheet
                        // timing edge case per feedback_ios_modal_bottom_sheet_timing memory),
                        // leaving them set would silently suppress the next balance-warning.
                        selectedNewStatus = null,
                        selectedNewSubStatus = null,
                    )
                }

            OrderDetailAction.OnBalanceWarningRecordPayment -> {
                _state.update {
                    it.copy(
                        showBalanceWarningDialog = false,
                        selectedNewStatus = null,
                        selectedNewSubStatus = null,
                        showRecordPaymentDialog = true,
                        paymentAmountInput = "",
                        wasPaymentCapped = false,
                    )
                }
            }
            OrderDetailAction.OnBalanceWarningProceed -> {
                val pending = _state.value.selectedNewStatus
                val pendingSub = _state.value.selectedNewSubStatus
                _state.update {
                    it.copy(
                        showBalanceWarningDialog = false,
                        selectedNewStatus = null,
                        selectedNewSubStatus = null,
                    )
                }
                if (pending != null) performStatusUpdate(pending, pendingSub)
            }
            OrderDetailAction.OnBalanceWarningDismiss ->
                _state.update {
                    it.copy(
                        showBalanceWarningDialog = false,
                        selectedNewStatus = null,
                        selectedNewSubStatus = null,
                    )
                }

            // Sharing
            OrderDetailAction.OnShareClick ->
                _state.update { it.copy(showShareSheet = true) }
            OrderDetailAction.OnShareAsImageClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt { receiptSharer.shareReceiptAsImage(it) }
            }
            OrderDetailAction.OnShareAsPdfClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt { receiptSharer.shareReceiptAsPdf(it) }
            }
            OrderDetailAction.OnDismissShareSheet ->
                _state.update { it.copy(showShareSheet = false) }

            // Record payment
            OrderDetailAction.OnRecordPaymentClick -> {
                val isFirst = _state.value.order?.payments?.isEmpty() == true
                _state.update {
                    it.copy(
                        showRecordPaymentDialog = true,
                        paymentAmountInput = "",
                        wasPaymentCapped = false,
                        paymentTypeSelection = if (isFirst) PaymentType.DEPOSIT else PaymentType.PROGRESS,
                        paymentMethodSelection = PaymentMethod.TRANSFER,
                    )
                }
            }
            is OrderDetailAction.OnPaymentAmountChange -> {
                val rawDigits = action.digits.filter { it.isDigit() }.trimStart('0')
                val capped = capPaymentAmountDigits(action.digits)
                val didCap = rawDigits.isNotEmpty() && rawDigits != capped
                _state.update { it.copy(paymentAmountInput = capped, wasPaymentCapped = didCap) }
            }
            is OrderDetailAction.OnPaymentMethodSelect ->
                _state.update { it.copy(paymentMethodSelection = action.method) }
            is OrderDetailAction.OnPaymentTypeSelect ->
                _state.update { it.copy(paymentTypeSelection = action.type) }
            OrderDetailAction.OnMarkPaidInFull -> markPaidInFull()
            OrderDetailAction.OnConfirmRecordPayment -> recordPayment()
            OrderDetailAction.OnDismissRecordPayment -> {
                _state.update {
                    it.copy(
                        showRecordPaymentDialog = false,
                        paymentAmountInput = "",
                        wasPaymentCapped = false,
                    )
                }
            }
            OrderDetailAction.OnPaymentHistoryToggle ->
                _state.update { it.copy(isPaymentHistoryExpanded = !it.isPaymentHistoryExpanded) }

            // Notes
            OrderDetailAction.OnNotesEditClick ->
                _state.update {
                    it.copy(isEditingNotes = true, notesDraft = it.order?.notes.orEmpty())
                }
            is OrderDetailAction.OnNotesDraftChange ->
                _state.update { it.copy(notesDraft = action.text) }
            OrderDetailAction.OnNotesSaveClick -> saveNotes()
            OrderDetailAction.OnNotesCancelClick ->
                _state.update { it.copy(isEditingNotes = false, notesDraft = "") }

            // Customer reach-out
            OrderDetailAction.OnWhatsAppClick -> launchWhatsApp()
            OrderDetailAction.OnCallClick -> launchDialer()
            OrderDetailAction.OnSendReminderClick -> launchWhatsApp()
            OrderDetailAction.OnAddStyleClick -> {
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToStyleGallery(customerId))
                }
            }

            // Measurements
            OrderDetailAction.OnLinkMeasurementsClick ->
                _state.update { it.copy(showMeasurementPickerSheet = true) }

            is OrderDetailAction.OnSelectMeasurement -> linkExistingMeasurement(action.measurementId)

            OrderDetailAction.OnCreateNewMeasurementClick -> {
                _state.update { it.copy(showMeasurementPickerSheet = false) }
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToMeasurementForm(customerId, orderId))
                }
            }

            OrderDetailAction.OnDismissMeasurementPickerSheet ->
                _state.update { it.copy(showMeasurementPickerSheet = false) }

            // Misc
            OrderDetailAction.OnErrorDismiss ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun shareReceipt(share: suspend (ReceiptData) -> Unit) {
        val order = _state.value.order ?: return
        val user = _state.value.user ?: return
        viewModelScope.launch {
            try {
                val garmentNames = order.items
                    .map { it.garmentType }
                    .distinct()
                    .associate { it to garmentDisplayNameAsync(it) }
                val receiptData = ReceiptFormatter.format(order, user, garmentNames)
                share(receiptData)
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                _state.update {
                    it.copy(errorMessage = UiText.StringResourceText(Res.string.receipt_share_error))
                }
            }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _state.update { it.copy(user = user) }
        }
    }

    private fun observeOrder() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            orderRepository.observeOrder(userId, orderId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _state.update { it.copy(order = result.data, isLoading = false) }
                        loadCustomerIfNeeded(result.data.customerId, userId)
                        loadMeasurementsIfNeeded(result.data.customerId, userId)
                        val styleId = result.data.items.firstOrNull()?.styleId
                        if (styleId != null) {
                            loadStyleIfNeeded(result.data.customerId, styleId, userId)
                        }
                    }
                    is Result.Error -> {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = result.error.toOrderUiText())
                        }
                    }
                }
            }
        }
    }

    private fun loadCustomerIfNeeded(customerId: String, userId: String) {
        if (_state.value.customer?.id == customerId) return
        viewModelScope.launch {
            when (val res = customerRepository.getCustomer(userId, customerId)) {
                is Result.Success -> _state.update { it.copy(customer = res.data) }
                is Result.Error -> Unit
            }
        }
    }

    private fun loadMeasurementsIfNeeded(customerId: String, userId: String) {
        if (loadedMeasurementsCustomerId == customerId) return
        loadedMeasurementsCustomerId = customerId
        measurementsJob?.cancel()
        measurementsJob = viewModelScope.launch {
            measurementRepository.observeMeasurements(userId, customerId).collect { res ->
                if (res is Result.Success) {
                    _state.update { current ->
                        val linkedId = current.order?.items?.firstOrNull()?.measurementId
                        val linked = linkedId?.let { id -> res.data.firstOrNull { it.id == id } }
                        current.copy(
                            availableMeasurements = res.data,
                            measurement = linked,
                        )
                    }
                }
            }
        }
    }

    private fun linkExistingMeasurement(measurementId: String) {
        _state.update { it.copy(showMeasurementPickerSheet = false) }
        val order = _state.value.order ?: return
        val firstItem = order.items.firstOrNull() ?: return
        if (firstItem.measurementId != measurementId) {
            val updatedItems = listOf(firstItem.copy(measurementId = measurementId)) + order.items.drop(1)
            viewModelScope.launch {
                val userId = authRepository.getCurrentUser()?.id ?: return@launch
                when (val res = orderRepository.updateOrder(userId, order.copy(items = updatedItems))) {
                    is Result.Success -> Unit // observeOrder Flow re-emits with the new measurementId
                    is Result.Error -> _state.update {
                        it.copy(errorMessage = res.error.toOrderUiText())
                    }
                }
            }
        }
    }

    private fun loadStyleIfNeeded(customerId: String, styleId: String, userId: String) {
        if (loadedStyleId == styleId) return
        loadedStyleId = styleId
        styleJob?.cancel()
        styleJob = viewModelScope.launch {
            styleRepository.observeStyles(userId, customerId).collect { res ->
                if (res is Result.Success) {
                    val match = res.data.firstOrNull { it.id == styleId }
                    if (match != null) _state.update { it.copy(style = match) }
                }
            }
        }
    }

    private fun deleteOrder() {
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val result = orderRepository.deleteOrder(userId, orderId)) {
                is Result.Success -> _events.send(OrderDetailEvent.OrderDeleted)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toOrderUiText())
                }
            }
        }
    }

    private fun archiveOrder() {
        _state.update { it.copy(showArchiveDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val res = orderRepository.archiveOrder(userId, orderId)) {
                is Result.Success -> _events.send(OrderDetailEvent.OrderArchived)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = res.error.toOrderUiText())
                }
            }
        }
    }

    private fun handleStatusTransition(transition: StatusTransition) {
        val order = _state.value.order ?: return
        val needsBalanceWarning = order.balanceRemaining > 0.0 &&
            (transition.toStatus == OrderStatus.READY || transition.toStatus == OrderStatus.DELIVERED)
        if (needsBalanceWarning) {
            _state.update {
                it.copy(
                    showStatusSheet = false,
                    selectedNewStatus = transition.toStatus,
                    selectedNewSubStatus = transition.toSubStatus,
                    showBalanceWarningDialog = true,
                )
            }
            return
        }
        _state.update { it.copy(showStatusSheet = false) }
        performStatusUpdate(transition.toStatus, transition.toSubStatus)
    }

    private fun performStatusUpdate(newStatus: OrderStatus, newSubStatus: OrderSubStatus?) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val statusResult = orderRepository.updateOrderStatus(userId, orderId, newStatus)
            if (statusResult is Result.Error) {
                _state.update { it.copy(errorMessage = statusResult.error.toOrderUiText()) }
                return@launch
            }
            // Always normalise subStatus: only IN_PROGRESS keeps it; other states clear.
            val effectiveSub = if (newStatus == OrderStatus.IN_PROGRESS) newSubStatus else null
            val subResult = orderRepository.updateSubStatus(userId, orderId, effectiveSub)
            if (subResult is Result.Error) {
                _state.update { it.copy(errorMessage = subResult.error.toOrderUiText()) }
            }
        }
    }

    private fun saveNotes() {
        val draft = _state.value.notesDraft
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val toSave = draft.takeIf { it.isNotBlank() }
            when (val res = orderRepository.updateNotes(userId, orderId, toSave)) {
                is Result.Success -> {
                    _state.update { it.copy(isEditingNotes = false, notesDraft = "") }
                    _events.send(OrderDetailEvent.NotesSaved)
                }
                is Result.Error ->
                    _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
            }
        }
    }

    private fun launchWhatsApp() {
        val snapshot = _state.value
        val customer = snapshot.customer?.takeIf { it.phone.isNotBlank() } ?: return
        val order = snapshot.order ?: return
        viewModelScope.launch {
            val message = WhatsAppMessageBuilder.buildForOrder(order, customer)
            _events.send(OrderDetailEvent.LaunchWhatsApp(customer.phone, message))
        }
    }

    private fun launchDialer() {
        val phone = _state.value.customer?.phone ?: return
        if (phone.isBlank()) return
        viewModelScope.launch { _events.send(OrderDetailEvent.LaunchDialer(phone)) }
    }

    private fun capPaymentAmountDigits(digits: String): String {
        val remaining = _state.value.order?.balanceRemaining ?: return digits
        return capPaymentDigits(digits, remaining)
    }

    private fun markPaidInFull() {
        val order = _state.value.order ?: return
        submitPayment(order.balanceRemaining)
    }

    private fun recordPayment() {
        val amount = _state.value.paymentAmountInput.trimStart('0').toDoubleOrNull() ?: return
        submitPayment(amount)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun submitPayment(amountJustPaid: Double) {
        val state = _state.value
        val order = state.order ?: return
        if (amountJustPaid <= 0.0) return
        val now = Clock.System.now().toEpochMilliseconds()
        val payment = Payment(
            id = Uuid.random().toString(),
            amount = amountJustPaid.coerceAtMost(order.balanceRemaining),
            method = state.paymentMethodSelection,
            type = state.paymentTypeSelection,
            recordedAt = now,
            note = null,
        )
        _state.update {
            it.copy(
                showRecordPaymentDialog = false,
                paymentAmountInput = "",
                wasPaymentCapped = false,
            )
        }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val res = orderRepository.recordPayment(userId, orderId, payment)) {
                is Result.Success -> _events.send(OrderDetailEvent.PaymentRecorded)
                is Result.Error ->
                    _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
            }
        }
    }
}

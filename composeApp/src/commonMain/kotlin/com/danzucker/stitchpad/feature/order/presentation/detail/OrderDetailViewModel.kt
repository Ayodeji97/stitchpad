package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import com.danzucker.stitchpad.core.domain.model.ownedStoragePaths
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.ReceiptData
import com.danzucker.stitchpad.core.sharing.ReceiptFormatter
import com.danzucker.stitchpad.core.sharing.toPngBytes
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.domain.toOrderUiText
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayNameAsync
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.receipt_share_error
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Bound on awaitHydrated() in the share path. Long enough for a typical cold-start
// Firestore snapshot to land (single-digit hundreds of ms in practice); short
// enough that a true hydration failure (missing user doc, rules issue, network
// stall) falls back to current() in ~2s rather than appearing to hang silently —
// the share sheet is already closed by the time we wait, so there's no visible
// progress affordance to support a longer wait.
private const val ENTITLEMENTS_HYDRATION_TIMEOUT_MS = 2_000L

// Constructor passes the detekt threshold of 10 by exactly one — Coil's ImageLoader
// and PlatformContext are required for the brand-logo receipt prefetch (PTSP-21).
// A refactor to bundle repositories into a single dependency would obscure the
// per-layer wiring; staying explicit + suppressing here keeps the seams visible.
@Suppress("TooManyFunctions", "LongParameterList")
class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val receiptSharer: OrderReceiptSharer,
    private val imageLoader: ImageLoader,
    private val platformContext: PlatformContext,
    private val entitlementsProvider: EntitlementsProvider,
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private var hasStartedObserving = false
    private var customerJob: Job? = null
    private var loadedCustomerId: String? = null
    private var measurementsJob: Job? = null
    private var loadedMeasurementsCustomerId: String? = null
    private var styleJob: Job? = null
    private var loadedStylesCustomerId: String? = null
    private val _state = MutableStateFlow(OrderDetailState())

    private val _events = Channel<OrderDetailEvent>(Channel.BUFFERED)
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
                    // iOS fires onDismissRequest as a side effect of programmatic
                    // showStatusSheet = false too (per feedback_ios_modal_bottom_sheet_timing).
                    // When handleStatusTransition has already raised the balance warning,
                    // those pending fields MUST stay populated so OnBalanceWarningProceed
                    // can replay the transition. Only clear when no warning is in flight.
                    if (it.showBalanceWarningDialog) {
                        it.copy(showStatusSheet = false)
                    } else {
                        it.copy(
                            showStatusSheet = false,
                            selectedNewStatus = null,
                            selectedNewSubStatus = null,
                        )
                    }
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
                _state.update { it.copy(documentTypeChoice = null) }
            }
            OrderDetailAction.OnShareAsPdfClick -> {
                _state.update { it.copy(showShareSheet = false) }
                shareReceipt { receiptSharer.shareReceiptAsPdf(it) }
                _state.update { it.copy(documentTypeChoice = null) }
            }
            OrderDetailAction.OnDismissShareSheet ->
                _state.update { it.copy(showShareSheet = false, documentTypeChoice = null) }
            is OrderDetailAction.OnDocumentTypeChoice ->
                _state.update { it.copy(documentTypeChoice = action.choice) }
            OrderDetailAction.OnShareReceiptFromSnackbar ->
                _state.update { it.copy(showShareSheet = true, documentTypeChoice = null) }

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
            OrderDetailAction.OnAddStyleClick ->
                _state.update { it.copy(showStylePickerSheet = true) }
            OrderDetailAction.OnAddFabricClick -> {
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToOrderForm(orderId))
                }
            }
            OrderDetailAction.OnAddFabricNameClick -> {
                val currentName = _state.value.order?.items?.firstOrNull()?.fabricName.orEmpty()
                _state.update {
                    it.copy(showFabricNameDialog = true, fabricNameDraft = currentName)
                }
            }
            is OrderDetailAction.OnFabricNameDraftChange ->
                _state.update { it.copy(fabricNameDraft = action.text) }
            OrderDetailAction.OnSaveFabricName -> saveFabricName()
            OrderDetailAction.OnDismissFabricNameDialog ->
                _state.update { it.copy(showFabricNameDialog = false, fabricNameDraft = "") }
            OrderDetailAction.OnAddPhoneClick -> {
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToCustomerForm(customerId))
                }
            }

            // Styles
            is OrderDetailAction.OnSelectStyle -> linkExistingStyle(action.styleId)

            OrderDetailAction.OnCreateNewStyleClick -> {
                _state.update { it.copy(showStylePickerSheet = false) }
                val customerId = _state.value.order?.customerId ?: return
                viewModelScope.launch {
                    _events.send(OrderDetailEvent.NavigateToStyleForm(customerId, orderId))
                }
            }

            OrderDetailAction.OnDismissStylePickerSheet ->
                _state.update { it.copy(showStylePickerSheet = false) }

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

            // Deadline
            OrderDetailAction.OnSetDeadlineClick ->
                _state.update { it.copy(showDatePickerDialog = true) }

            is OrderDetailAction.OnDeadlineSelected -> setDeadline(action.epochMillis)

            OrderDetailAction.OnDismissDatePickerDialog ->
                _state.update { it.copy(showDatePickerDialog = false) }

            // Misc
            OrderDetailAction.OnErrorDismiss ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun saveFabricName() {
        val snapshot = _state.value
        val order = snapshot.order
        val firstItem = order?.items?.firstOrNull()
        val newName = snapshot.fabricNameDraft.trim().ifBlank { null }
        _state.update { it.copy(showFabricNameDialog = false, fabricNameDraft = "") }
        if (order == null || firstItem == null || firstItem.fabricName == newName) return
        val updatedItems = listOf(firstItem.copy(fabricName = newName)) + order.items.drop(1)
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (val res = orderRepository.updateOrder(userId, order.copy(items = updatedItems))) {
                is Result.Success -> Unit
                is Result.Error -> _state.update {
                    it.copy(errorMessage = res.error.toOrderUiText())
                }
            }
        }
    }

    private fun setDeadline(epochMillis: Long) {
        _state.update { it.copy(showDatePickerDialog = false) }
        val order = _state.value.order ?: return
        if (order.deadline == epochMillis) return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val updated = order.copy(deadline = epochMillis)
            when (val res = orderRepository.updateOrder(userId, updated)) {
                is Result.Success -> Unit
                is Result.Error -> _state.update {
                    it.copy(errorMessage = res.error.toOrderUiText())
                }
            }
        }
    }

    private fun shareReceipt(share: suspend (ReceiptData) -> Unit) {
        val order = _state.value.order ?: return
        val user = _state.value.user ?: return
        val choice = _state.value.documentTypeChoice
        viewModelScope.launch {
            try {
                val garmentNames = order.items
                    .map { it.garmentType }
                    .distinct()
                    .associate { it to garmentDisplayNameAsync(it) }
                val logoBytes = fetchLogoBytes(user.businessLogoUrl)
                // Wait briefly for entitlements to hydrate so a Pro/Atelier user
                // opening the app and immediately sharing doesn't get the StitchPad
                // watermark while the snapshot is still racing. Bounded by a short
                // timeout because awaitHydrated() never resolves when the user doc
                // is missing or Firestore can't produce a snapshot (rules issue,
                // long network stall, fresh-signup race) — the share sheet has
                // already closed by this point and there's no loading affordance,
                // so an unbounded wait would look like a silent hang to the user.
                // On timeout we fall back to current() which defaults to FREE +
                // StitchPad watermark — degraded for a paid user, never broken.
                val tier = withTimeoutOrNull(ENTITLEMENTS_HYDRATION_TIMEOUT_MS) {
                    entitlementsProvider.awaitHydrated()
                }?.tier ?: entitlementsProvider.current().tier
                val receiptData = ReceiptFormatter.format(
                    order = order,
                    user = user,
                    tier = tier,
                    garmentNames = garmentNames,
                    businessLogoBytes = logoBytes,
                    forceDocumentType = choice,
                )
                share(receiptData)
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                _state.update {
                    it.copy(errorMessage = UiText.StringResourceText(Res.string.receipt_share_error))
                }
            }
        }
    }

    /**
     * Prefetch the user's brand logo via Coil and encode it as PNG bytes.
     * Returns null when no URL is set or decoding fails.
     * The bytes are passed through [ReceiptData] so receipt renderers can draw
     * them synchronously on a Canvas/CGContext without suspending.
     */
    @Suppress("ReturnCount")
    private suspend fun fetchLogoBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        val request = ImageRequest.Builder(platformContext)
            .data(url)
            .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        return result.image.toPngBytes()
    }

    private fun loadUser() {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser() ?: return@launch
            // Resolve the Firestore user doc — the Auth user has businessName,
            // whatsappNumber, phoneNumber, and businessLogoUrl hardcoded to null, so
            // without this read the receipt header would fall back to the generic
            // business name and never show the uploaded logo. Merge identity (email,
            // displayName) from Auth when the Firestore doc lacks them (the user doc
            // doesn't redundantly store those fields).
            val firestoreUser = userRepository.observeUser(authUser.id).first()
            val user = firestoreUser?.copy(
                email = firestoreUser.email.ifBlank { authUser.email },
                displayName = firestoreUser.displayName.ifBlank { authUser.displayName },
            ) ?: authUser
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
                        // Re-derive linked style + measurement from already-loaded
                        // collection lists when the order's items[0].styleId or
                        // .measurementId changes (e.g., from a picker auto-link).
                        // Without this, only the *collection* flows update those
                        // fields, so picking an existing style/measurement leaves
                        // state.style or state.measurement stale.
                        _state.update { current ->
                            val newOrder = result.data
                            val firstItem = newOrder.items.firstOrNull()
                            val linkedMeasurement = firstItem?.measurementId?.let { id ->
                                current.availableMeasurements.firstOrNull { it.id == id }
                                    ?: current.measurement?.takeIf { it.id == id }
                            }
                            current.copy(
                                order = newOrder,
                                isLoading = false,
                                measurement = linkedMeasurement,
                            )
                        }
                        loadCustomerIfNeeded(result.data.customerId, userId)
                        loadMeasurementsIfNeeded(result.data.customerId, userId)
                        loadStylesIfNeeded(result.data.customerId, userId)
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
        if (loadedCustomerId == customerId) return
        loadedCustomerId = customerId
        customerJob?.cancel()
        customerJob = viewModelScope.launch {
            // Observe (not getCustomer) so phone/name edits made via the
            // CustomerForm flight propagate back to the order detail without
            // requiring a screen recreation. The empty-phone CTA depends on
            // this — without observe, the new phone is invisible until the
            // VM is destroyed and rebuilt.
            customerRepository.observeCustomer(userId, customerId).collect { res ->
                if (res is Result.Success) {
                    _state.update { it.copy(customer = res.data) }
                }
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

    private fun loadStylesIfNeeded(customerId: String, userId: String) {
        if (loadedStylesCustomerId == customerId) return
        loadedStylesCustomerId = customerId
        styleJob?.cancel()
        styleJob = viewModelScope.launch {
            // Observe the customer's full style list and build a lookup map. The
            // hero image resolver in OrderDetailScreen resolves the relevant styles
            // per styleImages[].styleId at render time. Cheaper than per-style
            // subscriptions; the gallery list is small for any tailor.
            styleRepository.observeStyles(userId, customerId).collect { res ->
                if (res is Result.Success) {
                    _state.update { current ->
                        current.copy(
                            availableStyles = res.data,
                            styles = res.data.associateBy { it.id },
                        )
                    }
                }
            }
        }
    }

    private fun linkExistingStyle(styleId: String) {
        _state.update { it.copy(showStylePickerSheet = false) }
        val order = _state.value.order ?: return
        val firstItem = order.items.firstOrNull() ?: return
        // PTSP-11 — APPEND a LIBRARY ref to the first item's styleImages list.
        // Guard against duplicates and the 3-image cap.
        val alreadyHas = firstItem.styleImages.any {
            it.source == StyleImageSource.LIBRARY && it.styleId == styleId
        }
        val atCap = firstItem.styleImages.size >= 3
        if (!alreadyHas && !atCap) {
            val newRef = StyleImageRef(
                source = StyleImageSource.LIBRARY,
                styleId = styleId,
            )
            val updatedItem = firstItem.copy(styleImages = firstItem.styleImages + newRef)
            val updatedItems = listOf(updatedItem) + order.items.drop(1)
            viewModelScope.launch {
                val userId = authRepository.getCurrentUser()?.id ?: return@launch
                when (val res = orderRepository.updateOrder(userId, order.copy(items = updatedItems))) {
                    is Result.Success -> Unit
                    is Result.Error -> _state.update {
                        it.copy(errorMessage = res.error.toOrderUiText())
                    }
                }
            }
        }
    }

    private fun deleteOrder() {
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            when (
                val result = orderRepository.deleteOrder(
                    userId = userId,
                    orderId = orderId,
                    ownedStoragePaths = _state.value.order?.ownedStoragePaths().orEmpty(),
                )
            ) {
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
        // capPaymentDigits intentionally echoes user input when balance is 0.0
        // (so the field stays editable visually), so we must guard AFTER
        // coercing — otherwise a 0-balance order accepts a phantom payment.
        val safeAmount = amountJustPaid.coerceAtMost(order.balanceRemaining)
        if (safeAmount <= 0.0) return
        val now = Clock.System.now().toEpochMilliseconds()
        val payment = Payment(
            id = Uuid.random().toString(),
            amount = safeAmount,
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
            when (
                val res = orderRepository.recordPayment(
                    userId = userId,
                    orderId = orderId,
                    payment = payment,
                    knownPayments = order.payments,
                )
            ) {
                is Result.Success -> {
                    // Optimistically reflect the new payment in local state BEFORE emitting
                    // PaymentRecorded. Otherwise the snackbar's "Share receipt" action can
                    // race the observeOrder snapshot and format a receipt against a stale
                    // order — wrong doc type (INVOICE instead of DEPOSIT_RECEIPT) or
                    // missing the just-recorded payment row. observeOrder will overwrite
                    // with the server-authoritative version once the snapshot lands; the
                    // optimistic value will match by then.
                    _state.update { current ->
                        val existing = current.order ?: return@update current
                        current.copy(order = existing.copy(payments = existing.payments + payment))
                    }
                    _events.send(OrderDetailEvent.PaymentRecorded)
                }
                is Result.Error ->
                    _state.update { it.copy(errorMessage = res.error.toOrderUiText()) }
            }
        }
    }
}

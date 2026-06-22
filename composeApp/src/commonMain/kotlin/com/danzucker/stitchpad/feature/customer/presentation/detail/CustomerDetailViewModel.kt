package com.danzucker.stitchpad.feature.customer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import com.danzucker.stitchpad.feature.measurement.presentation.toMeasurementUiText
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
import stitchpad.composeapp.generated.resources.customer_delete_orders_load_failed
import stitchpad.composeapp.generated.resources.customer_delete_pending_orders_load

class CustomerDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]

    /** Count of this customer's non-delivered orders, maintained by [observeOrders]. */
    private var activeOrderCount: Int = 0

    /** Set when a delete is in flight so [observeCustomer] ignores the doc's NOT_FOUND. */
    private var isDeletingCustomer: Boolean = false

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(CustomerDetailState())

    private val _events = Channel<CustomerDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId == null) {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(CustomerDetailEvent.NavigateBack)
                    return@onStart
                }
                loadData()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CustomerDetailState()
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: CustomerDetailAction) {
        when (action) {
            CustomerDetailAction.OnEditCustomerClick -> {
                withCustomerId {
                    viewModelScope.launch {
                        _events.send(CustomerDetailEvent.NavigateToEditCustomer(it))
                    }
                }
            }
            CustomerDetailAction.OnAddMeasurementClick -> {
                // Existing measurements → offer edit-vs-create so tailors stop making
                // duplicates. Empty list → straight to a blank new form (no needless sheet).
                if (_state.value.measurements.isNotEmpty()) {
                    _state.update { it.copy(showAddMeasurementSheet = true) }
                } else {
                    withCustomerId {
                        viewModelScope.launch {
                            _events.send(CustomerDetailEvent.NavigateToAddMeasurement(it))
                        }
                    }
                }
            }
            CustomerDetailAction.OnCreateNewMeasurementClick -> {
                _state.update { it.copy(showAddMeasurementSheet = false) }
                withCustomerId {
                    viewModelScope.launch {
                        _events.send(CustomerDetailEvent.NavigateToAddMeasurement(it))
                    }
                }
            }
            CustomerDetailAction.OnDismissAddMeasurementSheet -> {
                _state.update { it.copy(showAddMeasurementSheet = false) }
            }
            is CustomerDetailAction.OnMeasurementClick -> {
                _state.update { it.copy(showAddMeasurementSheet = false) }
                withCustomerId {
                    viewModelScope.launch {
                        _events.send(CustomerDetailEvent.NavigateToEditMeasurement(it, action.measurement.id))
                    }
                }
            }
            is CustomerDetailAction.OnDeleteMeasurementClick -> {
                _state.update { it.copy(showDeleteDialog = true, measurementToDelete = action.measurement) }
            }
            CustomerDetailAction.OnConfirmDelete -> deleteMeasurement()
            CustomerDetailAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, measurementToDelete = null) }
            }
            CustomerDetailAction.OnOverflowClick -> {
                _state.update { it.copy(showOverflowMenu = true) }
            }
            CustomerDetailAction.OnDismissOverflow -> {
                _state.update { it.copy(showOverflowMenu = false) }
            }
            CustomerDetailAction.OnDeleteCustomerClick -> {
                // The dialog only renders when customer != null, so don't arm it
                // before the customer has loaded — otherwise the flag sticks true
                // with no visible dialog and no dismiss path (Bugbot #147). Just
                // close the menu in that case.
                _state.update {
                    it.copy(
                        showOverflowMenu = false,
                        showDeleteCustomerDialog = it.customer != null,
                        customerDeleteActiveOrderCount = activeOrderCount,
                    )
                }
            }
            CustomerDetailAction.OnConfirmDeleteCustomer -> deleteCustomer()
            CustomerDetailAction.OnDismissDeleteCustomerDialog -> {
                _state.update {
                    it.copy(showDeleteCustomerDialog = false, customerDeleteActiveOrderCount = 0)
                }
            }
            CustomerDetailAction.OnMessageWhatsAppClick -> messageOnWhatsApp()
            CustomerDetailAction.OnCallClick -> {
                val phone = _state.value.customer?.phone?.takeIf { it.isNotBlank() } ?: return
                viewModelScope.launch { _events.send(CustomerDetailEvent.LaunchDialer(phone)) }
            }
            CustomerDetailAction.OnViewStylesClick -> {
                withCustomerId {
                    viewModelScope.launch {
                        _events.send(CustomerDetailEvent.NavigateToStyleGallery(it))
                    }
                }
            }
            CustomerDetailAction.OnUpgradeClick -> {
                viewModelScope.launch { _events.send(CustomerDetailEvent.NavigateToUpgrade) }
            }
            CustomerDetailAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(CustomerDetailEvent.NavigateBack) }
            }
            CustomerDetailAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    // PTSP-33: build a generic greeting and launch WhatsApp. No sheet to dismiss
    // here (chips live in the screen body), so no UIKit-timing delay is needed.
    private fun messageOnWhatsApp() {
        val customer = _state.value.customer ?: return
        if (customer.phone.isBlank()) return
        viewModelScope.launch {
            val message = WhatsAppMessageBuilder.buildForCustomer(customer)
            _events.send(CustomerDetailEvent.LaunchWhatsApp(customer.phone, message))
        }
    }

    private fun withCustomerId(block: (String) -> Unit) {
        val customerId = customerId ?: return
        block(customerId)
    }

    private fun loadData() {
        val customerId = customerId ?: return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            launch { observeCustomer(userId, customerId) }
            launch { observeCustomFieldLabels(userId) }
            launch { observeOrders(userId, customerId) }
            observeMeasurements(userId, customerId)
        }
    }

    // PTSP-12: keep a UUID → label map of the tailor's custom fields so the
    // measurement preview row can render custom-keyed values with their human
    // labels. Includes archived fields — past measurements with recorded
    // values still need labels per the "we never delete your data" promise.
    // Errors are silent: the preview just falls back to the marquee row.
    private suspend fun observeCustomFieldLabels(userId: String) {
        customFieldRepository.observeFields(userId).collect { result ->
            if (result is Result.Success) {
                val labels = result.data.associate { it.id to it.label }
                _state.update { it.copy(customFieldLabels = labels) }
            }
        }
    }

    // Observes the customer doc rather than one-shot fetching it so the screen
    // reflects live slotState flips. Without this, a customer locked by the
    // server's reconcile while the detail screen is open keeps rendering the
    // editable (active) UI until the user navigates away and back.
    private suspend fun observeCustomer(userId: String, customerId: String) {
        customerRepository.observeCustomer(userId, customerId).collect { result ->
            when (result) {
                is Result.Success -> _state.update { it.copy(customer = result.data, isLoading = false) }
                // Once we've deleted the customer, the observed doc legitimately
                // disappears and emits NOT_FOUND — that's expected, not an error to
                // surface (we're already navigating back). Suppress it (Bugbot #147).
                is Result.Error -> if (!isDeletingCustomer) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toCustomerUiText())
                    }
                }
            }
        }
    }

    private suspend fun observeMeasurements(userId: String, customerId: String) {
        measurementRepository.observeMeasurements(userId, customerId).collect { result ->
            when (result) {
                is Result.Success -> _state.update { it.copy(measurements = result.data, isLoading = false) }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.toMeasurementUiText())
                }
            }
        }
    }

    // Mirrors CustomerListViewModel.observeOrders, scoped to this one customer.
    // deleteCustomer is a single-doc delete with no cascade, so we must know the
    // active-order count before allowing it — otherwise we'd orphan live orders.
    private suspend fun observeOrders(userId: String, customerId: String) {
        orderRepository.observeOrders(userId).collect { result ->
            when (result) {
                is Result.Success -> {
                    activeOrderCount = result.data.count {
                        it.customerId == customerId && it.status != OrderStatus.DELIVERED
                    }
                    _state.update { it.copy(ordersLoaded = true, ordersLoadFailed = false) }
                }
                is Result.Error -> _state.update { it.copy(ordersLoadFailed = true) }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun deleteCustomer() {
        val customerId = customerId ?: return
        val current = _state.value

        // Race guard #1: no trustworthy order count yet — refuse rather than risk
        // orphaning non-delivered orders on a stale empty count.
        if (!current.ordersLoaded) {
            val message = if (current.ordersLoadFailed) {
                Res.string.customer_delete_orders_load_failed
            } else {
                Res.string.customer_delete_pending_orders_load
            }
            _state.update { it.copy(errorMessage = UiText.StringResourceText(message)) }
            return
        }

        // Race guard #2: count may have changed since the dialog opened — morph
        // into the "blocked" variant by writing the live count back into state.
        if (activeOrderCount > 0) {
            _state.update { it.copy(customerDeleteActiveOrderCount = activeOrderCount) }
            return
        }

        _state.update {
            it.copy(showDeleteCustomerDialog = false, customerDeleteActiveOrderCount = 0)
        }
        // Tell observeCustomer to ignore the impending NOT_FOUND from the doc we're
        // about to delete, so a stale "customer not found" snackbar doesn't flash.
        isDeletingCustomer = true
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id
            if (userId == null) {
                // No signed-in user — nothing was deleted, so re-arm the observer
                // rather than leaving it permanently swallowing errors (Bugbot #147).
                isDeletingCustomer = false
                return@launch
            }
            when (val result = customerRepository.deleteCustomer(userId, customerId)) {
                is Result.Error -> {
                    // Delete failed — re-arm the observer and surface the error.
                    isDeletingCustomer = false
                    _state.update { it.copy(errorMessage = result.error.toCustomerUiText()) }
                }
                is Result.Success ->
                    // Customer doc is gone — leave the now-empty detail screen.
                    _events.send(CustomerDetailEvent.NavigateBack)
            }
        }
    }

    private fun deleteMeasurement() {
        val customerId = customerId ?: return
        val measurement = _state.value.measurementToDelete ?: return
        _state.update { it.copy(showDeleteDialog = false, measurementToDelete = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = measurementRepository.deleteMeasurement(userId, customerId, measurement.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toMeasurementUiText()) }
            }
        }
    }
}

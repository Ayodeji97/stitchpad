package com.danzucker.stitchpad.feature.customer.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
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

class CustomerDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(CustomerDetailState())

    private val _events = Channel<CustomerDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadData()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CustomerDetailState()
        )

    fun onAction(action: CustomerDetailAction) {
        when (action) {
            CustomerDetailAction.OnEditCustomerClick -> {
                viewModelScope.launch { _events.send(CustomerDetailEvent.NavigateToEditCustomer(customerId)) }
            }
            CustomerDetailAction.OnAddMeasurementClick -> {
                viewModelScope.launch { _events.send(CustomerDetailEvent.NavigateToAddMeasurement(customerId)) }
            }
            is CustomerDetailAction.OnMeasurementClick -> {
                viewModelScope.launch {
                    _events.send(CustomerDetailEvent.NavigateToEditMeasurement(customerId, action.measurement.id))
                }
            }
            is CustomerDetailAction.OnDeleteMeasurementClick -> {
                _state.update { it.copy(showDeleteDialog = true, measurementToDelete = action.measurement) }
            }
            CustomerDetailAction.OnConfirmDelete -> deleteMeasurement()
            CustomerDetailAction.OnDismissDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false, measurementToDelete = null) }
            }
            CustomerDetailAction.OnViewStylesClick -> {
                viewModelScope.launch { _events.send(CustomerDetailEvent.NavigateToStyleGallery(customerId)) }
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

    private fun loadData() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            launch { observeCustomer(userId) }
            launch { observeCustomFieldLabels(userId) }
            observeMeasurements(userId)
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
    private suspend fun observeCustomer(userId: String) {
        customerRepository.observeCustomer(userId, customerId).collect { result ->
            when (result) {
                is Result.Success -> _state.update { it.copy(customer = result.data, isLoading = false) }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.toCustomerUiText())
                }
            }
        }
    }

    private suspend fun observeMeasurements(userId: String) {
        measurementRepository.observeMeasurements(userId, customerId).collect { result ->
            when (result) {
                is Result.Success -> _state.update { it.copy(measurements = result.data, isLoading = false) }
                is Result.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.toMeasurementUiText())
                }
            }
        }
    }

    private fun deleteMeasurement() {
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

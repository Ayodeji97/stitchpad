package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.measurement.presentation.toMeasurementUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MeasurementDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val customerId: String? = savedStateHandle["customerId"]
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val source: String = savedStateHandle["source"] ?: MeasurementDetailSource.CUSTOMER_DETAIL
    private val fromSave: Boolean = savedStateHandle["fromSave"] ?: false

    private var hasLoadedInitialData = false

    // Set when THIS screen initiates the exit (delete, missing measurement) so
    // the measurement observer doesn't double-fire NavigateBack.
    private var navigatedAway = false

    // True once the observer has delivered the measurement at least once.
    // Arriving from a save, the create/update is enqueued on the app-lifetime
    // OfflineWriteDispatcher scope, so the first snapshot can predate the local
    // cache applying it — an initial miss must be waited out, not read as
    // "deleted elsewhere".
    private var hasSeenMeasurement = false

    private val _state = MutableStateFlow(MeasurementDetailState(showSavedMessage = fromSave))
    private val _events = Channel<MeasurementDetailEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId == null || measurementId == null) {
                    _events.send(MeasurementDetailEvent.NavigateBack)
                    return@onStart
                }
                analytics.logEvent(AnalyticsEvent.MeasurementDetailViewed(source))
                observeMeasurement(customerId, measurementId)
                observeCustomFieldLabels()
                observeLockState(customerId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementDetailState(showSavedMessage = fromSave),
        )

    fun onAction(action: MeasurementDetailAction) {
        when (action) {
            MeasurementDetailAction.OnEditClick -> onEditClick()
            MeasurementDetailAction.OnRenameClick -> onRenameClick()
            is MeasurementDetailAction.OnRenameDraftChange ->
                _state.update { it.copy(renameDraft = action.name) }
            MeasurementDetailAction.OnConfirmRename -> renameMeasurement()
            MeasurementDetailAction.OnDismissRenameDialog ->
                _state.update { it.copy(renameDraft = null) }
            MeasurementDetailAction.OnDeleteClick -> onDeleteClick()
            MeasurementDetailAction.OnConfirmDelete -> deleteMeasurement()
            MeasurementDetailAction.OnDismissDeleteDialog ->
                _state.update { it.copy(showDeleteDialog = false) }
            MeasurementDetailAction.OnSavedMessageShown ->
                _state.update { it.copy(showSavedMessage = false) }
            MeasurementDetailAction.OnNavigateBack ->
                viewModelScope.launch { _events.send(MeasurementDetailEvent.NavigateBack) }
            MeasurementDetailAction.OnErrorDismiss ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun onEditClick() = requireUnlocked {
        val customerId = customerId ?: return@requireUnlocked
        val measurementId = measurementId ?: return@requireUnlocked
        viewModelScope.launch {
            _events.send(MeasurementDetailEvent.NavigateToEdit(customerId, measurementId))
        }
    }

    private fun onRenameClick() = requireUnlocked {
        _state.update { it.copy(renameDraft = it.measurement?.name ?: "") }
    }

    private fun onDeleteClick() = requireUnlocked {
        _state.update { it.copy(showDeleteDialog = true) }
    }

    /**
     * Gated actions on a locked (over-cap) customer route to the upgrade screen
     * instead. While the lock state is still unknown (customer doc not yet
     * emitted) taps are ignored — failing closed beats briefly letting a locked
     * customer into the edit path or wrongly bouncing an active one to Upgrade.
     */
    private inline fun requireUnlocked(block: () -> Unit) {
        when (_state.value.isLocked) {
            null -> Unit
            true -> viewModelScope.launch { _events.send(MeasurementDetailEvent.NavigateToUpgrade) }
            false -> block()
        }
    }

    private fun observeMeasurement(customerId: String, measurementId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            measurementRepository.observeMeasurements(userId, customerId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val measurement = result.data.find { it.id == measurementId }
                        when {
                            measurement != null -> {
                                hasSeenMeasurement = true
                                _state.update { it.copy(measurement = measurement, isLoading = false) }
                            }
                            !navigatedAway && (hasSeenMeasurement || !fromSave) -> {
                                // Deleted elsewhere (another device / another screen) — leave.
                                navigatedAway = true
                                _events.send(MeasurementDetailEvent.NavigateBack)
                            }
                            // else: fromSave and never seen — the enqueued write hasn't
                            // reached the local cache yet; wait for the next snapshot.
                        }
                    }
                    is Result.Error -> _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toMeasurementUiText())
                    }
                }
            }
        }
    }

    private fun observeCustomFieldLabels() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customFieldRepository.observeFields(userId).collect { result ->
                if (result is Result.Success) {
                    // ALL definitions, archived included — recorded values on old
                    // measurements must keep their labels after archive.
                    _state.update { current ->
                        current.copy(customFieldLabels = result.data.associate { it.id to it.label })
                    }
                }
                // Errors are non-fatal: custom rows fall back to being skipped.
            }
        }
    }

    private fun observeLockState(customerId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customerRepository.observeCustomer(userId, customerId).collect { result ->
                if (result is Result.Success) {
                    _state.update {
                        it.copy(isLocked = result.data.slotState == CustomerSlotState.LOCKED)
                    }
                }
                // On error keep the last known lock state — read-only content stays visible.
            }
        }
    }

    private fun renameMeasurement() {
        val customerId = customerId
        val measurement = _state.value.measurement
        val newName = _state.value.renameDraft?.trim().orEmpty()
        if (customerId == null || measurement == null || newName.isBlank()) return
        _state.update { it.copy(renameDraft = null) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = measurementRepository.updateMeasurement(
                userId,
                customerId,
                measurement.copy(name = newName),
            )
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toMeasurementUiText()) }
            }
        }
    }

    private fun deleteMeasurement() {
        val customerId = customerId ?: return
        val measurement = _state.value.measurement ?: return
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            navigatedAway = true
            // deleteMeasurement only schedules the write on the app-lifetime
            // OfflineWriteDispatcher scope and returns — it never awaits the
            // server ACK. Awaiting it here is therefore offline-safe AND
            // guarantees the delete is enqueued before navigation clears this
            // ViewModel's scope.
            val result = measurementRepository.deleteMeasurement(userId, customerId, measurement.id)
            if (result is Result.Error) {
                // Scheduling itself failed — nothing was enqueued; stay on screen.
                navigatedAway = false
                _state.update { it.copy(errorMessage = result.error.toMeasurementUiText()) }
                return@launch
            }
            _events.send(MeasurementDetailEvent.NavigateBack)
        }
    }
}

package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.measurement.presentation.toMeasurementUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MeasurementFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(MeasurementFormState(isEditMode = measurementId != null))

    private val _events = Channel<MeasurementFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                val unit = measurementPreferencesStore.getUnit()
                _state.update { it.copy(unit = unit) }
                if (measurementId != null) {
                    loadMeasurement(measurementId)
                } else {
                    onGenderChange(CustomerGender.FEMALE)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementFormState(isEditMode = measurementId != null)
        )

    fun onAction(action: MeasurementFormAction) {
        when (action) {
            is MeasurementFormAction.OnGenderChange -> onGenderChange(action.gender)
            is MeasurementFormAction.OnSectionChange -> {
                _state.update { it.copy(currentSectionIndex = action.index, isCurrentSectionExpanded = true) }
            }
            MeasurementFormAction.OnNextSection -> {
                _state.update { s ->
                    val next = (s.currentSectionIndex + 1).coerceAtMost(s.sections.size - 1)
                    s.copy(currentSectionIndex = next, isCurrentSectionExpanded = true)
                }
            }
            MeasurementFormAction.OnPreviousSection -> {
                _state.update { s ->
                    val prev = (s.currentSectionIndex - 1).coerceAtLeast(0)
                    s.copy(currentSectionIndex = prev, isCurrentSectionExpanded = true)
                }
            }
            MeasurementFormAction.OnToggleShowMore -> {
                _state.update { it.copy(isCurrentSectionExpanded = !it.isCurrentSectionExpanded) }
            }
            MeasurementFormAction.OnToggleNotes -> {
                _state.update { it.copy(isNotesExpanded = !it.isNotesExpanded) }
            }
            is MeasurementFormAction.OnFieldChange -> {
                _state.update { it.copy(fields = it.fields + (action.key to action.value)) }
            }
            is MeasurementFormAction.OnNotesChange -> {
                _state.update { it.copy(notes = action.notes) }
            }
            MeasurementFormAction.OnSaveClick -> save()
            MeasurementFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateBack) }
            }
            MeasurementFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun onGenderChange(gender: CustomerGender) {
        val sections = BodyProfileTemplate.sectionsFor(gender)
        val allKeys = sections.flatMap { it.fields }.map { it.key }
        _state.update {
            it.copy(
                gender = gender,
                sections = sections,
                currentSectionIndex = 0,
                isCurrentSectionExpanded = true,
                fields = allKeys.associateWith { "" }
            )
        }
    }

    private fun loadMeasurement(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            when (val result = measurementRepository.observeMeasurements(userId, customerId).first()) {
                is Result.Success -> {
                    val measurement = result.data.find { it.id == id }
                    if (measurement != null) {
                        val sections = BodyProfileTemplate.sectionsFor(measurement.gender)
                        val allKeys = sections.flatMap { it.fields }.map { it.key }
                        val fieldsAsString = allKeys.associateWith { key ->
                            val v = measurement.fields[key]
                            if (v != null) {
                                if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                            } else {
                                ""
                            }
                        }
                        _state.update {
                            it.copy(
                                gender = measurement.gender,
                                sections = sections,
                                fields = fieldsAsString,
                                unit = measurement.unit,
                                notes = measurement.notes ?: "",
                                originalCreatedAt = measurement.createdAt,
                                originalDateTaken = measurement.dateTaken,
                                isLoading = false
                            )
                        }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.toMeasurementUiText()
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val s = _state.value
            val gender = s.gender ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val parsedFields = s.fields
                .mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                .filter { it.value > 0.0 }

            // Pre-generate the id for create flow so we can link it to the order
            // before observeOrder re-emits. For edit flow we keep the existing id.
            val isCreate = measurementId == null
            val effectiveId = measurementId ?: Uuid.random().toString()

            val measurement = Measurement(
                id = effectiveId,
                customerId = customerId,
                gender = gender,
                fields = parsedFields,
                unit = s.unit,
                notes = s.notes.trim().ifBlank { null },
                dateTaken = s.originalDateTaken,
                createdAt = s.originalCreatedAt
            )
            val saveResult = if (isCreate) {
                measurementRepository.createMeasurement(userId, customerId, measurement)
            } else {
                measurementRepository.updateMeasurement(userId, customerId, measurement)
            }
            if (saveResult is Result.Error) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = saveResult.error.toMeasurementUiText())
                }
                return@launch
            }

            // If the form was opened from an order's "link measurement" picker,
            // attach this measurement id to the order's first item. Failure to link
            // is logged via the order error path but does NOT block the save —
            // the measurement is already persisted; the user can retry the link
            // from the order details screen.
            val linkOrderId = linkToOrderId
            if (isCreate && linkOrderId != null) {
                when (val orderResult = orderRepository.getOrder(userId, linkOrderId)) {
                    is Result.Success -> {
                        val order = orderResult.data
                        val firstItem = order.items.firstOrNull()
                        if (firstItem != null) {
                            val updatedItems = listOf(firstItem.copy(measurementId = effectiveId)) +
                                order.items.drop(1)
                            orderRepository.updateOrder(userId, order.copy(items = updatedItems))
                            // Ignore failure — order's observeOrder Flow re-emits when network
                            // recovers; user sees the unlinked state and can retry. We deliberately
                            // do not surface a separate error toast here, since the measurement save
                            // itself succeeded and that's the primary user intent.
                        }
                    }
                    is Result.Error -> Unit // same rationale as above
                }
            }

            _state.update { it.copy(isLoading = false) }
            _events.send(MeasurementFormEvent.NavigateBack)
        }
    }
}

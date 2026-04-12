package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.measurement.presentation.toMeasurementUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MeasurementFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val measurementId: String? = savedStateHandle["measurementId"]

    private val _state = MutableStateFlow(MeasurementFormState(isEditMode = measurementId != null))
    val state = _state.asStateFlow()

    private val _events = Channel<MeasurementFormEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val unit = measurementPreferencesStore.getUnit()
            _state.update { it.copy(unit = unit) }
            if (measurementId != null) {
                loadMeasurement(measurementId)
            } else {
                val defaultType = _state.value.garmentType
                _state.update { it.copy(fields = defaultType.fieldLabels.associateWith { "" }) }
            }
        }
    }

    fun onAction(action: MeasurementFormAction) {
        when (action) {
            is MeasurementFormAction.OnGarmentTypeChange -> onGarmentTypeChange(action.type)
            is MeasurementFormAction.OnFieldChange -> {
                _state.update { it.copy(fields = it.fields + (action.label to action.value)) }
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

    private fun onGarmentTypeChange(type: GarmentType) {
        _state.update {
            it.copy(
                garmentType = type,
                fields = type.fieldLabels.associateWith { "" }
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
            measurementRepository.observeMeasurements(userId, customerId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val measurement = result.data.find { it.id == id }
                        if (measurement != null) {
                            val fieldsAsString = measurement.garmentType.fieldLabels.associateWith { label ->
                                val v = measurement.fields[label]
                                if (v != null) {
                                    if (v == v.toLong().toDouble()) {
                                        v.toLong().toString()
                                    } else {
                                        v.toString()
                                    }
                                } else {
                                    ""
                                }
                            }
                            _state.update {
                                it.copy(
                                    garmentType = measurement.garmentType,
                                    fields = fieldsAsString,
                                    unit = measurement.unit,
                                    notes = measurement.notes ?: "",
                                    isLoading = false
                                )
                            }
                        } else {
                            _state.update { it.copy(isLoading = false) }
                        }
                        return@collect
                    }
                    is Result.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.error.toMeasurementUiText()
                            )
                        }
                        return@collect
                    }
                }
            }
        }
    }

    private fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val s = _state.value
            val parsedFields = s.fields
                .mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                .filter { it.value > 0.0 }

            val measurement = Measurement(
                id = measurementId ?: "",
                customerId = customerId,
                garmentType = s.garmentType,
                fields = parsedFields,
                unit = s.unit,
                notes = s.notes.trim().ifBlank { null },
                dateTaken = 0L,
                createdAt = 0L
            )
            val result = if (measurementId != null) {
                measurementRepository.updateMeasurement(userId, customerId, measurement)
            } else {
                measurementRepository.createMeasurement(userId, customerId, measurement)
            }
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> _events.send(MeasurementFormEvent.NavigateBack)
                is Result.Error -> _state.update {
                    it.copy(errorMessage = result.error.toMeasurementUiText())
                }
            }
        }
    }
}

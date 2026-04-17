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

class MeasurementFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val measurementId: String? = savedStateHandle["measurementId"]

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

            val measurement = Measurement(
                id = measurementId ?: "",
                customerId = customerId,
                gender = gender,
                fields = parsedFields,
                unit = s.unit,
                notes = s.notes.trim().ifBlank { null },
                dateTaken = s.originalDateTaken,
                createdAt = s.originalCreatedAt
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

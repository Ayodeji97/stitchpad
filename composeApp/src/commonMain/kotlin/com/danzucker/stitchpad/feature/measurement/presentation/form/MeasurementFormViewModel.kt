package com.danzucker.stitchpad.feature.measurement.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
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
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MeasurementFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
    private val orderRepository: OrderRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
    private val entitlements: EntitlementsProvider,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]

    private var hasLoadedInitialData = false

    // Unfiltered cache of all custom fields the tailor has defined. `state.customFields`
    // holds the gender-filtered subset; switching gender re-filters from THIS list
    // (not from the already-filtered state, which would lose other-gender fields).
    private var allCustomFields: List<CustomMeasurementField> = emptyList()

    private val _state = MutableStateFlow(MeasurementFormState(isEditMode = measurementId != null))

    private val _events = Channel<MeasurementFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                val unit = measurementPreferencesStore.getUnit()
                val ents = entitlements.current()
                _state.update {
                    it.copy(
                        unit = unit,
                        canUseCustomMeasurements = ents.canUseCustomMeasurements,
                        isInWelcomeWindow = ents.isInWelcomeWindow,
                    )
                }
                if (measurementId != null) {
                    loadMeasurement(measurementId)
                } else {
                    onGenderChange(CustomerGender.FEMALE)
                }
                observeCustomFields()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementFormState(isEditMode = measurementId != null)
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
            MeasurementFormAction.OnAddCustomFieldClick -> {
                if (_state.value.canUseCustomMeasurements) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.Adding) }
                } else {
                    viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
                }
            }
            MeasurementFormAction.OnLockedCustomFieldClick -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
            }
            is MeasurementFormAction.OnEditCustomFieldClick -> {
                val field = _state.value.customFields.find { it.id == action.fieldId }
                if (field != null) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.Editing(field)) }
                }
            }
            MeasurementFormAction.OnCustomFieldSheetDismiss -> {
                _state.update { it.copy(customFieldSheet = null) }
            }
            is MeasurementFormAction.OnArchiveCustomFieldRequest -> {
                val field = _state.value.customFields.find { it.id == action.fieldId }
                if (field != null) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.ConfirmArchive(field)) }
                }
            }
            is MeasurementFormAction.OnSaveCustomField -> saveCustomField(action.id, action.label, action.genders)
            is MeasurementFormAction.OnArchiveCustomFieldConfirm -> archiveCustomField(action.fieldId)
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
        _state.update { current ->
            val visibleCustom = filterFieldsForCurrentGender(allCustomFields, gender)
            current.copy(
                gender = gender,
                sections = sections,
                currentSectionIndex = 0,
                isCurrentSectionExpanded = true,
                fields = allKeys.associateWith { "" },
                customFields = visibleCustom,
            )
        }
    }

    private fun observeCustomFields() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customFieldRepository.observeFields(userId).collect { result ->
                if (result is Result.Success) {
                    allCustomFields = result.data
                    _state.update { current ->
                        current.copy(customFields = filterFieldsForCurrentGender(result.data, current.gender))
                    }
                }
                // Errors on the field stream are non-fatal — keep the form
                // functional; tailors can retry by reopening the screen.
            }
        }
    }

    private fun filterFieldsForCurrentGender(
        fields: List<CustomMeasurementField>,
        gender: CustomerGender?,
    ): List<CustomMeasurementField> =
        fields.filter { !it.isArchived && (gender == null || gender in it.genders) }

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
                        val templateKeys = sections.flatMap { it.fields }.map { it.key }.toSet()
                        val customKeys = _state.value.customFields.map { it.id }.toSet()
                        val recordedKeys = measurement.fields.keys
                        // Union: template + visible custom + anything actually
                        // recorded on the doc (orphans included so save round-
                        // trips them cleanly, even if no definition exists).
                        val allKeys = templateKeys + customKeys + recordedKeys
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
                                isLoading = false,
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
    @Suppress("CyclomaticComplexMethod")
    private fun save() {
        // Defense in depth: gate on canSave (and isLoading) at entry so any
        // non-button invocation of OnSaveClick — accessibility activate,
        // programmatic triggers, future call sites — can't bypass the UI gate
        // and persist an empty/all-zero measurement. canSave already encodes
        // gender + at-least-one-positive-field + !isLoading.
        if (!_state.value.canSave) return
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

    @OptIn(ExperimentalUuidApi::class)
    private fun saveCustomField(id: String?, label: String, genders: Set<CustomerGender>) {
        // Defense in depth: VM-side entitlement re-check (welcome window could
        // have elapsed since the form opened; the UI check is the first gate).
        if (!entitlements.current().canUseCustomMeasurements) {
            viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
            return
        }
        // Trim + validate. Empty label or empty gender set keeps the sheet open
        // so the user can correct without losing what they typed.
        val trimmed = label.trim()
        if (trimmed.isEmpty() || genders.isEmpty()) return

        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val now = Clock.System.now().toEpochMilliseconds()
            val isCreate = id == null
            val existingCreatedAt = if (isCreate) {
                now
            } else {
                _state.value.customFields.find { it.id == id }?.createdAt ?: now
            }
            val field = CustomMeasurementField(
                id = id ?: Uuid.random().toString(),
                label = trimmed,
                genders = genders,
                isArchived = false,
                createdAt = existingCreatedAt,
                updatedAt = now,
            )
            val result = if (isCreate) {
                customFieldRepository.createField(userId, field)
            } else {
                customFieldRepository.updateField(userId, field)
            }
            if (result is Result.Success) {
                _state.update { it.copy(customFieldSheet = null) }
            } else {
                // Surface via the shared snackbar. Leave the sheet OPEN so the
                // user can retry without re-typing.
                _state.update {
                    it.copy(errorMessage = (result as Result.Error).error.toMeasurementUiText())
                }
            }
        }
    }

    private fun archiveCustomField(fieldId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = customFieldRepository.archiveField(userId, fieldId)
            if (result is Result.Success) {
                _state.update { it.copy(customFieldSheet = null) }
            } else {
                _state.update {
                    it.copy(
                        customFieldSheet = null, // close confirm; error shown via snackbar
                        errorMessage = (result as Result.Error).error.toMeasurementUiText(),
                    )
                }
            }
        }
    }
}

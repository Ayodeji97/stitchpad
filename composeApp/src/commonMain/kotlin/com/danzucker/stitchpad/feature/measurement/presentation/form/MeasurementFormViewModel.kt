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
import kotlinx.coroutines.withTimeoutOrNull
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

    private val customerId: String? = savedStateHandle["customerId"]
    private val measurementId: String? = savedStateHandle["measurementId"]
    private val linkToOrderId: String? = savedStateHandle["linkToOrderId"]
    private val fromCustomerCreation: Boolean = savedStateHandle["fromCustomerCreation"] ?: false

    private var hasLoadedInitialData = false

    // Unfiltered cache of all custom fields the tailor has defined. `state.customFields`
    // holds the gender-filtered subset; switching gender re-filters from THIS list
    // (not from the already-filtered state, which would lose other-gender fields).
    private var allCustomFields: List<CustomMeasurementField> = emptyList()

    private val _state = MutableStateFlow(
        MeasurementFormState(
            isEditMode = measurementId != null,
            fromCustomerCreation = fromCustomerCreation,
        ),
    )

    private val _events = Channel<MeasurementFormEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                if (customerId == null) {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(MeasurementFormEvent.NavigateBack)
                    return@onStart
                }
                val unit = measurementPreferencesStore.getUnit()
                _state.update { it.copy(unit = unit) }
                // Observe the entitlement flow rather than reading current()
                // once — on cold start current() returns the default FREE /
                // not-in-welcome snapshot before the user-doc hydrates, which
                // would briefly show Pro users (and fresh-signup First Month
                // users) the locked UI + upgrade sheet. Collecting the flow
                // keeps state in sync as hydration lands.
                observeEntitlements()
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
            initialValue = MeasurementFormState(
                isEditMode = measurementId != null,
                fromCustomerCreation = fromCustomerCreation,
            )
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onAction(action: MeasurementFormAction) {
        when (action) {
            is MeasurementFormAction.OnGenderChange -> onGenderChange(action.gender)
            is MeasurementFormAction.OnNameChange ->
                _state.update { it.copy(name = action.name) }
            is MeasurementFormAction.OnSectionChange -> {
                _state.update { it.copy(currentSectionIndex = action.index, isCurrentSectionExpanded = true) }
            }
            MeasurementFormAction.OnNextSection -> {
                _state.update { s ->
                    // Last page is the custom step at index sections.size, so Next
                    // walks one past the last default section (sections.size - 1).
                    val next = (s.currentSectionIndex + 1).coerceAtMost(s.sections.size)
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
                openCustomFieldSheetWhenEntitled()
            }
            MeasurementFormAction.OnLockedCustomFieldClick -> {
                openCustomFieldSheetWhenEntitled()
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
            is MeasurementFormAction.OnCustomFieldDraftLabelChange -> {
                updateCustomFieldDraft { it.copy(label = action.label) }
            }
            is MeasurementFormAction.OnCustomFieldDraftInitialValueChange -> {
                updateCustomFieldDraft { it.copy(initialValue = action.value) }
            }
            is MeasurementFormAction.OnCustomFieldDraftGendersChange -> {
                updateCustomFieldDraft { it.copy(genders = action.genders) }
            }
            is MeasurementFormAction.OnArchiveCustomFieldRequest -> {
                val field = _state.value.customFields.find { it.id == action.fieldId }
                if (field != null) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.ConfirmArchive(field)) }
                }
            }
            is MeasurementFormAction.OnSaveCustomField -> saveCustomField(
                id = action.id,
                label = action.label,
                genders = action.genders,
                initialValue = action.initialValue,
            )
            is MeasurementFormAction.OnArchiveCustomFieldConfirm -> archiveCustomField(action.fieldId)
            MeasurementFormAction.OnSaveClick -> save()
            MeasurementFormAction.OnSkipClick -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.SkipMeasurements) }
            }
            MeasurementFormAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateBack) }
            }
            MeasurementFormAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun openCustomFieldSheetWhenEntitled() {
        viewModelScope.launch {
            val resolvedEntitlements = withTimeoutOrNull(ENTITLEMENTS_HYDRATION_TIMEOUT_MS) {
                entitlements.awaitHydrated()
            } ?: entitlements.current()
            val canUseCustomMeasurements = resolvedEntitlements.canUseCustomMeasurements
            if (canUseCustomMeasurements) {
                _state.update { it.copy(customFieldSheet = CustomFieldSheet.Adding()) }
            } else {
                _events.send(MeasurementFormEvent.NavigateToUpgrade)
            }
        }
    }

    private fun updateCustomFieldDraft(transform: (CustomFieldDraft) -> CustomFieldDraft) {
        _state.update { current ->
            val sheet = when (val active = current.customFieldSheet) {
                is CustomFieldSheet.Adding -> active.copy(draft = transform(active.draft))
                is CustomFieldSheet.Editing -> active.copy(draft = transform(active.draft))
                is CustomFieldSheet.ConfirmArchive,
                null -> active
            }
            current.copy(customFieldSheet = sheet)
        }
    }

    private fun onGenderChange(gender: CustomerGender) {
        val sections = BodyProfileTemplate.sectionsFor(gender)
        val templateKeys = sections.flatMap { it.fields }.map { it.key }
        _state.update { current ->
            val visibleCustom = customFieldsForGender(
                fields = allCustomFields,
                gender = gender,
                recordedValues = current.fields,
                preserveRecorded = current.isEditMode,
            )
            val customKeys = visibleCustom.map { it.id }
            val preservedRecordedCustomKeys = if (current.isEditMode) {
                current.fields.keys.filter { key ->
                    current.fields[key].orEmpty().isNotBlank() && isCustomOrOrphanKey(key)
                }
            } else {
                emptyList()
            }
            val allKeys = templateKeys + customKeys + preservedRecordedCustomKeys
            val newFields = allKeys.associateWith { key -> current.fields[key] ?: "" }
            current.copy(
                gender = gender,
                sections = sections,
                currentSectionIndex = 0,
                isCurrentSectionExpanded = true,
                fields = newFields,
                customFields = visibleCustom,
            )
        }
    }

    private fun observeEntitlements() {
        viewModelScope.launch {
            entitlements.flow.collect { ents ->
                _state.update { current ->
                    val fields = if (!current.isEditMode && !ents.canUseCustomMeasurements) {
                        current.fields.filterKeys { key -> !isCustomOrOrphanKey(key) }
                    } else {
                        current.fields
                    }
                    current.copy(
                        fields = fields,
                        canUseCustomMeasurements = ents.canUseCustomMeasurements,
                        isInWelcomeWindow = ents.isInWelcomeWindow,
                        tier = ents.tier,
                    )
                }
            }
        }
    }

    private fun observeCustomFields() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customFieldRepository.observeFields(userId).collect { result ->
                if (result is Result.Success) {
                    allCustomFields = result.data
                    _state.update { current ->
                        // Preserve fields whose ids already appear as recorded values
                        // on the loaded measurement — even if archived or opposite-
                        // gender. Without this the observer's re-emit (e.g., after
                        // loadMeasurement completes) would silently drop archived-
                        // but-recorded rows from the form. See loadMeasurement for
                        // the matching edit-mode filter.
                        val visible = result.data.filter { field ->
                            val hasRecordedValue = current.isEditMode &&
                                current.fields[field.id]?.isNotBlank() == true
                            val passesNormalFilter = !field.isArchived &&
                                (current.gender == null || current.gender in field.genders)
                            hasRecordedValue || passesNormalFilter
                        }
                        val fields = if (current.isEditMode) {
                            current.fields
                        } else {
                            val visibleIds = visible.map { it.id }.toSet()
                            val customFieldIds = result.data.map { it.id }.toSet()
                            current.fields.filterKeys { key -> key !in customFieldIds || key in visibleIds }
                        }
                        current.copy(customFields = visible, fields = fields)
                    }
                }
                // Errors on the field stream are non-fatal — keep the form
                // functional; tailors can retry by reopening the screen.
            }
        }
    }

    private fun customFieldsForGender(
        fields: List<CustomMeasurementField>,
        gender: CustomerGender?,
        recordedValues: Map<String, String>,
        preserveRecorded: Boolean,
    ): List<CustomMeasurementField> =
        fields.filter { field ->
            val hasRecordedValue = preserveRecorded && recordedValues[field.id]?.isNotBlank() == true
            val passesNormalFilter = !field.isArchived && (gender == null || gender in field.genders)
            hasRecordedValue || passesNormalFilter
        }

    private fun isCustomOrOrphanKey(key: String): Boolean {
        val customFieldIds = allCustomFields.map { it.id }.toSet()
        val templateKeys = CustomerGender.entries
            .flatMap { gender -> BodyProfileTemplate.sectionsFor(gender) }
            .flatMap { section -> section.fields }
            .map { field -> field.key }
            .toSet()
        return key in customFieldIds || key !in templateKeys
    }

    @Suppress("LongMethod")
    private fun loadMeasurement(id: String) {
        val customerId = customerId ?: return
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
                        val recordedKeys = measurement.fields.keys
                        // Re-filter custom fields against the measurement's gender. In
                        // edit mode the observer may have emitted before gender was
                        // known (the filter treats null as wildcard); without this
                        // re-filter, opposite-gender fields can leak into the UI and
                        // be persisted on save. ALSO surface any field the user has
                        // already recorded a value for — even archived/opposite-gender
                        // — so the spec promise "Values already recorded stay visible
                        // on past measurements" holds and the value isn't silently
                        // strand-edited via the orphan path.
                        val visibleCustom = allCustomFields.filter { field ->
                            val hasRecordedValue = field.id in recordedKeys
                            val passesNormalFilter = !field.isArchived && measurement.gender in field.genders
                            hasRecordedValue || passesNormalFilter
                        }
                        val templateKeys = sections.flatMap { it.fields }.map { it.key }.toSet()
                        val customKeys = visibleCustom.map { it.id }.toSet()
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
                                name = measurement.name,
                                sections = sections,
                                fields = fieldsAsString,
                                customFields = visibleCustom,
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

    // Complexity sits exactly at detekt's threshold (15). Pre-existing; surfaced
    // by gating after PTSP-21 wired in unrelated dependencies. A real refactor
    // (extract validation, persistence, navigation arms) is worth doing but
    // outside the brand-logo PR scope.
    @OptIn(ExperimentalUuidApi::class)
    @Suppress("CyclomaticComplexMethod")
    private fun save() {
        // Defense in depth: gate on canSave (and isLoading) at entry so any
        // non-button invocation of OnSaveClick — accessibility activate,
        // programmatic triggers, future call sites — can't bypass the UI gate
        // and persist an empty/all-zero measurement. canSave already encodes
        // gender + at-least-one-positive-field + !isLoading.
        if (!_state.value.canSave) return
        val customerId = customerId ?: return
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
            val isCreate = measurementId == null
            val parsedFields = s.fields
                .mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                .filter { it.value > 0.0 }
                .filterKeys { key ->
                    !isCreate || s.canUseCustomMeasurements || !isCustomOrOrphanKey(key)
                }
            if (parsedFields.isEmpty()) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // Pre-generate the id for create flow so we can link it to the order
            // before observeOrder re-emits. For edit flow we keep the existing id.
            val effectiveId = measurementId ?: Uuid.random().toString()

            val measurement = Measurement(
                id = effectiveId,
                customerId = customerId,
                gender = gender,
                name = s.name.trim(),
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

            if (isCreate) {
                linkMeasurementToOrderIfRequested(userId, effectiveId)
            }

            _state.update { it.copy(isLoading = false) }
            _events.send(MeasurementFormEvent.NavigateBack)
        }
    }

    /**
     * If the form was opened from an order's "link measurement" picker,
     * attach the just-persisted measurement to the order's first item.
     *
     * Failure to link is silent: the measurement is already persisted, the
     * order's `observeOrder` flow re-emits on network recovery, and the user
     * can retry the link from the order details screen. We deliberately do
     * not surface a separate error toast — the measurement save itself
     * succeeded and that's the primary user intent.
     */
    private suspend fun linkMeasurementToOrderIfRequested(
        userId: String,
        measurementId: String,
    ) {
        val linkOrderId = linkToOrderId ?: return
        val order = withTimeoutOrNull(LINK_ORDER_READ_TIMEOUT_MS) {
            (orderRepository.getOrder(userId, linkOrderId) as? Result.Success)?.data
        }
        val firstItem = order?.items?.firstOrNull()
        if (order != null && firstItem != null) {
            val updatedItems = listOf(firstItem.copy(measurementId = measurementId)) +
                order.items.drop(1)
            orderRepository.updateOrder(userId, order.copy(items = updatedItems))
        }
    }

    private companion object {
        const val LINK_ORDER_READ_TIMEOUT_MS = 750L
        const val ENTITLEMENTS_HYDRATION_TIMEOUT_MS = 2_000L
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveCustomField(
        id: String?,
        label: String,
        genders: Set<CustomerGender>,
        initialValue: String,
    ) {
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
            // Look up the whole existing field once so we preserve BOTH
            // createdAt AND isArchived on edit. Use the unfiltered cache
            // (`allCustomFields`) — `state.customFields` is the gender+archive
            // filtered subset, so archived fields surfaced in edit mode aren't
            // present there. Hardcoding isArchived = false would silently
            // un-archive an archived field (Bugbot HIGH).
            val existingField = id?.let { fieldId ->
                allCustomFields.find { it.id == fieldId }
            }
            val field = CustomMeasurementField(
                id = id ?: Uuid.random().toString(),
                label = trimmed,
                genders = genders,
                isArchived = existingField?.isArchived ?: false,
                createdAt = existingField?.createdAt ?: now,
                updatedAt = now,
            )
            val result = if (isCreate) {
                customFieldRepository.createField(userId, field)
            } else {
                customFieldRepository.updateField(userId, field)
            }
            if (result is Result.Success) {
                _state.update { current ->
                    val valueToApply = initialValue.trim()
                    val shouldSeedInitialValue = shouldSeedInitialCustomValue(
                        isCreate = isCreate,
                        value = valueToApply,
                        currentGender = current.gender,
                        field = field,
                    )
                    val updatedFields = if (shouldSeedInitialValue) {
                        current.fields + (field.id to valueToApply)
                    } else {
                        current.fields
                    }
                    current.copy(
                        fields = updatedFields,
                        customFieldSheet = null,
                    )
                }
            } else {
                // Surface via the shared snackbar. Leave the sheet OPEN so the
                // user can retry without re-typing.
                _state.update {
                    it.copy(errorMessage = (result as Result.Error).error.toMeasurementUiText())
                }
            }
        }
    }

    private fun shouldSeedInitialCustomValue(
        isCreate: Boolean,
        value: String,
        currentGender: CustomerGender?,
        field: CustomMeasurementField,
    ): Boolean =
        isCreate &&
            value.isNotBlank() &&
            currentGender?.let { it in field.genders } == true

    private fun archiveCustomField(fieldId: String) {
        // Defense in depth: VM-side entitlement re-check (welcome window could
        // have elapsed since the form opened; the Edit sheet may still be
        // reachable on a recorded row in edit mode even after the entitlement
        // is lost). Mirrors the saveCustomField pattern.
        if (!entitlements.current().canUseCustomMeasurements) {
            viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
            return
        }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = customFieldRepository.archiveField(userId, fieldId)
            if (result is Result.Success) {
                _state.update { current ->
                    // PTSP-30: deleting a custom field also clears its value on the
                    // measurement being edited, so the row disappears immediately
                    // (otherwise the recorded-value branch in observeCustomFields
                    // re-adds it on the next snapshot) and the value is dropped from
                    // this measurement on save. Other measurements that recorded the
                    // field keep their values — their documents are untouched.
                    current.copy(
                        fields = current.fields - fieldId,
                        customFields = current.customFields.filterNot { it.id == fieldId },
                        customFieldSheet = null,
                    )
                }
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

package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.MeasurementShareData
import com.danzucker.stitchpad.core.sharing.MeasurementSharer
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareFormatter
import com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareLabels
import com.danzucker.stitchpad.feature.measurement.presentation.toMeasurementUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_section_title
import stitchpad.composeapp.generated.resources.measurement_detail_title
import stitchpad.composeapp.generated.resources.measurement_gender_men
import stitchpad.composeapp.generated.resources.measurement_gender_women
import stitchpad.composeapp.generated.resources.measurement_share_error
import stitchpad.composeapp.generated.resources.measurement_unit_cm
import stitchpad.composeapp.generated.resources.measurement_unit_inches
import stitchpad.composeapp.generated.resources.section_arms
import stitchpad.composeapp.generated.resources.section_body_lengths
import stitchpad.composeapp.generated.resources.section_bust
import stitchpad.composeapp.generated.resources.section_neck_shoulders
import stitchpad.composeapp.generated.resources.section_trouser
import stitchpad.composeapp.generated.resources.section_upper_body
import stitchpad.composeapp.generated.resources.section_waist_hip

class MeasurementDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository,
    private val analytics: Analytics,
    private val measurementSharer: MeasurementSharer,
    // Defaulted (rather than a plain member fun) so ViewModel tests can substitute a
    // resource-free fake: the production resolver calls getString(), which throws
    // "Method getSystem in android.content.res.Resources not mocked" under plain-JVM
    // ViewModel unit tests (testDebugUnitTest has no Robolectric/Android Resources).
    // Confirmed empirically — no existing commonTest precedent calls getString().
    // Koin can't supply a default for a constructor-ref registration (see
    // feedback_koin_constructor_ref_defaults memory), so MeasurementModule registers
    // this VM via an explicit `viewModel { ... }` lambda that omits this arg.
    private val shareLabelsResolver: suspend (Measurement) -> MeasurementShareLabels =
        defaultShareLabelsResolver(authRepository),
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
                observeCustomer(customerId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementDetailState(showSavedMessage = fromSave),
        )

    // Cyclomatic complexity 17 vs the 15 threshold — same as OrderDetailViewModel.onAction:
    // a flat `when` dispatcher over every user action is inherently this "complex"; splitting
    // it into sub-dispatchers would only add indirection, not reduce real complexity, and
    // would push this class over TooManyFunctions (already right at its own threshold).
    @Suppress("CyclomaticComplexMethod")
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
            MeasurementDetailAction.OnShareClick -> onShareClick()
            MeasurementDetailAction.OnDismissShareSheet ->
                _state.update { it.copy(showShareSheet = false) }
            MeasurementDetailAction.OnShareAsImageClick -> shareMeasurement(FORMAT_IMAGE)
            MeasurementDetailAction.OnShareAsPdfClick -> shareMeasurement(FORMAT_PDF)
            MeasurementDetailAction.OnShareWhatsAppClick -> shareMeasurement(FORMAT_WHATSAPP)
        }
    }

    private fun onShareClick() = requireUnlocked {
        _state.update { it.copy(showShareSheet = true) }
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

    private fun observeCustomer(customerId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customerRepository.observeCustomer(userId, customerId).collect { result ->
                if (result is Result.Success) {
                    _state.update {
                        it.copy(
                            customer = result.data,
                            isLocked = result.data.slotState == CustomerSlotState.LOCKED,
                        )
                    }
                }
                // On error keep the last known customer/lock state — read-only content stays visible.
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

    private fun shareMeasurement(format: String) {
        val measurement = _state.value.measurement ?: return
        val customer = _state.value.customer ?: return
        _state.update { it.copy(showShareSheet = false) }
        viewModelScope.launch {
            try {
                val data = buildShareData(measurement, customer)
                dispatchShare(format, data, customer)
                analytics.logEvent(AnalyticsEvent.MeasurementShared(format))
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                // Same contract as receipt sharing: renderers throw on failure. Matches
                // OrderDetailViewModel.shareReceipt's identical suppression pair.
                _state.update {
                    it.copy(errorMessage = UiText.StringResourceText(Res.string.measurement_share_error))
                }
            }
        }
    }

    // Folds the WhatsApp branch inline (rather than a separate shareWhatsApp() member)
    // to keep this class's function count under detekt's TooManyFunctions threshold.
    private suspend fun dispatchShare(format: String, data: MeasurementShareData, customer: Customer) {
        when (format) {
            FORMAT_IMAGE -> measurementSharer.shareAsImage(data)
            FORMAT_PDF -> measurementSharer.shareAsPdf(data)
            FORMAT_WHATSAPP -> {
                val text = MeasurementShareFormatter.buildWhatsAppText(data)
                if (customer.phone.isBlank()) {
                    // No number on file — fall back to the generic share sheet.
                    measurementSharer.shareAsText(text)
                } else {
                    _events.send(MeasurementDetailEvent.LaunchWhatsApp(customer.phone, text))
                }
            }
        }
    }

    private suspend fun buildShareData(measurement: Measurement, customer: Customer): MeasurementShareData =
        MeasurementShareFormatter.format(
            measurement = measurement,
            customerName = customer.name,
            labels = shareLabelsResolver(measurement),
            customFieldLabels = _state.value.customFieldLabels,
        )

    private companion object {
        const val FORMAT_IMAGE = "image"
        const val FORMAT_PDF = "pdf"
        const val FORMAT_WHATSAPP = "whatsapp_text"
    }
}

/**
 * Production [MeasurementShareLabels] resolver — reads localized string resources via
 * [getString]. Kept top-level (not a class member) so it's the one piece of the share
 * pipeline that touches Compose resources; the ViewModel takes it as an injected
 * function so tests can supply a resource-free fake instead.
 */
private fun defaultShareLabelsResolver(
    authRepository: AuthRepository,
): suspend (Measurement) -> MeasurementShareLabels = { measurement ->
    MeasurementShareLabels(
        measurementName = measurement.name.ifBlank { getString(Res.string.measurement_detail_title) },
        genderLabel = getString(
            if (measurement.gender == CustomerGender.FEMALE) {
                Res.string.measurement_gender_women
            } else {
                Res.string.measurement_gender_men
            },
        ),
        unitLabel = getString(
            if (measurement.unit == MeasurementUnit.INCHES) {
                Res.string.measurement_unit_inches
            } else {
                Res.string.measurement_unit_cm
            },
        ),
        unitSuffix = if (measurement.unit == MeasurementUnit.INCHES) "″" else "cm",
        dateFormatted = MeasurementShareFormatter.formatShareDate(measurement.dateTaken),
        businessName = authRepository.getCurrentUser()?.businessName?.takeIf { it.isNotBlank() },
        sectionTitles = defaultLocalizedSectionTitles(),
        customSectionTitle = getString(Res.string.custom_field_section_title),
    )
}

// Split out of defaultShareLabelsResolver() purely to keep that function's line/param
// count under detekt's thresholds — same section keys as measurementDetailSections().
private suspend fun defaultLocalizedSectionTitles(): Map<String, String> = mapOf(
    "section_upper_body" to getString(Res.string.section_upper_body),
    "section_body_lengths" to getString(Res.string.section_body_lengths),
    "section_trouser" to getString(Res.string.section_trouser),
    "section_neck_shoulders" to getString(Res.string.section_neck_shoulders),
    "section_bust" to getString(Res.string.section_bust),
    "section_waist_hip" to getString(Res.string.section_waist_hip),
    "section_arms" to getString(Res.string.section_arms),
)

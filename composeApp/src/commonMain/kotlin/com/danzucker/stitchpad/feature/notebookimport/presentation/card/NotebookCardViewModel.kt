package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.CurrentCountryProvider
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import com.danzucker.stitchpad.feature.freemium.presentation.upgradeCtaPrice
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.domain.SaveOutcome
import com.danzucker.stitchpad.feature.notebookimport.domain.isDone
import com.danzucker.stitchpad.feature.notebookimport.domain.resolveMatch
import com.danzucker.stitchpad.feature.notebookimport.presentation.CapSheet
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.NotebookReviewSession
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.ReviewDismissals
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.pageNumbers
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.pendingSamePersonGroups
import com.danzucker.stitchpad.util.Platform
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.notebook_card_not_found

private const val TAG = "NotebookCard"
private const val MAX_NAME_LENGTH = 80
private const val MAX_PHONE_LENGTH = 20
private const val MAX_VALUE_LENGTH = 40

/**
 * Edits one review card. Edits stay in the draft and are written when the tailor leaves or acts
 * ("Looks right", "Save now", "Skip"), through the offline queue. The existing-customer match is a
 * suggestion until the tailor picks "Add to"; only then is matchedCustomerId set.
 * Never logs names, phones or values.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class NotebookCardViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NotebookImportRepository,
    private val saver: NotebookImportSaver,
    private val customerRepository: CustomerRepository,
    private val activeWorkshop: ActiveWorkshopProvider,
    private val entitlements: EntitlementsProvider,
    private val countryProvider: CurrentCountryProvider,
    private val unitStore: MeasurementPreferencesStore,
    private val reviewSession: NotebookReviewSession,
) : ViewModel() {

    private val jobId: String = savedStateHandle["jobId"] ?: ""
    private val cardId: String = savedStateHandle["cardId"] ?: ""

    private var hasLoadedInitialData = false
    private var ownerUid: String? = null

    /** The card as last loaded or written; the draft is compared against it on leave. */
    private var persisted: ReviewCard? = null

    /** Null until the customer list has loaded. */
    private var customers: List<Customer>? = null
    private var imageRequested = false

    /** The job's other active cards and the kept-separate groups, for the same-person check. */
    private var otherActive: List<ReviewCard> = emptyList()
    private var dismissals = ReviewDismissals()

    private val _state = MutableStateFlow(NotebookCardState())
    private val _events = Channel<NotebookCardEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                viewModelScope.launch { load() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = NotebookCardState(),
        )

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: NotebookCardAction) {
        when (action) {
            is NotebookCardAction.OnNameChange -> edit { it.copy(name = action.name.take(MAX_NAME_LENGTH)) }
            is NotebookCardAction.OnPhoneChange -> edit { card ->
                val phone = action.phone.filter { it.isDigit() || it == '+' || it == ' ' }.take(MAX_PHONE_LENGTH)
                card.copy(phone = phone.ifBlank { null })
            }
            is NotebookCardAction.OnGenderSelect -> edit { it.copy(gender = action.gender) }
            is NotebookCardAction.OnUnitSelect -> edit { it.copy(unit = action.unit) }
            NotebookCardAction.OnDateClick -> if (!isReadOnly()) _state.update { it.copy(showDatePicker = true) }
            NotebookCardAction.OnDateDismiss -> _state.update { it.copy(showDatePicker = false) }
            is NotebookCardAction.OnDatePicked -> {
                edit { it.copy(dateWritten = action.date) }
                _state.update { it.copy(showDatePicker = false) }
            }
            NotebookCardAction.OnDateClear -> edit { it.copy(dateWritten = null) }
            is NotebookCardAction.OnValueChange -> editValue(action.index) {
                it.copy(value = action.value.take(MAX_VALUE_LENGTH), flagged = false)
            }
            is NotebookCardAction.OnValueConfirm -> editValue(action.index) { it.copy(flagged = false) }
            is NotebookCardAction.OnValueRemove -> edit { card ->
                card.copy(values = card.values.filterIndexed { i, _ -> i != action.index })
            }
            is NotebookCardAction.OnFieldPickerOpen ->
                if (!isReadOnly()) _state.update { it.copy(fieldPickerIndex = action.index) }
            NotebookCardAction.OnFieldPickerDismiss -> _state.update { it.copy(fieldPickerIndex = null) }
            is NotebookCardAction.OnFieldSelect -> selectField(action.index, action.fieldKey)
            is NotebookCardAction.OnMatchToggle -> toggleMatch(action.addToExisting)
            NotebookCardAction.OnLooksRightClick -> looksRight()
            NotebookCardAction.OnSaveNowClick -> saveNow()
            NotebookCardAction.OnSkipClick -> skip()
            NotebookCardAction.OnNavigateBack -> leave()
            NotebookCardAction.OnMessageShown -> _state.update { it.copy(message = null) }
            NotebookCardAction.OnCapSheetDismiss -> _state.update { it.copy(capSheet = null) }
        }
    }

    private fun isReadOnly() = _state.value.isReadOnly

    private suspend fun load() {
        val session = activeWorkshop.awaitHydrated()
        val uid = session.workshopUid.takeIf {
            session.isOwner && it.isNotBlank() && jobId.isNotBlank() && cardId.isNotBlank()
        }
        if (uid == null) {
            _events.send(NotebookCardEvent.NavigateBack)
            return
        }
        ownerUid = uid
        val defaultUnit = unitStore.getUnit()
        _state.update { it.copy(defaultUnit = defaultUnit) }
        val cards = repository.observeCards(uid, jobId).first()
        val card = (cards as? Result.Success)?.data?.find { it.id == cardId }
        if (card == null) {
            AppLogger.w(TAG) { "card not found jobId=$jobId cardId=$cardId" }
            _state.update {
                it.copy(
                    isLoading = false,
                    notFound = true,
                    message = UiText.StringResourceText(Res.string.notebook_card_not_found),
                )
            }
            return
        }
        persisted = card
        otherActive = (cards as Result.Success).data.filter { it.id != cardId && !it.isDone }
        _state.update {
            it.copy(
                isLoading = false,
                draft = card,
                crop = cropRegion(card.box),
                fieldOptions = fieldOptionsFor(card.gender),
            )
        }
        refreshDuplicate()
        observeOtherCards(uid)
        observePageNumber(uid, card.pageId)
        observePhoto(uid, card.pageId)
        observeCustomers(uid)
        viewModelScope.launch {
            countryProvider.country.collect { country ->
                _state.update { it.copy(upgradeCtaPrice = upgradeCtaPrice(Platform.isIos, country.currency)) }
            }
        }
    }

    private fun observeOtherCards(uid: String) {
        viewModelScope.launch {
            repository.observeCards(uid, jobId).collect { result ->
                if (result is Result.Success) {
                    otherActive = result.data.filter { it.id != cardId && !it.isDone }
                    refreshDuplicate()
                }
            }
        }
        viewModelScope.launch {
            reviewSession.dismissals(jobId).collect {
                dismissals = it
                refreshDuplicate()
            }
        }
    }

    /**
     * Whether the draft is in a same-person group still waiting for Combine or Keep separate, judged
     * exactly as the review list judges it. While it is, the card cannot be checked or saved alone.
     */
    private fun refreshDuplicate() {
        _state.update { s ->
            val draft = s.draft ?: return@update s
            val pending = !draft.isDone &&
                pendingSamePersonGroups(otherActive + draft, dismissals).any { draft.id in it }
            s.copy(inPendingDuplicate = pending)
        }
    }

    private fun observePageNumber(uid: String, pageId: String) {
        viewModelScope.launch {
            repository.observePages(uid, jobId).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(pageNumber = pageNumbers(result.data)[pageId] ?: 0) }
                }
            }
        }
    }

    /** The photo is shown until the job's images are deleted (7 days, or once the job ends). */
    private fun observePhoto(uid: String, pageId: String) {
        viewModelScope.launch {
            repository.observeJob(uid, jobId).collect { job -> onJob(uid, pageId, job) }
        }
    }

    private suspend fun onJob(uid: String, pageId: String, job: ImportJob?) {
        if (job == null || job.imagesDeletedAt != null) {
            _state.update { it.copy(imageUrl = null, photoUnavailable = true) }
            return
        }
        if (imageRequested) return
        imageRequested = true
        when (val url = repository.pageImageUrl(uid, jobId, pageId)) {
            is Result.Success -> _state.update { it.copy(imageUrl = url.data, photoUnavailable = false) }
            is Result.Error -> {
                AppLogger.w(TAG) { "page image unavailable jobId=$jobId: ${url.error}" }
                _state.update { it.copy(imageUrl = null, photoUnavailable = true) }
            }
        }
    }

    private fun observeCustomers(uid: String) {
        viewModelScope.launch {
            customerRepository.observeCustomers(uid).collect { result ->
                if (result is Result.Success) {
                    customers = result.data
                    refreshMatch()
                }
            }
        }
    }

    /**
     * The existing customer to offer: the confirmed one, else the matcher's suggestion for the draft.
     * A confirmed match whose customer no longer exists is cleared from the draft (written on the next
     * save or leave), so the card is never saved under a missing id.
     */
    private fun refreshMatch() {
        val loaded = customers ?: return
        _state.update { s ->
            val draft = s.draft ?: return@update s
            if (draft.status == CardStatus.SAVED) return@update s.copy(customersLoaded = true)
            val resolution = resolveMatch(draft, loaded, countryProvider.currentCountry())
            s.copy(
                draft = resolution.card,
                matchCandidate = resolution.customer?.let { ExistingCustomer(it.id, it.name) },
                customersLoaded = true,
            )
        }
    }

    private fun edit(transform: (ReviewCard) -> ReviewCard) {
        if (isReadOnly()) return
        _state.update { s ->
            val draft = s.draft ?: return@update s
            val next = transform(draft)
            s.copy(
                draft = next,
                fieldOptions = if (next.gender != draft.gender) fieldOptionsFor(next.gender) else s.fieldOptions
            )
        }
        refreshMatch()
        refreshDuplicate()
    }

    private fun editValue(index: Int, transform: (CardValue) -> CardValue) = edit { card ->
        if (index !in card.values.indices) {
            card
        } else {
            card.copy(values = card.values.mapIndexed { i, v -> if (i == index) transform(v) else v })
        }
    }

    private fun selectField(index: Int, fieldKey: String?) {
        val allowed = fieldKey == null || _state.value.fieldOptions.any { it.key == fieldKey }
        if (allowed) editValue(index) { it.copy(fieldKey = fieldKey) }
        _state.update { it.copy(fieldPickerIndex = null) }
    }

    private fun toggleMatch(addToExisting: Boolean) {
        val candidate = _state.value.matchCandidate ?: return
        edit {
            if (addToExisting) {
                it.copy(matchedCustomerId = candidate.id, keepAsNew = false)
            } else {
                it.copy(matchedCustomerId = null, keepAsNew = true)
            }
        }
    }

    /** Null when the draft can be checked, else why not. */
    private fun blockReason(): UiText? =
        _state.value.checkBlocker?.let { UiText.StringResourceText(it.hintResource()) }

    /** The draft as a checked card, or null (with the reason shown) when it cannot be checked yet. */
    @Suppress("ReturnCount")
    private fun checkedDraft(): ReviewCard? {
        val draft = _state.value.draft ?: return null
        if (!_state.value.customersLoaded) return null
        val reason = blockReason()
        if (reason != null) {
            _state.update { it.copy(message = reason) }
            return null
        }
        return draft.copy(status = CardStatus.CHECKED)
    }

    @Suppress("ReturnCount")
    private fun looksRight() {
        if (isReadOnly()) return leave()
        if (_state.value.isSaving) return
        val checked = checkedDraft() ?: return
        viewModelScope.launch {
            if (persist(checked)) _events.send(NotebookCardEvent.NavigateBack)
        }
    }

    @Suppress("ReturnCount")
    private fun saveNow() {
        if (isReadOnly() || _state.value.isSaving) return
        val uid = ownerUid ?: return
        val checked = checkedDraft() ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (!persist(checked)) return@launch
                val country = countryProvider.countryForWrite()
                if (country == null) {
                    _state.update { it.copy(message = UiText.StringResourceText(Res.string.error_no_internet)) }
                    return@launch
                }
                refreshDuplicate()
                val blocked = setOf(checked.id).takeIf { _state.value.inPendingDuplicate }.orEmpty()
                onSaveOutcome(saver.save(uid, listOf(checked), unitStore.getUnit(), country, blocked))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun onSaveOutcome(outcome: SaveOutcome) {
        when (outcome) {
            // Done(0) only when the card was already saved elsewhere: either way it is finished here.
            is SaveOutcome.Done -> _events.send(NotebookCardEvent.NavigateBack)
            is SaveOutcome.StoppedAtCap -> {
                val cap = entitlements.current().customerCap
                _state.update { it.copy(capSheet = CapSheet(activeCount = cap, customerCap = cap)) }
            }
            is SaveOutcome.Failed -> {
                AppLogger.w(TAG) { "save now failed: ${outcome.error}" }
                showError(outcome.error)
            }
        }
    }

    private fun skip() {
        if (isReadOnly()) return leave()
        val draft = _state.value.draft ?: return leave()
        viewModelScope.launch {
            if (persist(draft.copy(status = CardStatus.SKIPPED))) _events.send(NotebookCardEvent.NavigateBack)
        }
    }

    /** Back: keeps the tailor's edits (without changing the card's status), then leaves. */
    private fun leave() {
        val unsaved = _state.value.draft?.takeIf { !isReadOnly() && it != persisted }
        viewModelScope.launch {
            if (unsaved != null && !persist(unsaved)) return@launch
            _events.send(NotebookCardEvent.NavigateBack)
        }
    }

    private suspend fun persist(card: ReviewCard): Boolean {
        val uid = ownerUid ?: return false
        return when (val result = repository.updateCard(uid, card)) {
            is Result.Success -> {
                persisted = card
                _state.update { it.copy(draft = card) }
                true
            }
            is Result.Error -> {
                showError(result.error)
                false
            }
        }
    }

    private fun showError(error: DataError.Network) {
        _state.update { it.copy(message = error.toCustomerUiText()) }
    }

    private fun fieldOptionsFor(gender: CustomerGender?): List<MeasurementField> {
        val genders = gender?.let { listOf(it) } ?: CustomerGender.entries
        return genders.flatMap { g -> BodyProfileTemplate.sectionsFor(g).flatMap { it.fields } }.distinctBy { it.key }
    }
}

package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.CurrentCountryProvider
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import com.danzucker.stitchpad.feature.freemium.presentation.upgradeCtaPrice
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.domain.SaveOutcome
import com.danzucker.stitchpad.feature.notebookimport.domain.UploadPhase
import com.danzucker.stitchpad.feature.notebookimport.domain.isDone
import com.danzucker.stitchpad.feature.notebookimport.domain.mergeCards
import com.danzucker.stitchpad.util.Platform
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.notebook_review_saved_count
import stitchpad.composeapp.generated.resources.notebook_review_saved_some

private const val TAG = "NotebookReview"

/** Everything the screen is built from, from one snapshot of each source. */
private data class ReviewInputs(
    val cards: List<ReviewCard>,
    val pages: List<ImportPage>,
    val customers: List<Customer>,
    val country: Country,
    val localUploading: Int,
)

/**
 * The review list of one import job. Match suggestions live only here (in memory); a card's
 * matchedCustomerId is written only when the tailor confirms. Never logs names, phones or values.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class NotebookReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NotebookImportRepository,
    private val saver: NotebookImportSaver,
    private val customerRepository: CustomerRepository,
    private val activeWorkshop: ActiveWorkshopProvider,
    private val entitlements: EntitlementsProvider,
    private val countryProvider: CurrentCountryProvider,
    private val unitStore: MeasurementPreferencesStore,
    private val uploader: NotebookPageUploader,
) : ViewModel() {

    private val jobId: String = savedStateHandle["jobId"] ?: ""

    private var hasLoadedInitialData = false
    private var ownerUid: String? = null
    private var latestCards: List<ReviewCard> = emptyList()
    private var latestSections: ReviewSections? = null
    private var jobClosed = false
    private val dismissals = MutableStateFlow(ReviewDismissals())

    private val _state = MutableStateFlow(NotebookReviewState())
    private val _events = Channel<NotebookReviewEvent>()
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
            initialValue = NotebookReviewState(),
        )

    fun onAction(action: NotebookReviewAction) {
        when (action) {
            is NotebookReviewAction.OnCardClick -> send(NotebookReviewEvent.NavigateToCard(jobId, action.cardId))
            is NotebookReviewAction.OnConfirmMatch -> confirmMatch(action.cardId)
            is NotebookReviewAction.OnRejectMatch -> rejectMatch(action.cardId)
            is NotebookReviewAction.OnMergeClick -> merge(action.cardIds, action.dateNoteLabel)
            is NotebookReviewAction.OnKeepSeparateClick ->
                dismissals.update { it.copy(groups = it.groups + setOf(action.cardIds.toSet())) }
            NotebookReviewAction.OnSaveAllClick -> saveAll()
            NotebookReviewAction.OnDiscardClick -> _state.update { it.copy(showDiscardDialog = true) }
            NotebookReviewAction.OnDiscardDismiss -> _state.update { it.copy(showDiscardDialog = false) }
            NotebookReviewAction.OnDiscardConfirm -> discard()
            NotebookReviewAction.OnNavigateBack -> send(NotebookReviewEvent.NavigateBack)
            NotebookReviewAction.OnMessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private fun send(event: NotebookReviewEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    /** Owner only: staff never see an import. */
    private suspend fun load() {
        val session = activeWorkshop.awaitHydrated()
        val uid = session.workshopUid.takeIf { session.isOwner && it.isNotBlank() && jobId.isNotBlank() }
        if (uid == null) {
            _events.send(NotebookReviewEvent.NavigateBack)
            return
        }
        ownerUid = uid
        viewModelScope.launch {
            countryProvider.country.collect { country ->
                _state.update {
                    it.copy(upgradeCtaPrice = upgradeCtaPrice(Platform.isIos, country.currency))
                }
            }
        }
        val inputs = combine(
            cardsOf(uid),
            pagesOf(uid),
            customersOf(uid),
            countryProvider.country,
            localUploadingCount(),
        ) { cards, pages, customers, country, uploading -> ReviewInputs(cards, pages, customers, country, uploading) }
        combine(inputs, dismissals) { input, dismissed -> input to dismissed }
            .collect { (input, dismissed) -> render(input, dismissed) }
    }

    private fun cardsOf(uid: String): Flow<List<ReviewCard>> = flow {
        repository.observeCards(uid, jobId).collect { result ->
            when (result) {
                is Result.Success -> emit(result.data)
                is Result.Error -> AppLogger.w(TAG) { "observeCards failed: ${result.error}" }
            }
        }
    }

    private fun pagesOf(uid: String): Flow<List<ImportPage>> = flow {
        repository.observePages(uid, jobId).collect { result ->
            when (result) {
                is Result.Success -> emit(result.data)
                is Result.Error -> AppLogger.w(TAG) { "observePages failed: ${result.error}" }
            }
        }
    }

    /** Customers only feed match suggestions, so the list never waits for them. */
    private fun customersOf(uid: String): Flow<List<Customer>> = flow {
        emit(emptyList())
        customerRepository.observeCustomers(uid).collect { result ->
            if (result is Result.Success) emit(result.data)
        }
    }

    /** Pages of this job still uploading from this device. */
    private fun localUploadingCount(): Flow<Int> =
        combine(uploader.jobId, uploader.pages) { id, pages ->
            if (id != jobId) 0 else pages.count { it.phase == UploadPhase.UPLOADING }
        }

    private fun render(input: ReviewInputs, dismissed: ReviewDismissals) {
        latestCards = input.cards
        val sections = buildReviewSections(
            cards = input.cards,
            pageNumbers = pageNumbers(input.pages),
            customers = input.customers,
            country = input.country,
            dismissals = dismissed,
        )
        latestSections = sections
        val reading = input.localUploading +
            input.pages.count { it.status == PageStatus.UPLOADING || it.status == PageStatus.PROCESSING }
        _state.update {
            it.copy(
                isLoading = false,
                needLook = sections.needLook,
                ready = sections.ready,
                saved = sections.saved,
                skipped = sections.skipped,
                foundCount = input.cards.size,
                needLookCount = sections.needLook.size,
                pagesReading = reading,
            )
        }
        if (input.cards.isNotEmpty() && input.cards.all { it.isDone } && reading == 0) finishWhenAllDone()
    }

    private fun finishWhenAllDone() {
        if (jobClosed) return
        jobClosed = true
        viewModelScope.launch {
            if (closeJob(abandoned = false)) {
                _state.update { it.copy(isFinished = true) }
            } else {
                jobClosed = false
            }
        }
    }

    /** Ends the job and drops its pending local uploads. Returns false when the status write failed. */
    @Suppress("ReturnCount")
    private suspend fun closeJob(abandoned: Boolean): Boolean {
        val uid = ownerUid ?: return false
        val result = repository.finishJob(uid, jobId, abandoned)
        if (result is Result.Error) {
            AppLogger.w(TAG) { "finishJob failed jobId=$jobId: ${result.error}" }
            _state.update { it.copy(message = result.error.toCustomerUiText()) }
            return false
        }
        uploader.clear(jobId)
        return true
    }

    private fun discard() {
        _state.update { it.copy(showDiscardDialog = false) }
        if (jobClosed) return
        jobClosed = true
        viewModelScope.launch {
            if (closeJob(abandoned = true)) {
                _events.send(NotebookReviewEvent.NavigateBack)
            } else {
                jobClosed = false
            }
        }
    }

    private fun card(id: String): ReviewCard? = latestCards.find { it.id == id }

    @Suppress("ReturnCount")
    private fun confirmMatch(cardId: String) {
        val uid = ownerUid ?: return
        val card = card(cardId)?.takeIf { !it.isDone } ?: return
        val suggestion = latestSections?.let { s -> (s.needLook + s.ready).find { it.cardId == cardId }?.match }
            ?: return
        update(uid, card.copy(matchedCustomerId = suggestion.customerId))
    }

    private fun rejectMatch(cardId: String) {
        val uid = ownerUid ?: return
        dismissals.update { it.copy(matches = it.matches + cardId) }
        val card = card(cardId)?.takeIf { !it.isDone } ?: return
        if (card.matchedCustomerId != null) update(uid, card.copy(matchedCustomerId = null))
    }

    private fun update(uid: String, card: ReviewCard) {
        viewModelScope.launch {
            val result = repository.updateCard(uid, card)
            if (result is Result.Error) showError(result.error)
        }
    }

    /** Keeps the first card of the group, folds the others into it, then deletes them. */
    private fun merge(cardIds: List<String>, dateNoteLabel: String) {
        val uid = ownerUid ?: return
        val group = cardIds.mapNotNull { id -> card(id)?.takeIf { !it.isDone } }
        if (group.size < 2) return
        val merged = group.drop(1).fold(group.first()) { acc, other -> mergeCards(acc, other, dateNoteLabel) }
        viewModelScope.launch {
            val written = repository.updateCard(uid, merged)
            if (written is Result.Error) {
                showError(written.error)
                return@launch
            }
            for (other in group.drop(1)) {
                val deleted = repository.deleteCard(uid, jobId, other.id)
                if (deleted is Result.Error) showError(deleted.error)
            }
        }
    }

    @Suppress("ReturnCount")
    private fun saveAll() {
        val uid = ownerUid ?: return
        if (_state.value.isSaving) return
        // Clean cards the server read without doubt count as checked once their values were shown here.
        val toSave = latestSections?.readyCards.orEmpty()
            .map { if (it.status == CardStatus.NEW) it.copy(status = CardStatus.CHECKED) else it }
        if (toSave.isEmpty()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val country = countryProvider.countryForWrite()
                if (country == null) {
                    _state.update { it.copy(message = UiText.StringResourceText(Res.string.error_no_internet)) }
                    return@launch
                }
                onSaveOutcome(saver.save(uid, toSave, unitStore.getUnit(), country), toSave.size)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun onSaveOutcome(outcome: SaveOutcome, attempted: Int) {
        when (outcome) {
            is SaveOutcome.Done -> {
                val left = attempted - outcome.savedCount
                val message = if (left > 0) {
                    UiText.StringResourceText(Res.string.notebook_review_saved_some, arrayOf(outcome.savedCount, left))
                } else {
                    UiText.StringResourceText(Res.string.notebook_review_saved_count, arrayOf(outcome.savedCount))
                }
                _state.update { it.copy(message = message) }
            }
            is SaveOutcome.StoppedAtCap -> {
                AppLogger.i(TAG) { "save stopped at cap saved=${outcome.savedCount} remaining=${outcome.remaining}" }
                val cap = entitlements.current().customerCap
                _events.send(NotebookReviewEvent.ShowCapReachedSheet(activeCount = cap, customerCap = cap))
            }
            is SaveOutcome.Failed -> {
                AppLogger.w(TAG) { "save failed saved=${outcome.savedCount}: ${outcome.error}" }
                showError(outcome.error)
            }
        }
    }

    private fun showError(error: DataError.Network) {
        _state.update { it.copy(message = error.toCustomerUiText()) }
    }
}

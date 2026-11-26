package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.LocalUpload
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.STUCK_PAGE_MILLIS
import com.danzucker.stitchpad.feature.notebookimport.domain.UploadFailure
import com.danzucker.stitchpad.feature.notebookimport.domain.UploadPhase
import com.danzucker.stitchpad.feature.notebookimport.domain.canStartNotebookImport
import com.danzucker.stitchpad.feature.notebookimport.domain.isDone
import com.danzucker.stitchpad.feature.notebookimport.domain.isStuck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.notebook_capture_photo_unreadable
import stitchpad.composeapp.generated.resources.notebook_capture_retry_limit
import kotlin.time.Clock

private const val TAG = "NotebookCapture"
private const val RESUME_LOOKUP_TIMEOUT_MS = 3_000L

/** Extra wait after a page turns stuck, so the re-check lands just past the 10 minute mark. */
private const val STUCK_RECHECK_SLACK_MS = 1_000L

/** Server error value on a page that was refused by the page cap. Retrying cannot help. */
private const val PAGE_CAP_REACHED = "page_cap_reached"

/** Outcome of one open-job look-up. */
private sealed interface LookupOutcome {
    data class Resume(val job: ImportJob) : LookupOutcome
    data object FreshStart : LookupOutcome
    data object Failed : LookupOutcome
}

/**
 * Thin observer over [NotebookPageUploader] (which owns capture, compression and upload for the app's
 * lifetime) and the repository's pages. Never logs names, phones, values or image bytes.
 *
 * Before any photo is taken the screen looks up the open job to resume. Until that look-up succeeds
 * capture stays disabled (a timeout or error shows Try again): falling through to a new job could
 * orphan a reviewed one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class NotebookCaptureViewModel(
    private val repository: NotebookImportRepository,
    private val activeWorkshop: ActiveWorkshopProvider,
    private val entitlements: EntitlementsProvider,
    private val uploader: NotebookPageUploader,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val loaded = CompletableDeferred<Unit>()
    private var serverPages: List<ImportPage> = emptyList()
    private var ownerUid: String? = null
    private var lookupJob: Job? = null
    private var bannerStarted = false

    /** Bumped when a reading page turns stuck, so the tiles are rebuilt without a server change. */
    private val clockTick = MutableStateFlow(0)
    private var stuckTimer: Job? = null

    private val _state = MutableStateFlow(NotebookCaptureState())
    private val _events = Channel<NotebookCaptureEvent>()
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
            initialValue = NotebookCaptureState(),
        )

    fun onAction(action: NotebookCaptureAction) {
        when (action) {
            is NotebookCaptureAction.OnPhotosPicked -> onPhotosPicked(action.photos)
            is NotebookCaptureAction.OnRetryPage -> onRetry(action.pageKey)
            NotebookCaptureAction.OnReviewClick -> onReviewClick()
            NotebookCaptureAction.OnNavigateBack -> send(NotebookCaptureEvent.NavigateBack)
            NotebookCaptureAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
            NotebookCaptureAction.OnRetryLookup -> retryLookup()
            NotebookCaptureAction.OnEarlierScanReviewClick ->
                _state.value.earlierJobId?.let { send(NotebookCaptureEvent.NavigateToReview(it)) }
        }
    }

    private fun send(event: NotebookCaptureEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    /** Owner-only: null when the session is not allowed to start an import. */
    private suspend fun ownerUidOrNull(): String? {
        val session = activeWorkshop.awaitHydrated()
        if (!canStartNotebookImport(session, entitlements.awaitHydrated())) return null
        return session.workshopUid.takeIf { it.isNotBlank() }
    }

    private suspend fun load() {
        try {
            val uid = ownerUidOrNull()
            if (uid == null) {
                _events.send(NotebookCaptureEvent.NavigateBack)
                return
            }
            ownerUid = uid
            observe(uid)
            runLookup(uid)
        } finally {
            loaded.complete(Unit)
        }
    }

    private fun retryLookup() {
        val uid = ownerUid ?: return
        if (_state.value.lookup != ResumeLookup.FAILED || lookupJob?.isActive == true) return
        lookupJob = viewModelScope.launch { runLookup(uid) }
    }

    /** Decides the job new photos go to. Capture is enabled only when this succeeds. */
    private suspend fun runLookup(uid: String) {
        _state.update { it.copy(lookup = ResumeLookup.LOADING) }
        // A job already held by the uploader (earlier visit to this screen) is the live one.
        val outcome = if (uploader.currentJobId() != null) LookupOutcome.FreshStart else lookUpOpenJob(uid)
        when (outcome) {
            LookupOutcome.Failed -> {
                AppLogger.w(TAG) { "open job lookup failed or timed out" }
                _state.update { it.copy(lookup = ResumeLookup.FAILED) }
                return
            }
            is LookupOutcome.Resume -> uploader.adoptJob(outcome.job)
            LookupOutcome.FreshStart -> Unit
        }
        _state.update { it.copy(lookup = ResumeLookup.READY) }
        observeEarlierScans(uid)
    }

    /**
     * Resume = the newest open job that has at least one page and whose images are not expired. An
     * empty open job (a timed-out create that synced later) is ignored; the cleanup cron removes it.
     * A timeout or a read error is [LookupOutcome.Failed], never a fresh start.
     */
    private suspend fun lookUpOpenJob(uid: String): LookupOutcome =
        withTimeoutOrNull(RESUME_LOOKUP_TIMEOUT_MS) {
            val jobs = when (val open = repository.observeOpenJobs(uid).first()) {
                is Result.Error -> return@withTimeoutOrNull LookupOutcome.Failed
                is Result.Success -> open.data
            }
            val now = nowMillis()
            for (job in jobs.filterNot { it.isImageExpired(now) }.sortedByDescending { it.createdAt }) {
                when (val pages = repository.observePages(uid, job.id).first()) {
                    is Result.Error -> return@withTimeoutOrNull LookupOutcome.Failed
                    is Result.Success -> if (pages.data.isNotEmpty()) return@withTimeoutOrNull LookupOutcome.Resume(job)
                }
            }
            LookupOutcome.FreshStart
        } ?: LookupOutcome.Failed

    /**
     * The banner: the newest open job, other than the current one, that still has a card neither saved
     * nor skipped. Old expired jobs land here, so their reviewed cards are never stranded.
     */
    private fun observeEarlierScans(uid: String) {
        if (bannerStarted) return
        bannerStarted = true
        viewModelScope.launch {
            repository.observeOpenJobs(uid)
                .combine(uploader.jobId) { jobs, current -> jobs to current }
                .flatMapLatest { (jobs, current) ->
                    val others = (jobs as? Result.Success)?.data.orEmpty()
                        .filter { it.id != current }
                        .sortedByDescending { it.createdAt }
                    if (others.isEmpty()) {
                        flowOf(null)
                    } else {
                        combine(others.map { job -> pendingCardsOf(uid, job.id) }) { pending ->
                            others.zip(pending.toList()).firstOrNull { it.second }?.first?.id
                        }
                    }
                }
                .collect { id -> _state.update { it.copy(earlierJobId = id) } }
        }
    }

    /** True while the job has a card that is neither saved nor skipped; false if the cards cannot be read. */
    private fun pendingCardsOf(uid: String, jobId: String): Flow<Boolean> =
        repository.observeCards(uid, jobId).map { result ->
            result is Result.Success && result.data.any { !it.isDone }
        }

    private fun observe(uid: String) {
        viewModelScope.launch {
            uploader.jobId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else serverPagesOf(uid, id) }
                .combine(uploader.pages) { server, local -> server to local }
                .combine(clockTick) { (server, local), _ ->
                    serverPages = server
                    scheduleStuckRecheck(server)
                    buildPages(server, local, nowMillis())
                }
                .collect { pages -> _state.update { it.copy(pages = pages) } }
        }
        viewModelScope.launch {
            uploader.errors.collect { failure ->
                when (failure) {
                    is UploadFailure.Network -> showError(failure.error)
                    UploadFailure.PhotoUnreadable -> _state.update {
                        it.copy(errorMessage = UiText.StringResourceText(Res.string.notebook_capture_photo_unreadable))
                    }
                }
            }
        }
    }

    /** Rebuilds the tiles when the next reading page passes the 10 minute mark. */
    private fun scheduleStuckRecheck(server: List<ImportPage>) {
        stuckTimer?.cancel()
        val now = nowMillis()
        val nextStuckAt = server
            .filter { it.status == PageStatus.PROCESSING && !it.isStuck(now) }
            .mapNotNull { it.claimedAt?.toEpochMilliseconds()?.plus(STUCK_PAGE_MILLIS) }
            .minOrNull() ?: return
        stuckTimer = viewModelScope.launch {
            delay(nextStuckAt - now + STUCK_RECHECK_SLACK_MS)
            clockTick.update { it + 1 }
        }
    }

    private fun serverPagesOf(uid: String, jobId: String): Flow<List<ImportPage>> = flow {
        repository.observePages(uid, jobId).collect { result ->
            when (result) {
                is Result.Success -> emit(result.data)
                is Result.Error -> AppLogger.w(TAG) { "observePages failed: ${result.error}" }
            }
        }
    }

    private fun onPhotosPicked(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            loaded.await()
            // Never start (or add to) a job before we know which job is the live one.
            if (!_state.value.canCapture) return@launch
            if (ownerUidOrNull() == null) return@launch
            uploader.submit(photos)
        }
    }

    private fun onRetry(key: String) {
        if (uploader.hasPage(key)) {
            // A local retry may create the job, so it waits for the open-job lookup like a new photo.
            if (!_state.value.canCapture) return
            uploader.retry(key)
            return
        }
        val page = serverPages.find { it.id == key }
        val job = uploader.currentJobId()
        if (page != null && job != null && page.isRetryable()) {
            viewModelScope.launch { retryServerPage(job, page.id) }
        }
    }

    private fun ImportPage.isRetryable() =
        (status == PageStatus.FAILED && error != PAGE_CAP_REACHED) || isStuck(nowMillis())

    private suspend fun retryServerPage(job: String, pageId: String) {
        val result = repository.retryPage(job, pageId)
        if (result is Result.Error) {
            AppLogger.w(TAG) { "retryPage failed: ${result.error}" }
            if (result.error == DataError.Network.TOO_MANY_REQUESTS) {
                // The server allows 3 retries per page (resource-exhausted / retry_limit_reached).
                _state.update {
                    it.copy(errorMessage = UiText.StringResourceText(Res.string.notebook_capture_retry_limit))
                }
            } else {
                showError(result.error)
            }
        }
    }

    private fun onReviewClick() {
        val job = uploader.currentJobId() ?: return
        if (!_state.value.canReview) return
        send(NotebookCaptureEvent.NavigateToReview(job))
    }

    private fun showError(error: DataError.Network) {
        val text = when (error) {
            DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
            else -> UiText.StringResourceText(Res.string.error_unknown)
        }
        _state.update { it.copy(errorMessage = text) }
    }

    private fun buildPages(server: List<ImportPage>, local: List<LocalUpload>, now: Long): List<CapturePage> {
        val uploaded = local.filter { it.pageId != null }.associateBy { it.pageId }
        val serverIds = server.mapTo(HashSet()) { it.id }

        val fromServer = server.sortedBy { it.id }.map { page ->
            CapturePage(
                key = page.id,
                number = 0,
                status = page.toCaptureStatus(now),
                personCount = page.personCount,
                thumb = uploaded[page.id]?.display,
            )
        }
        // Not on the server yet: still uploading, failed to upload, or uploaded but not synced back.
        val pending = local.filter { it.pageId == null || it.pageId !in serverIds }.map { page ->
            CapturePage(
                key = page.id,
                number = 0,
                status = if (page.phase == UploadPhase.FAILED) {
                    CapturePageStatus.UPLOAD_FAILED
                } else {
                    CapturePageStatus.UPLOADING
                },
                thumb = page.display,
            )
        }
        return (fromServer + pending).mapIndexed { index, page -> page.numbered(index + 1) }
    }

    private fun ImportPage.toCaptureStatus(now: Long): CapturePageStatus = when (status) {
        PageStatus.UPLOADING -> CapturePageStatus.UPLOADING
        PageStatus.PROCESSING -> if (isStuck(now)) CapturePageStatus.READ_STUCK else CapturePageStatus.READING
        PageStatus.DONE -> CapturePageStatus.DONE
        PageStatus.FAILED ->
            if (error == PAGE_CAP_REACHED) CapturePageStatus.CAP_REACHED else CapturePageStatus.READ_FAILED
    }
}

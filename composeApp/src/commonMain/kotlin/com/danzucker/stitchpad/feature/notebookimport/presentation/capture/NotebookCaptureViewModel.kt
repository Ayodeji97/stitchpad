package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.canStartNotebookImport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown

private const val TAG = "NotebookCapture"

// Handwriting needs more resolution than a style photo; values from the plan's Global Constraints.
private const val NOTEBOOK_MAX_EDGE_PX = 2560
private const val NOTEBOOK_JPEG_QUALITY = 90

private const val RESUME_LOOKUP_TIMEOUT_MS = 3_000L

/** Server error value on a page that was refused by the page cap. Retrying cannot help. */
private const val PAGE_CAP_REACHED = "page_cap_reached"

private enum class LocalPhase { UPLOADING, FAILED, UPLOADED }

/** A page captured in this session. Holds the compressed JPEG for the thumbnail and for re-upload. */
private class LocalPage(
    val id: String,
    val bytes: ByteArray,
    val phase: LocalPhase,
    val pageId: String? = null,
) {
    fun with(phase: LocalPhase, pageId: String? = this.pageId) = LocalPage(id, bytes, phase, pageId)
}

/**
 * Capture screen logic. Never logs names, phones, values or image bytes: only ids and error types.
 *
 * Uploads run one at a time, in capture order, so server page ids (which encode upload time) keep
 * the order the tailor shot the pages in. The job is created lazily before the first upload and
 * only after the server confirmed it ([NotebookImportRepository.createJob]).
 */
@Suppress("TooManyFunctions")
class NotebookCaptureViewModel(
    private val repository: NotebookImportRepository,
    private val activeWorkshop: ActiveWorkshopProvider,
    private val entitlements: EntitlementsProvider,
    private val imageCompressor: ImageCompressor,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val loaded = CompletableDeferred<Unit>()

    private var jobId: String? = null
    private var observeJob: Job? = null
    private var serverPages: List<ImportPage> = emptyList()
    private var localPages: List<LocalPage> = emptyList()
    private var localCounter = 0

    private val uploadMutex = Mutex()

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
            resumeOpenJob(uid)
        } finally {
            loaded.complete(Unit)
        }
    }

    /**
     * Adopts the newest open job only when it already has pages. An open job without pages (for
     * example one whose create timed out and synced later) is a fresh start, not a resume.
     */
    private suspend fun resumeOpenJob(uid: String) {
        val resumed = withTimeoutOrNull(RESUME_LOOKUP_TIMEOUT_MS) {
            val job = repository.observeOpenJob(uid).first() ?: return@withTimeoutOrNull null
            val pages = repository.observePages(uid, job.id).first()
            if (pages is Result.Success && pages.data.isNotEmpty()) job.id else null
        } ?: return
        jobId = resumed
        observePages(uid, resumed)
    }

    private fun observePages(uid: String, id: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observePages(uid, id).collect { result ->
                when (result) {
                    is Result.Success -> {
                        serverPages = result.data
                        publish()
                    }
                    is Result.Error -> AppLogger.w(TAG) { "observePages failed: ${result.error}" }
                }
            }
        }
    }

    private fun onPhotosPicked(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            if (ownerUidOrNull() == null) return@launch
            for (photo in photos) {
                val jpeg = imageCompressor.compress(photo, NOTEBOOK_MAX_EDGE_PX, NOTEBOOK_JPEG_QUALITY) ?: photo
                val page = LocalPage(id = "local-${localCounter++}", bytes = jpeg, phase = LocalPhase.UPLOADING)
                localPages = localPages + page
                publish()
                launch { upload(page.id) }
            }
        }
    }

    private suspend fun upload(localId: String) {
        val uid = ownerUidOrNull()
        if (uid == null) {
            updateLocal(localId) { it.with(LocalPhase.FAILED) }
            return
        }
        uploadMutex.withLock {
            loaded.await()
            val page = localPages.find { it.id == localId } ?: return
            val result = when (val job = ensureJob(uid)) {
                is Result.Success -> repository.uploadPage(uid, job.data, page.bytes)
                is Result.Error -> Result.Error(job.error)
            }
            when (result) {
                is Result.Success -> updateLocal(localId) { it.with(LocalPhase.UPLOADED, result.data) }
                is Result.Error -> {
                    AppLogger.w(TAG) { "upload failed: ${result.error}" }
                    updateLocal(localId) { it.with(LocalPhase.FAILED) }
                    showError(result.error)
                }
            }
        }
    }

    private suspend fun ensureJob(uid: String): Result<String, DataError.Network> {
        jobId?.let { return Result.Success(it) }
        val created = repository.createJob(uid)
        if (created is Result.Success) {
            jobId = created.data
            observePages(uid, created.data)
        }
        return created
    }

    private fun onRetry(key: String) {
        val local = localPages.find { it.id == key }
        if (local != null) {
            if (local.phase == LocalPhase.FAILED) {
                updateLocal(key) { it.with(LocalPhase.UPLOADING) }
                viewModelScope.launch { upload(key) }
            }
            return
        }
        val page = serverPages.find { it.id == key }
        val job = jobId
        if (page != null && job != null && page.isRetryable()) {
            viewModelScope.launch { retryServerPage(job, page.id) }
        }
    }

    private fun ImportPage.isRetryable() = status == PageStatus.FAILED && error != PAGE_CAP_REACHED

    private suspend fun retryServerPage(job: String, pageId: String) {
        val result = repository.retryPage(job, pageId)
        if (result is Result.Error) {
            AppLogger.w(TAG) { "retryPage failed: ${result.error}" }
            showError(result.error)
        }
    }

    private fun onReviewClick() {
        val job = jobId ?: return
        if (!_state.value.canReview) return
        send(NotebookCaptureEvent.NavigateToReview(job))
    }

    private fun updateLocal(id: String, change: (LocalPage) -> LocalPage) {
        localPages = localPages.map { if (it.id == id) change(it) else it }
        publish()
    }

    private fun showError(error: DataError.Network) {
        val text = when (error) {
            DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
            else -> UiText.StringResourceText(Res.string.error_unknown)
        }
        _state.update { it.copy(errorMessage = text) }
    }

    private fun publish() {
        val uploadedThumbs = localPages.filter { it.pageId != null }.associateBy { it.pageId }
        val serverIds = serverPages.mapTo(HashSet()) { it.id }

        val fromServer = serverPages.sortedBy { it.id }.map { page ->
            CapturePage(
                key = page.id,
                number = 0,
                status = page.toCaptureStatus(),
                personCount = page.personCount,
                thumb = uploadedThumbs[page.id]?.bytes,
            )
        }
        // Not on the server yet: still uploading, failed to upload, or uploaded but not synced back.
        val pending = localPages.filter { it.pageId == null || it.pageId !in serverIds }.map { page ->
            CapturePage(
                key = page.id,
                number = 0,
                status = if (page.phase == LocalPhase.FAILED) {
                    CapturePageStatus.UPLOAD_FAILED
                } else {
                    CapturePageStatus.UPLOADING
                },
                thumb = page.bytes,
            )
        }
        val pages = (fromServer + pending).mapIndexed { index, page -> page.numbered(index + 1) }
        _state.update { it.copy(pages = pages) }
    }

    private fun ImportPage.toCaptureStatus(): CapturePageStatus = when (status) {
        PageStatus.UPLOADING -> CapturePageStatus.UPLOADING
        PageStatus.PROCESSING -> CapturePageStatus.READING
        PageStatus.DONE -> CapturePageStatus.DONE
        PageStatus.FAILED ->
            if (error == PAGE_CAP_REACHED) CapturePageStatus.CAP_REACHED else CapturePageStatus.READ_FAILED
    }
}

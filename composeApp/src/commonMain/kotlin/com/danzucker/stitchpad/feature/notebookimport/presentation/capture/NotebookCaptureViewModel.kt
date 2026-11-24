package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.LocalUpload
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.UploadPhase
import com.danzucker.stitchpad.feature.notebookimport.domain.canStartNotebookImport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown

private const val TAG = "NotebookCapture"
private const val RESUME_LOOKUP_TIMEOUT_MS = 3_000L

/** Server error value on a page that was refused by the page cap. Retrying cannot help. */
private const val PAGE_CAP_REACHED = "page_cap_reached"

/**
 * Thin observer over [NotebookPageUploader] (which owns capture, compression and upload for the app's
 * lifetime) and the repository's pages. Never logs names, phones, values or image bytes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class NotebookCaptureViewModel(
    private val repository: NotebookImportRepository,
    private val activeWorkshop: ActiveWorkshopProvider,
    private val entitlements: EntitlementsProvider,
    private val uploader: NotebookPageUploader,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val loaded = CompletableDeferred<Unit>()
    private var serverPages: List<ImportPage> = emptyList()

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
            // A job already held by the uploader (earlier visit to this screen) is the live one.
            if (uploader.jobId.value == null) resumeOpenJob(uid)
            observe(uid)
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
        uploader.adoptJob(resumed)
    }

    private fun observe(uid: String) {
        viewModelScope.launch {
            uploader.jobId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else serverPagesOf(uid, id) }
                .combine(uploader.pages) { server, local ->
                    serverPages = server
                    buildPages(server, local)
                }
                .collect { pages -> _state.update { it.copy(pages = pages) } }
        }
        viewModelScope.launch {
            uploader.errors.collect { showError(it) }
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
            val uid = ownerUidOrNull() ?: return@launch
            uploader.submit(uid, photos)
        }
    }

    private fun onRetry(key: String) {
        val local = uploader.pages.value.find { it.id == key }
        if (local != null) {
            viewModelScope.launch { ownerUidOrNull()?.let { uploader.retry(it, key) } }
            return
        }
        val page = serverPages.find { it.id == key }
        val job = uploader.jobId.value
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
        val job = uploader.jobId.value ?: return
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

    private fun buildPages(server: List<ImportPage>, local: List<LocalUpload>): List<CapturePage> {
        val uploaded = local.filter { it.pageId != null }.associateBy { it.pageId }
        val serverIds = server.mapTo(HashSet()) { it.id }

        val fromServer = server.sortedBy { it.id }.map { page ->
            CapturePage(
                key = page.id,
                number = 0,
                status = page.toCaptureStatus(),
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

    private fun ImportPage.toCaptureStatus(): CapturePageStatus = when (status) {
        PageStatus.UPLOADING -> CapturePageStatus.UPLOADING
        PageStatus.PROCESSING -> CapturePageStatus.READING
        PageStatus.DONE -> CapturePageStatus.DONE
        PageStatus.FAILED ->
            if (error == PAGE_CAP_REACHED) CapturePageStatus.CAP_REACHED else CapturePageStatus.READ_FAILED
    }
}

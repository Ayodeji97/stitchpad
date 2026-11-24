package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.media.ImageCompressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "NotebookUploader"

// Handwriting needs more resolution than a style photo; values from the plan's Global Constraints.
private const val NOTEBOOK_MAX_EDGE_PX = 2560
private const val NOTEBOOK_JPEG_QUALITY = 90
private const val THUMB_MAX_EDGE_PX = 256
private const val THUMB_JPEG_QUALITY = 80
private const val ERROR_BUFFER = 8

enum class UploadPhase { UPLOADING, FAILED, UPLOADED }

/**
 * A page captured in this app session. [bytes] (the compressed full-size JPEG) is held only until the
 * page is uploaded; after that only the small [thumb] stays. [pageId] is set once uploaded.
 */
class LocalUpload(
    val id: String,
    val bytes: ByteArray?,
    val thumb: ByteArray?,
    val phase: UploadPhase,
    val pageId: String? = null,
) {
    /** What to show as the page image. */
    val display: ByteArray? get() = bytes ?: thumb
}

/**
 * App-lifetime upload pipeline for notebook pages, so uploads survive leaving the capture screen.
 * Process death still loses bytes that were not uploaded yet.
 *
 * One worker uploads strictly in capture order (server page ids encode upload time and carry the page
 * order). The job is created, server-confirmed, before the first upload. Never logs bytes or content.
 */
class NotebookPageUploader(
    private val repository: NotebookImportRepository,
    private val imageCompressor: ImageCompressor,
    private val scope: CoroutineScope,
) {
    private class Work(val ownerUid: String, val localId: String)

    private val _pages = MutableStateFlow<List<LocalUpload>>(emptyList())
    val pages: StateFlow<List<LocalUpload>> = _pages.asStateFlow()

    private val _jobId = MutableStateFlow<String?>(null)
    val jobId: StateFlow<String?> = _jobId.asStateFlow()

    private val _errors = MutableSharedFlow<DataError.Network>(extraBufferCapacity = ERROR_BUFFER)

    /** One emission per burst of failures: a batch failing the same way is reported once. */
    val errors: SharedFlow<DataError.Network> = _errors.asSharedFlow()

    private val queue = Channel<Work>(Channel.UNLIMITED)
    private var counter = 0
    private var errorReported = false

    init {
        scope.launch {
            for (work in queue) process(work)
        }
    }

    /** Adopts a job found on resume. Ignored when this session already has a job. */
    fun adoptJob(id: String) {
        _jobId.compareAndSet(null, id)
    }

    /** Compresses and queues [photos] in order. */
    fun submit(ownerUid: String, photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        errorReported = false
        scope.launch {
            for (photo in photos) {
                val jpeg = imageCompressor.compress(photo, NOTEBOOK_MAX_EDGE_PX, NOTEBOOK_JPEG_QUALITY) ?: photo
                val id = "local-${counter++}"
                _pages.update { it + LocalUpload(id, jpeg, null, UploadPhase.UPLOADING) }
                queue.send(Work(ownerUid, id))
            }
        }
    }

    /** Re-queues a page whose upload failed. */
    fun retry(ownerUid: String, localId: String) {
        val page = _pages.value.find { it.id == localId }
        if (page == null || page.phase != UploadPhase.FAILED) return
        errorReported = false
        change(localId) { LocalUpload(it.id, it.bytes, it.thumb, UploadPhase.UPLOADING, it.pageId) }
        queue.trySend(Work(ownerUid, localId))
    }

    /** The job was saved or abandoned: drop its pending pages and forget it. */
    fun clear(finishedJobId: String) {
        if (_jobId.value != finishedJobId) return
        _jobId.value = null
        _pages.value = emptyList()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun process(work: Work) {
        val page = _pages.value.find { it.id == work.localId }
        val bytes = page?.bytes
        if (page == null || bytes == null || page.phase != UploadPhase.UPLOADING) return
        try {
            val result = when (val job = ensureJob(work.ownerUid)) {
                is Result.Success -> repository.uploadPage(work.ownerUid, job.data, bytes)
                is Result.Error -> Result.Error(job.error)
            }
            when (result) {
                is Result.Success -> onUploaded(work.localId, result.data, bytes)
                is Result.Error -> onFailed(work.localId, result.error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, e) { "upload crashed: ${e::class.simpleName}" }
            onFailed(work.localId, DataError.Network.UNKNOWN)
        }
    }

    private suspend fun ensureJob(ownerUid: String): Result<String, DataError.Network> {
        _jobId.value?.let { return Result.Success(it) }
        val created = repository.createJob(ownerUid)
        if (created is Result.Success) _jobId.value = created.data
        return created
    }

    private suspend fun onUploaded(localId: String, pageId: String, bytes: ByteArray) {
        errorReported = false
        val thumb = imageCompressor.compress(bytes, THUMB_MAX_EDGE_PX, THUMB_JPEG_QUALITY)
        change(localId) { LocalUpload(it.id, null, thumb, UploadPhase.UPLOADED, pageId) }
    }

    private fun onFailed(localId: String, error: DataError.Network) {
        AppLogger.w(TAG) { "upload failed: $error" }
        change(localId) { LocalUpload(it.id, it.bytes, it.thumb, UploadPhase.FAILED, it.pageId) }
        if (!errorReported) {
            errorReported = true
            _errors.tryEmit(error)
        }
    }

    private fun change(id: String, transform: (LocalUpload) -> LocalUpload) {
        _pages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}

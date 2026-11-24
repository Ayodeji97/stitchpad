package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.media.ImageCompressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

private const val TAG = "NotebookUploader"

// Handwriting needs more resolution than a style photo; values from the plan's Global Constraints.
private const val NOTEBOOK_MAX_EDGE_PX = 2560
private const val NOTEBOOK_JPEG_QUALITY = 90
private const val THUMB_MAX_EDGE_PX = 256
private const val THUMB_JPEG_QUALITY = 80
private const val ERROR_BUFFER = 8

enum class UploadPhase { UPLOADING, FAILED, UPLOADED }

/** Why a page did not upload. */
sealed interface UploadFailure {
    data class Network(val error: DataError.Network) : UploadFailure

    /** The photo could not be compressed (unreadable image). It is never uploaded raw. */
    data object PhotoUnreadable : UploadFailure
}

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
 * Everything is bound to the signed-in owner's uid. On sign-out or an account switch all state (queue,
 * bytes, thumbnails, job id) is dropped, nothing queued for a previous uid is ever uploaded, and the
 * exposed flows show nothing for any uid but the bound one. Before every upload the worker re-checks
 * that the session is still the owner and [canStartNotebookImport] still holds.
 *
 * One worker uploads strictly in capture order (server page ids encode upload time and carry the page
 * order); order and ids are fixed synchronously in [submit]. The job is created, server-confirmed,
 * before the first upload. A job whose images are expired (see [ImportJob.isImageExpired]) never takes
 * new uploads: it is not adopted, and a held job that turns 7 days old is replaced by a new one.
 * Never logs bytes or content.
 */
@Suppress("TooManyFunctions")
class NotebookPageUploader(
    private val repository: NotebookImportRepository,
    private val imageCompressor: ImageCompressor,
    private val activeWorkshop: ActiveWorkshopProvider,
    private val entitlements: EntitlementsProvider,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private class Work(val ownerUid: String, val localId: String)

    /** All mutable state in one atomically updated value. [nextId] never resets, so ids never collide. */
    private class Holder(
        val ownerUid: String? = null,
        val jobId: String? = null,
        val pages: List<LocalUpload> = emptyList(),
        val nextId: Int = 0,
        val errorReported: Boolean = false,
        /** When [jobId] was created, to stop using it once its images expire. */
        val jobCreatedAtMillis: Long? = null,
    ) {
        @Suppress("LongParameterList")
        fun copy(
            ownerUid: String? = this.ownerUid,
            jobId: String? = this.jobId,
            pages: List<LocalUpload> = this.pages,
            nextId: Int = this.nextId,
            errorReported: Boolean = this.errorReported,
            jobCreatedAtMillis: Long? = this.jobCreatedAtMillis,
        ) = Holder(ownerUid, jobId, pages, nextId, errorReported, jobCreatedAtMillis)

        fun emptied() = Holder(nextId = nextId)
    }

    private val holder = MutableStateFlow(Holder())

    /** Pages of the current owner only; empty for anyone else. */
    val pages: StateFlow<List<LocalUpload>> =
        combine(holder, activeWorkshop.flow) { h, s -> if (h.ownerUid == s.uidOrNull()) h.pages else emptyList() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Job of the current owner only; null for anyone else. */
    val jobId: StateFlow<String?> =
        combine(holder, activeWorkshop.flow) { h, s -> if (h.ownerUid == s.uidOrNull()) h.jobId else null }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val _errors = MutableSharedFlow<UploadFailure>(extraBufferCapacity = ERROR_BUFFER)

    /** One emission per burst of failures: a batch failing the same way is reported once. */
    val errors: SharedFlow<UploadFailure> = _errors.asSharedFlow()

    private val queue = Channel<Work>(Channel.UNLIMITED)

    init {
        scope.launch {
            activeWorkshop.flow.collect { session ->
                val bound = holder.value.ownerUid
                if (bound != null && bound != session.uidOrNull()) holder.update { it.emptied() }
            }
        }
        scope.launch {
            for (work in queue) process(work)
        }
    }

    private fun WorkshopSession.uidOrNull(): String? =
        workshopUid.takeIf { it.isNotBlank() }

    /** The signed-in owner's uid when they may run an import right now, else null. */
    private fun allowedUid(): String? {
        val session = activeWorkshop.current()
        return if (canStartNotebookImport(session, entitlements.current())) session.uidOrNull() else null
    }

    /** Job id for the current owner, read synchronously. */
    fun currentJobId(): String? {
        val uid = activeWorkshop.current().uidOrNull()
        return holder.value.takeIf { it.ownerUid == uid }?.jobId
    }

    /** True when [localId] is a page of the current owner. */
    fun hasPage(localId: String): Boolean {
        val uid = activeWorkshop.current().uidOrNull()
        return holder.value.let { h -> h.ownerUid == uid && h.pages.any { it.id == localId } }
    }

    /**
     * Adopts a job found on resume. Ignored when this owner already has a job, or when the job's images
     * are expired (it must not take new uploads).
     */
    fun adoptJob(job: ImportJob) {
        val uid = allowedUid() ?: return
        if (job.isImageExpired(nowMillis())) return
        holder.update {
            val base = if (it.ownerUid == uid) it else it.emptied()
            if (base.jobId == null) {
                base.copy(ownerUid = uid, jobId = job.id, jobCreatedAtMillis = job.createdAt.toEpochMilliseconds())
            } else {
                base
            }
        }
    }

    /** Queues [photos] in order. Ids and order are fixed here, synchronously; compression runs in the worker. */
    fun submit(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        val uid = allowedUid() ?: return
        val before = holder.getAndUpdate { h ->
            val base = if (h.ownerUid == uid) h else h.emptied()
            val added = photos.mapIndexed { i, bytes ->
                LocalUpload("local-${base.nextId + i}", bytes, null, UploadPhase.UPLOADING)
            }
            base.copy(
                ownerUid = uid,
                pages = base.pages + added,
                nextId = base.nextId + photos.size,
                errorReported = false,
            )
        }
        for (i in photos.indices) queue.trySend(Work(uid, "local-${before.nextId + i}"))
    }

    /** Re-queues a page whose upload failed. */
    fun retry(localId: String) {
        val uid = allowedUid() ?: return
        var queued = false
        holder.update { h ->
            val page = h.pages.find { it.id == localId }
            queued = h.ownerUid == uid && page != null && page.phase == UploadPhase.FAILED
            if (!queued) {
                h
            } else {
                h.copy(
                    pages = h.pages.map { if (it.id == localId) it.withPhase(UploadPhase.UPLOADING) else it },
                    errorReported = false,
                )
            }
        }
        if (queued) queue.trySend(Work(uid, localId))
    }

    /** The job was saved or abandoned: drop its pending pages and forget it. */
    fun clear(finishedJobId: String) {
        holder.update { if (it.jobId == finishedJobId) it.emptied() else it }
    }

    private fun dropFor(uid: String) {
        holder.update { if (it.ownerUid == uid) it.emptied() else it }
    }

    private fun LocalUpload.withPhase(phase: UploadPhase) = LocalUpload(id, bytes, thumb, phase, pageId)

    private fun pageOf(work: Work): LocalUpload? =
        holder.value.takeIf { it.ownerUid == work.ownerUid }?.pages?.find { it.id == work.localId }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun process(work: Work) {
        // Re-check before every upload: still signed in as this uid, still the owner, still allowed.
        if (allowedUid() != work.ownerUid) {
            dropFor(work.ownerUid)
            return
        }
        val raw = pageOf(work)?.takeIf { it.phase == UploadPhase.UPLOADING }?.bytes ?: return
        try {
            // Never upload the raw photo: it can be far over the 6 MB Storage limit and full resolution.
            val jpeg = imageCompressor.compress(raw, NOTEBOOK_MAX_EDGE_PX, NOTEBOOK_JPEG_QUALITY)
            if (jpeg == null) {
                onFailed(work, UploadFailure.PhotoUnreadable)
                return
            }
            val result = when (val job = ensureJob(work.ownerUid)) {
                is Result.Success -> repository.uploadPage(work.ownerUid, job.data, jpeg)
                is Result.Error -> Result.Error(job.error)
            }
            when (result) {
                is Result.Success -> onUploaded(work, result.data, jpeg)
                is Result.Error -> onFailed(work, UploadFailure.Network(result.error))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, e) { "upload crashed: ${e::class.simpleName}" }
            onFailed(work, UploadFailure.Network(DataError.Network.UNKNOWN))
        }
    }

    private suspend fun ensureJob(ownerUid: String): Result<String, DataError.Network> {
        val held = holder.value.takeIf { it.ownerUid == ownerUid }
        val existing = held?.jobId
        if (existing != null && !held.isJobExpired()) return Result.Success(existing)
        if (existing != null) {
            // The held job's images are about to be swept: its uploaded pages stay with it (reachable from
            // the earlier-scan banner) and new pages start a new job.
            AppLogger.i(TAG) { "held job expired jobId=$existing; starting a new one" }
            holder.update { h ->
                if (h.ownerUid != ownerUid || h.jobId != existing) {
                    h
                } else {
                    h.copy(jobId = null, jobCreatedAtMillis = null, pages = h.pages.filter { it.pageId == null })
                }
            }
        }
        val createdAtMillis = nowMillis()
        val created = repository.createJob(ownerUid)
        var bound = false
        if (created is Result.Success) {
            holder.update {
                bound = it.ownerUid == ownerUid
                if (bound) it.copy(jobId = created.data, jobCreatedAtMillis = createdAtMillis) else it
            }
        }
        // If the owner changed while the job was being created, do not upload into it.
        return if (created is Result.Success && !bound) Result.Error(DataError.Network.UNAUTHORIZED) else created
    }

    private fun Holder.isJobExpired(): Boolean {
        val created = jobCreatedAtMillis ?: return false
        return nowMillis() - created >= JOB_IMAGE_TTL_MILLIS
    }

    private suspend fun onUploaded(work: Work, pageId: String, jpeg: ByteArray) {
        val thumb = imageCompressor.compress(jpeg, THUMB_MAX_EDGE_PX, THUMB_JPEG_QUALITY)
        change(work) { LocalUpload(it.id, null, thumb, UploadPhase.UPLOADED, pageId) }
        holder.update { if (it.ownerUid == work.ownerUid) it.copy(errorReported = false) else it }
    }

    private fun onFailed(work: Work, error: UploadFailure) {
        AppLogger.w(TAG) { "upload failed: $error" }
        change(work) { it.withPhase(UploadPhase.FAILED) }
        var report = false
        holder.update {
            report = it.ownerUid == work.ownerUid && !it.errorReported
            if (report) it.copy(errorReported = true) else it
        }
        if (report) _errors.tryEmit(error)
    }

    private fun change(work: Work, transform: (LocalUpload) -> LocalUpload) {
        holder.update { h ->
            if (h.ownerUid != work.ownerUid) {
                h
            } else {
                h.copy(pages = h.pages.map { if (it.id == work.localId) transform(it) else it })
            }
        }
    }
}

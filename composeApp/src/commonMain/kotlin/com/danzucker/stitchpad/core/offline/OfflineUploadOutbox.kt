package com.danzucker.stitchpad.core.offline

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.data.mapper.toBaseDto
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.order.data.orderItemBaseWriteFields
import com.danzucker.stitchpad.feature.style.data.toStorageData
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.time.Clock

private const val TAG = "UploadOutbox"
private const val MAX_ATTEMPTS = 10
private const val MAX_BACKOFF_MS = 30 * 60 * 1_000L
private const val SYNC_STATE_SYNCED = "SYNCED"

@Suppress("TooManyFunctions")
class OfflineUploadOutbox(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val photoStore: OfflinePhotoStore,
    private val scheduler: OfflineUploadScheduler,
    private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val drainMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var hasStarted = false
    private var localPathByStoragePath: Map<String, String> = emptyMap()
    private var completedUrlByStoragePath: Map<String, String> = emptyMap()

    fun ensureRunning() {
        if (hasStarted) return
        hasStarted = true
        drainInBackground()
    }

    fun localPathForStoragePath(storagePath: String): String? =
        localPathByStoragePath[storagePath]

    fun completedUrlForStoragePath(storagePath: String): String? =
        completedUrlByStoragePath[storagePath]

    suspend fun enqueue(job: OfflineUploadJob) {
        mutex.withLock {
            val jobs = readJobsLocked().filterNot { it.id == job.id } + job
            writeJobsLocked(jobs)
        }
        scheduler.schedule()
        drainInBackground()
    }

    suspend fun cancel(jobId: String): OfflineUploadJob? =
        mutex.withLock {
            val jobs = readJobsLocked()
            val cancelled = jobs.firstOrNull { it.id == jobId }
            if (cancelled != null) {
                writeJobsLocked(jobs.filterNot { it.id == jobId })
                cancelled.localPath?.let { photoStore.delete(it) }
            }
            cancelled
        }

    fun drainInBackground() {
        appScope.launch {
            drain()
        }
    }

    suspend fun drain() {
        if (!drainMutex.tryLock()) return
        try {
            while (true) {
                val job = nextRunnableJob() ?: break
                val result = runJob(job)
                applyJobResult(job, result)
            }
            scheduleNextRetry()
        } finally {
            drainMutex.unlock()
        }
    }

    private suspend fun nextRunnableJob(): OfflineUploadJob? =
        mutex.withLock {
            val now = nowMs()
            readJobsLocked().firstOrNull { it.nextAttemptAt <= now && it.attempts < MAX_ATTEMPTS }
        }

    private suspend fun applyJobResult(job: OfflineUploadJob, result: JobResult) {
        mutex.withLock {
            val now = nowMs()
            val remaining = readJobsLocked().toMutableList()
            val index = remaining.indexOfFirst { it.id == job.id }
            if (index == -1 || remaining[index] != job) return
            when (result) {
                JobResult.Success -> remaining.removeAt(index)
                is JobResult.Retry -> {
                    remaining[index] = job.copy(
                        attempts = job.attempts + 1,
                        nextAttemptAt = now + retryDelay(job.attempts + 1),
                        lastError = result.reason.take(180),
                        updatedAt = now,
                    )
                }
            }
            writeJobsLocked(remaining)
        }
    }

    private suspend fun scheduleNextRetry() {
        val now = nowMs()
        val nextRetryAt = mutex.withLock {
            readJobsLocked()
                .filter { it.nextAttemptAt > now && it.attempts < MAX_ATTEMPTS }
                .minOfOrNull { it.nextAttemptAt }
        }
        if (nextRetryAt != null) {
            scheduler.schedule(delayMs = nextRetryAt - now)
        }
    }

    private suspend fun runJob(job: OfflineUploadJob): JobResult =
        try {
            when (job.type) {
                OfflineUploadJobType.ORDER_FABRIC_IMAGE -> {
                    val downloadUrl = upload(job)
                    patchOrderImage(job, downloadUrl, isFabric = true)
                    photoStore.delete(job.localPath.orEmpty())
                }
                OfflineUploadJobType.ORDER_STYLE_IMAGE -> {
                    val downloadUrl = upload(job)
                    patchOrderImage(job, downloadUrl, isFabric = false)
                    photoStore.delete(job.localPath.orEmpty())
                }
                OfflineUploadJobType.STYLE_GALLERY_IMAGE -> {
                    val downloadUrl = upload(job)
                    patchStyleImage(job, downloadUrl)
                    photoStore.delete(job.localPath.orEmpty())
                }
                OfflineUploadJobType.PROFILE_LOGO -> {
                    val downloadUrl = upload(job)
                    patchProfileLogo(job, downloadUrl)
                    photoStore.delete(job.localPath.orEmpty())
                }
                OfflineUploadJobType.STORAGE_DELETE -> {
                    deleteStorageObject(job.storagePath)
                }
            }
            JobResult.Success
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.w(tag = TAG, throwable = e) { "upload job failed type=${job.type} id=${job.id}" }
            JobResult.Retry(e.message ?: e::class.simpleName.orEmpty())
        }

    private suspend fun upload(job: OfflineUploadJob): String {
        val localPath = requireNotNull(job.localPath) { "Upload job has no local file" }
        val bytes = photoStore.read(localPath)
        storage.reference.child(job.storagePath).putData(bytes.toStorageData())
        return storage.reference.child(job.storagePath).getDownloadUrl()
    }

    private suspend fun deleteStorageObject(storagePath: String) {
        try {
            storage.reference.child(storagePath).delete()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (!e.isMissingStorageObject()) throw e
            AppLogger.w(tag = TAG, throwable = e) {
                "storage delete skipped missing object path=$storagePath"
            }
        }
    }

    @Suppress("SpreadOperator") // same GitLive vararg constraint as OrderRepository.updateItems
    private suspend fun patchOrderImage(
        job: OfflineUploadJob,
        downloadUrl: String,
        isFabric: Boolean,
    ) {
        val docRef = firestore.collection("users")
            .document(job.userId)
            .collection("orders")
            .document(requireNotNull(job.orderId))
        firestore.runTransaction {
            val snapshot = get(docRef)
            if (!snapshot.exists) error("Order document is unavailable")
            val fields = orderImagePatchFields(
                items = snapshot.data<OrderDto>().items,
                itemId = job.itemId,
                storagePath = job.storagePath,
                downloadUrl = downloadUrl,
                isFabric = isFabric,
                now = nowMs(),
            ) ?: error("Pending order image ref is unavailable")
            // Field-path update, NOT a whole-DTO merge set. The document is guaranteed
            // to exist here (the ViewModel wrote the PENDING image ref before the job
            // was enqueued, and the `snapshot.exists` check above re-confirms it), so
            // update() is safe — and it is the only shape that keeps this write inside
            // the Firestore staff work-fields whitelist
            // (status/subStatus/statusHistory/updatedAt/items/notes).
            //
            // The old code did `set(dto.toBaseDto(), merge = true)`. GitLive encodes
            // defaults, so every OrderBaseDto key rode on the wire; on any order created
            // before Phase 2a (no assignedMemberId/assignedMemberName; older docs also
            // no subStatus/archivedAt) the merge ADDED those keys, so
            // diff().affectedKeys() escaped the whitelist and every staff photo upload
            // was permission-denied forever — silently, since the Storage upload itself
            // succeeded. Verified in the rules emulator; pinned by the
            // "staff outbox photo patch" tests in firestore.rules.test.ts.
            //
            // Slice 8d-1 (stop-dual-write) still holds: the payload is items+updatedAt
            // only, items serialized through the money-free OrderItemBaseDto shape, so
            // an image completion can never re-mirror money onto the base doc. Legacy
            // TOP-LEVEL base money is now not merely preserved but untouched (it is not
            // in the payload at all); `items` remains a whole-array replacement, so any
            // legacy items[].price is wiped here — same accepted caveat as before.
            update(docRef, *fields.entries.map { it.key to it.value }.toTypedArray())
        }
        rememberCompletedUpload(job.storagePath, downloadUrl)
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun patchStyleImage(
        job: OfflineUploadJob,
        downloadUrl: String,
    ) {
        val docRef = if (job.inspirationStyle) {
            val folderId = job.folderId
            if (folderId != null) {
                firestore.collection("users").document(job.userId)
                    .collection("inspirationFolders").document(folderId)
                    .collection("styles").document(requireNotNull(job.styleId))
            } else {
                firestore.collection("users").document(job.userId)
                    .collection("inspiration").document(requireNotNull(job.styleId))
            }
        } else {
            val folderId = job.folderId
            if (folderId != null) {
                firestore.collection("users").document(job.userId)
                    .collection("customers").document(requireNotNull(job.customerId))
                    .collection("styleFolders").document(folderId)
                    .collection("styles").document(requireNotNull(job.styleId))
            } else {
                firestore.collection("users").document(job.userId)
                    .collection("customers").document(requireNotNull(job.customerId))
                    .collection("styles").document(requireNotNull(job.styleId))
            }
        }
        if (!docRef.get().exists) {
            runCatching { storage.reference.child(job.storagePath).delete() }
            return
        }
        docRef
            .set(
                mapOf(
                    "photoUrl" to downloadUrl,
                    "photoStoragePath" to job.storagePath,
                    "syncState" to "SYNCED",
                    "localPhotoPath" to FieldValue.delete,
                    "updatedAt" to nowMs(),
                ),
                merge = true,
            )
    }

    private suspend fun patchProfileLogo(
        job: OfflineUploadJob,
        downloadUrl: String,
    ) {
        val docRef = firestore.collection("users")
            .document(job.userId)
        var patched = false
        firestore.runTransaction {
            patched = false
            val snapshot = get(docRef)
            if (!snapshot.exists) return@runTransaction
            val dto = snapshot.data<UserDto>()
            if (dto.businessLogoStoragePath != job.storagePath || dto.businessLogoUploadId != job.id) {
                return@runTransaction
            }
            set(
                docRef,
                mapOf(
                    "businessLogoUrl" to downloadUrl,
                    "businessLogoStoragePath" to job.storagePath,
                    "businessLogoUploadId" to FieldValue.delete,
                    "updatedAt" to FieldValue.serverTimestamp,
                ),
                merge = true,
            )
            patched = true
        }
        if (!patched) {
            deleteStorageObject(job.storagePath)
        }
    }

    private suspend fun readJobsLocked(): List<OfflineUploadJob> =
        photoStore.readUploadJobs()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<List<OfflineUploadJob>>(it) }.getOrNull() }
            .orEmpty()
            .also(::indexLocalPaths)

    private suspend fun writeJobsLocked(jobs: List<OfflineUploadJob>) {
        photoStore.writeUploadJobs(json.encodeToString(jobs))
        indexLocalPaths(jobs)
    }

    private fun indexLocalPaths(jobs: List<OfflineUploadJob>) {
        localPathByStoragePath = jobs
            .mapNotNull { job -> job.localPath?.let { job.storagePath to it } }
            .toMap()
    }

    private fun rememberCompletedUpload(storagePath: String, downloadUrl: String) {
        completedUrlByStoragePath = completedUrlByStoragePath + (storagePath to downloadUrl)
    }

    private fun retryDelay(attempt: Int): Long {
        val base = 2_000L * (1 shl min(attempt, 8))
        return min(base, MAX_BACKOFF_MS)
    }

    private fun Exception.isMissingStorageObject(): Boolean {
        val haystack = listOfNotNull(message, cause?.message, toString()).joinToString(" ").lowercase()
        return "object-not-found" in haystack ||
            "object does not exist" in haystack ||
            "no object exists" in haystack ||
            "storage/object_not_found" in haystack
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * Builds the Firestore payload that marks a just-uploaded order photo as SYNCED.
 *
 * Pure by design so the wire shape is unit-testable without a Firestore fake (see
 * `OrderImagePatchFieldsTest`): this is the payload that must stay inside the staff
 * work-fields whitelist, and the review found it had silently drifted out of it.
 * Delegates to [orderItemBaseWriteFields] so the items-only write has exactly ONE key
 * list, shared with the repository's own items write
 * ([com.danzucker.stitchpad.feature.order.data.orderItemsWriteFields], used by
 * `OrderRepository.updateItems`).
 *
 * Returns `null` when no PENDING ref on [itemId] matches [storagePath] — the caller
 * aborts the transaction, which the outbox records as a retryable failure (identical
 * to the pre-fix `error("Pending order image ref is unavailable")` behaviour).
 */
internal fun orderImagePatchFields(
    items: List<OrderItemDto>,
    itemId: String?,
    storagePath: String,
    downloadUrl: String,
    isFabric: Boolean,
    now: Long,
): Map<String, Any?>? {
    var patched = false
    val updatedItems = items.map { item ->
        if (item.id != itemId) return@map item
        val next = if (isFabric) {
            item.withSyncedFabricImage(storagePath, downloadUrl)
        } else {
            item.withSyncedStyleImage(storagePath, downloadUrl)
        }
        if (next != null) patched = true
        next ?: item
    }
    if (!patched) return null
    return orderItemBaseWriteFields(updatedItems.map { it.toBaseDto() }, now)
}

/**
 * Marks the fabric ref at [storagePath] SYNCED (and follows the legacy single
 * `fabricPhotoUrl` field when it points at the same object). `null` when this item has
 * no such ref — a legacy-single-field-only match does NOT count, matching the previous
 * transaction's `patched` flag exactly.
 */
private fun OrderItemDto.withSyncedFabricImage(storagePath: String, downloadUrl: String): OrderItemDto? {
    if (fabricImages.none { it.photoStoragePath == storagePath }) return null
    return copy(
        fabricImages = fabricImages.map { ref ->
            if (ref.photoStoragePath == storagePath) {
                ref.copy(photoUrl = downloadUrl, syncState = SYNC_STATE_SYNCED)
            } else {
                ref
            }
        },
        fabricPhotoUrl = if (fabricPhotoStoragePath == storagePath) downloadUrl else fabricPhotoUrl,
    )
}

/** Style-side twin of [withSyncedFabricImage]. */
private fun OrderItemDto.withSyncedStyleImage(storagePath: String, downloadUrl: String): OrderItemDto? {
    if (styleImages.none { it.photoStoragePath == storagePath }) return null
    return copy(
        styleImages = styleImages.map { ref ->
            if (ref.photoStoragePath == storagePath) {
                ref.copy(photoUrl = downloadUrl, syncState = SYNC_STATE_SYNCED)
            } else {
                ref
            }
        },
        stylePhotoUrl = if (stylePhotoStoragePath == storagePath) downloadUrl else stylePhotoUrl,
    )
}

@Serializable
data class OfflineUploadJob(
    val id: String,
    val type: OfflineUploadJobType,
    val userId: String,
    val storagePath: String,
    val localPath: String? = null,
    val customerId: String? = null,
    val orderId: String? = null,
    val itemId: String? = null,
    val styleId: String? = null,
    val inspirationStyle: Boolean = false,
    val folderId: String? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
    val lastError: String? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = createdAt,
)

@Serializable
enum class OfflineUploadJobType {
    ORDER_FABRIC_IMAGE,
    ORDER_STYLE_IMAGE,
    STYLE_GALLERY_IMAGE,
    PROFILE_LOGO,
    STORAGE_DELETE,
}

private sealed interface JobResult {
    data object Success : JobResult
    data class Retry(val reason: String) : JobResult
}

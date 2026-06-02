package com.danzucker.stitchpad.core.offline

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.logging.AppLogger
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
            val dto = snapshot.data<OrderDto>()
            var patched = false
            val updatedItems = dto.items.map { item ->
                if (item.id != job.itemId) return@map item
                if (isFabric) {
                    item.copy(
                        fabricImages = item.fabricImages.map { ref ->
                            if (ref.photoStoragePath == job.storagePath) {
                                patched = true
                                ref.copy(
                                    photoUrl = downloadUrl,
                                    syncState = "SYNCED",
                                )
                            } else {
                                ref
                            }
                        },
                        fabricPhotoUrl = if (item.fabricPhotoStoragePath == job.storagePath) {
                            downloadUrl
                        } else {
                            item.fabricPhotoUrl
                        },
                    )
                } else {
                    item.copy(
                        styleImages = item.styleImages.map { ref ->
                            if (ref.photoStoragePath == job.storagePath) {
                                patched = true
                                ref.copy(
                                    photoUrl = downloadUrl,
                                    syncState = "SYNCED",
                                )
                            } else {
                                ref
                            }
                        },
                        stylePhotoUrl = if (item.stylePhotoStoragePath == job.storagePath) {
                            downloadUrl
                        } else {
                            item.stylePhotoUrl
                        },
                    )
                }
            }
            if (!patched) error("Pending order image ref is unavailable")
            set(docRef, dto.copy(items = updatedItems, updatedAt = nowMs()))
        }
        rememberCompletedUpload(job.storagePath, downloadUrl)
    }

    private suspend fun patchStyleImage(
        job: OfflineUploadJob,
        downloadUrl: String,
    ) {
        val docRef = firestore.collection("users")
            .document(job.userId)
            .collection("customers")
            .document(requireNotNull(job.customerId))
            .collection("styles")
            .document(requireNotNull(job.styleId))
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

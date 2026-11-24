package com.danzucker.stitchpad.feature.notebookimport.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "NotebookImportWriter"
internal const val CREATE_JOB_TIMEOUT_MS = 10_000L
internal const val IMAGE_CLEANUP_TIMEOUT_MS = 5_000L

/** The raw Firestore/Storage calls, kept behind a seam so the offline policy can be tested with a fake. */
internal interface NotebookImportRemote {
    /** Creates the job doc with exactly the keys the rules allow and returns its id. */
    suspend fun createJobDoc(ownerUid: String): String
    suspend fun updateCardDoc(ownerUid: String, card: ReviewCard)
    suspend fun setJobStatus(ownerUid: String, jobId: String, status: String)
    suspend fun deleteJobImages(ownerUid: String, jobId: String)
    suspend fun deleteCardDoc(ownerUid: String, jobId: String, cardId: String)
}

/**
 * Offline policy for the write paths. createJob must be confirmed by the server (the Storage rule reads
 * the job doc), so it is bounded and reports NO_INTERNET on timeout. Card and status writes go through
 * the app's offline queue like every other repository, so they never hang the caller.
 */
internal class NotebookImportWriter(
    private val remote: NotebookImportRemote,
    private val offlineWrites: OfflineWriteDispatcher,
    private val createTimeoutMs: Long = CREATE_JOB_TIMEOUT_MS,
    private val cleanupTimeoutMs: Long = IMAGE_CLEANUP_TIMEOUT_MS,
) {
    suspend fun createJob(ownerUid: String): Result<String, DataError.Network> =
        try {
            withTimeoutOrNull(createTimeoutMs) { remote.createJobDoc(ownerUid) }
                ?.let { Result.Success(it) }
                ?: Result.Error(DataError.Network.NO_INTERNET)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "createJob failed: ${e::class.simpleName}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network> =
        queued("updateCard cardId=${card.id}") { remote.updateCardDoc(ownerUid, card) }

    suspend fun deleteCard(ownerUid: String, jobId: String, cardId: String): EmptyResult<DataError.Network> =
        queued("deleteCard cardId=$cardId") { remote.deleteCardDoc(ownerUid, jobId, cardId) }

    suspend fun finishJob(ownerUid: String, jobId: String, abandoned: Boolean): EmptyResult<DataError.Network> {
        if (abandoned) deleteImagesBestEffort(ownerUid, jobId)
        val status = if (abandoned) "abandoned" else "saved"
        return queued("finishJob jobId=$jobId") { remote.setJobStatus(ownerUid, jobId, status) }
    }

    private suspend fun queued(name: String, block: suspend () -> Unit): EmptyResult<DataError.Network> =
        if (offlineWrites.enqueue(name, block)) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)

    private suspend fun deleteImagesBestEffort(ownerUid: String, jobId: String) {
        try {
            withTimeoutOrNull(cleanupTimeoutMs) { remote.deleteJobImages(ownerUid, jobId) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.w(tag = TAG, throwable = e) { "image cleanup failed jobId=$jobId" }
        }
    }
}

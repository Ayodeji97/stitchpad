package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface NotebookImportRepository {
    /**
     * Creates the job doc `{ createdAt, status:'open', pageCount:0 }` and returns its id.
     * Must complete BEFORE the first [uploadPage]: Storage rules reject uploads to a
     * missing or non-open job.
     */
    suspend fun createJob(ownerUid: String): Result<String, DataError.Network>

    /** Uploads one page JPEG to an open job created by [createJob]; returns the page id. */
    suspend fun uploadPage(ownerUid: String, jobId: String, jpeg: ByteArray): Result<String, DataError.Network>

    /** Pages of the job. [ImportPage.localThumb] is always null here. */
    fun observePages(ownerUid: String, jobId: String): Flow<Result<List<ImportPage>, DataError.Network>>

    fun observeCards(ownerUid: String, jobId: String): Flow<Result<List<ReviewCard>, DataError.Network>>

    /** Newest job with status open, for resume. */
    fun observeOpenJob(ownerUid: String): Flow<ImportJob?>

    /** One job by id (any status); null when it does not exist. Carries [ImportJob.imagesDeletedAt]. */
    fun observeJob(ownerUid: String, jobId: String): Flow<ImportJob?>

    /** Download URL of one page photo. Fails once the images were deleted (7 days, or after the job ends). */
    suspend fun pageImageUrl(ownerUid: String, jobId: String, pageId: String): Result<String, DataError.Network>

    suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network>

    suspend fun deleteCard(ownerUid: String, jobId: String, cardId: String): EmptyResult<DataError.Network>

    suspend fun retryPage(jobId: String, pageId: String): EmptyResult<DataError.Network>

    /** Sets job status to 'saved' or 'abandoned' (the only client-writable job change). */
    suspend fun finishJob(ownerUid: String, jobId: String, abandoned: Boolean): EmptyResult<DataError.Network>
}

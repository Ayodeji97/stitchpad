package com.danzucker.stitchpad.feature.notebookimport.data

import com.danzucker.stitchpad.core.data.retryWithFallback
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.style.data.toStorageData
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.FirebaseStorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import kotlin.time.Clock

private const val TAG = "NotebookImportRepo"
private const val PAGE_TIME_WIDTH = 13
private const val RANDOM_SUFFIX_BYTES = 4

/** Logs only ids and exception types: never names, phones or values. */
@Suppress("TooManyFunctions")
class FirebaseNotebookImportRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions,
    offlineWrites: OfflineWriteDispatcher,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : NotebookImportRepository {

    private val writer = NotebookImportWriter(FirebaseRemote(), offlineWrites)

    private fun jobsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("notebookImports")

    private fun jobDoc(uid: String, jobId: String) = jobsCollection(uid).document(jobId)

    private fun cardsCollection(uid: String, jobId: String) = jobDoc(uid, jobId).collection("cards")

    private fun storagePrefix(uid: String, jobId: String) = "users/$uid/notebookImports/$jobId"

    override suspend fun createJob(ownerUid: String): Result<String, DataError.Network> =
        writer.createJob(ownerUid)

    override suspend fun uploadPage(
        ownerUid: String,
        jobId: String,
        jpeg: ByteArray,
    ): Result<String, DataError.Network> = guarded("uploadPage") {
        val pageId = pageIdFor(nowMillis(), Random.nextBytes(RANDOM_SUFFIX_BYTES))
        storage.reference.child("${storagePrefix(ownerUid, jobId)}/$pageId.jpg")
            .putData(jpeg.toStorageData(), FirebaseStorageMetadata(contentType = "image/jpeg"))
        pageId
    }

    override fun observePages(
        ownerUid: String,
        jobId: String,
    ): Flow<Result<List<ImportPage>, DataError.Network>> =
        jobDoc(ownerUid, jobId).collection("pages").snapshots()
            .map { snapshot ->
                val pages = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.data<NotebookPageDto>().toImportPage(doc.id) }.getOrNull()
                }.sortedBy { it.id }
                Result.Success(pages) as Result<List<ImportPage>, DataError.Network>
            }
            .retryWithFallback(fallback = Result.Error(DataError.Network.UNKNOWN)) { throwable, attempt ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "observePages failed; retrying (attempt ${attempt + 1})"
                }
            }

    override fun observeCards(
        ownerUid: String,
        jobId: String,
    ): Flow<Result<List<ReviewCard>, DataError.Network>> =
        cardsCollection(ownerUid, jobId).snapshots()
            .map { snapshot ->
                val cards = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.data<NotebookCardDto>().toReviewCard(doc.id, jobId) }.getOrNull()
                }.sortedWith(compareBy({ it.pageId }, { it.id.substringAfterLast('_').toIntOrNull() ?: 0 }))
                Result.Success(cards) as Result<List<ReviewCard>, DataError.Network>
            }
            .retryWithFallback(fallback = Result.Error(DataError.Network.UNKNOWN)) { throwable, attempt ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "observeCards failed; retrying (attempt ${attempt + 1})"
                }
            }

    override fun observeOpenJob(ownerUid: String): Flow<ImportJob?> =
        jobsCollection(ownerUid).where { "status" equalTo "open" }.snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc ->
                        runCatching {
                            val dto = doc.data<NotebookJobDto>()
                            // A job whose server timestamp is still pending is the newest.
                            val sortKey = dto.createdAt?.let { it.seconds } ?: Long.MAX_VALUE
                            sortKey to dto.toImportJob(doc.id)
                        }.getOrNull()
                    }
                    .maxByOrNull { it.first }
                    ?.second
            }
            .retryWithFallback(fallback = null) { throwable, attempt ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "observeOpenJob failed; retrying (attempt ${attempt + 1})"
                }
            }

    override fun observeJob(ownerUid: String, jobId: String): Flow<ImportJob?> =
        jobDoc(ownerUid, jobId).snapshots
            .map { doc ->
                if (!doc.exists) null else runCatching { doc.data<NotebookJobDto>().toImportJob(doc.id) }.getOrNull()
            }
            .retryWithFallback(fallback = null) { throwable, attempt ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "observeJob failed; retrying (attempt ${attempt + 1})"
                }
            }

    override suspend fun pageImageUrl(
        ownerUid: String,
        jobId: String,
        pageId: String,
    ): Result<String, DataError.Network> = guarded("pageImageUrl") {
        storage.reference.child("${storagePrefix(ownerUid, jobId)}/$pageId.jpg").getDownloadUrl()
    }

    override suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network> =
        writer.updateCard(ownerUid, card)

    override suspend fun deleteCard(
        ownerUid: String,
        jobId: String,
        cardId: String,
    ): EmptyResult<DataError.Network> =
        writer.deleteCard(ownerUid, jobId, cardId)

    override suspend fun retryPage(jobId: String, pageId: String): EmptyResult<DataError.Network> =
        try {
            functions.httpsCallable("retryNotebookPage").invoke(RetryNotebookPageRequest(jobId, pageId))
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            AppLogger.e(tag = TAG, throwable = e) { "retryPage failed code=${e.code}" }
            Result.Error(e.code.toNetworkError())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "retryPage failed: ${e::class.simpleName}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    override suspend fun finishJob(
        ownerUid: String,
        jobId: String,
        abandoned: Boolean,
    ): EmptyResult<DataError.Network> = writer.finishJob(ownerUid, jobId, abandoned)

    private inner class FirebaseRemote : NotebookImportRemote {
        override suspend fun createJobDoc(ownerUid: String): String {
            val doc = jobsCollection(ownerUid).document
            // Exactly the three keys the rules allow on create.
            doc.set(
                mapOf(
                    "createdAt" to FieldValue.serverTimestamp,
                    "status" to "open",
                    "pageCount" to 0,
                ),
            )
            return doc.id
        }

        override suspend fun updateCardDoc(ownerUid: String, card: ReviewCard) {
            val updates = (card.toUpdateFields() + ("updatedAt" to FieldValue.serverTimestamp))
                .toList().toTypedArray()
            @Suppress("SpreadOperator")
            cardsCollection(ownerUid, card.jobId).document(card.id).update(*updates)
        }

        override suspend fun setJobStatus(ownerUid: String, jobId: String, status: String) {
            jobDoc(ownerUid, jobId).update("status" to status)
        }

        override suspend fun deleteCardDoc(ownerUid: String, jobId: String, cardId: String) {
            cardsCollection(ownerUid, jobId).document(cardId).delete()
        }

        override suspend fun deleteJobImages(ownerUid: String, jobId: String) {
            storage.reference.child(storagePrefix(ownerUid, jobId)).listAll().items.forEach { item ->
                try {
                    item.delete()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    AppLogger.w(tag = TAG, throwable = e) { "image delete failed jobId=$jobId" }
                }
            }
        }
    }

    private suspend fun <T> guarded(op: String, block: suspend () -> T): Result<T, DataError.Network> =
        try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "$op failed: ${e::class.simpleName}" }
            Result.Error(DataError.Network.UNKNOWN)
        }

    private fun FunctionsExceptionCode.toNetworkError(): DataError.Network = when (this) {
        FunctionsExceptionCode.PERMISSION_DENIED -> DataError.Network.FORBIDDEN
        FunctionsExceptionCode.UNAUTHENTICATED -> DataError.Network.UNAUTHORIZED
        FunctionsExceptionCode.NOT_FOUND -> DataError.Network.NOT_FOUND
        FunctionsExceptionCode.RESOURCE_EXHAUSTED -> DataError.Network.TOO_MANY_REQUESTS
        FunctionsExceptionCode.UNAVAILABLE -> DataError.Network.NO_INTERNET
        FunctionsExceptionCode.DEADLINE_EXCEEDED -> DataError.Network.REQUEST_TIMEOUT
        else -> DataError.Network.UNKNOWN
    }
}

/**
 * `p` + upload time as zero-padded epoch millis + `_` + 8 hex characters. The time prefix makes plain
 * string order equal upload order across app restarts (an in-memory counter would reset); the random
 * suffix keeps ids unique when two uploads share a millisecond.
 */
internal fun pageIdFor(uploadMillis: Long, randomBytes: ByteArray): String {
    val hex = randomBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "p${uploadMillis.toString().padStart(PAGE_TIME_WIDTH, '0')}_$hex"
}

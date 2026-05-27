package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.dto.CustomGarmentTypeDto
import com.danzucker.stitchpad.core.data.mapper.toCustomGarmentType
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "CustomGarmentTypeRepo"

class FirebaseCustomGarmentTypeRepository(
    private val firestore: FirebaseFirestore,
) : CustomGarmentTypeRepository {

    private fun collection(userId: String) =
        firestore.collection("users").document(userId).collection("customGarmentTypes")

    override fun observe(
        userId: String
    ): Flow<Result<List<CustomGarmentType>, DataError.Network>> =
        collection(userId)
            .snapshots()
            .map<_, Result<List<CustomGarmentType>, DataError.Network>> { snapshot ->
                val customs = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<CustomGarmentTypeDto>().toCustomGarmentType() }.getOrNull()
                    }
                    .sortedWith(
                        compareByDescending<CustomGarmentType> { it.lastUsedAt }
                            .thenBy { it.name.lowercase() }
                    )
                Result.Success(customs)
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observe failed userId=$userId" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error(DataError.Network.UNKNOWN)
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            // Deterministic doc ID from the sanitised name — concurrent calls converge on
            // the same document so no duplicates are created.  Firestore rejects paths that
            // contain '/' or consist solely of '.'; replace those with '-'.
            val baseId = sanitizeDocId(trimmed)
            // Two names that differ only by punctuation (e.g. "Iro/Buba" vs "Iro.Buba")
            // can map to the same sanitized ID. Check if the stored name matches; if not,
            // fall back to a unique, timestamp-suffixed ID so each distinct name gets its
            // own document and the user doesn't inadvertently pick the wrong custom garment.
            val existingSnap = collection(userId).document(baseId).get()
            val docId = if (existingSnap.exists &&
                existingSnap.data<CustomGarmentTypeDto>().name.lowercase() != trimmed.lowercase()
            ) {
                "${baseId}-${now}"
            } else {
                baseId
            }
            val docRef = collection(userId).document(docId)
            val resolved = firestore.runTransaction {
                val snap = get(docRef)
                if (snap.exists) {
                    val existing = snap.data<CustomGarmentTypeDto>()
                    update(docRef, "lastUsedAt" to now)
                    existing.copy(lastUsedAt = now).toCustomGarmentType()
                } else {
                    val newDoc = CustomGarmentTypeDto(
                        id = docId,
                        name = trimmed,
                        createdAt = now,
                        lastUsedAt = now,
                    )
                    set(docRef, newDoc)
                    newDoc.toCustomGarmentType()
                }
            }
            Result.Success(resolved)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "upsert failed userId=$userId name=$trimmed" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    /**
     * Converts a tailor-typed garment name into a valid Firestore document ID.
     * Firestore rejects paths containing '/' and treats '.' or '..' as special.
     * We replace those characters with '-' so names like "Iro/Buba" are stored
     * deterministically without silently failing. The original [name] field in
     * the document retains the user's exact capitalisation.
     */
    private fun sanitizeDocId(name: String): String =
        name.lowercase()
            .replace(Regex("[/.]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "garment-${Clock.System.now().toEpochMilliseconds()}" }

    override suspend fun touch(
        userId: String,
        id: String
    ): EmptyResult<DataError.Network> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            collection(userId).document(id).update("lastUsedAt" to now)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "touch failed userId=$userId id=$id" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}

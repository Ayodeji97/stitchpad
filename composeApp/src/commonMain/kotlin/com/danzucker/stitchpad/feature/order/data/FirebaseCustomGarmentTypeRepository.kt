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
            // Deterministic doc ID from the normalised name — concurrent calls
            // converge on the same document so no duplicate entries are created.
            val docId = trimmed.lowercase()
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

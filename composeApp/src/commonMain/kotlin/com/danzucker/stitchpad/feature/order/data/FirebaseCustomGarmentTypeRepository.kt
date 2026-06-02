package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.dto.CustomGarmentTypeDto
import com.danzucker.stitchpad.core.data.mapper.toCustomGarmentType
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "CustomGarmentTypeRepo"

internal fun customGarmentUpsertFields(
    id: String,
    name: String,
    lastUsedAt: Long,
): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "lastUsedAt" to lastUsedAt,
)

/**
 * Keep the pre-offline-first ID scheme so existing custom garment docs are
 * reused after this PR. It does collapse names that differ only by `/` vs `.`,
 * but changing the encoding would create duplicate picker rows for current users.
 */
internal fun sanitizeCustomGarmentDocId(name: String): String =
    name.lowercase()
        .replace(Regex("[/.]"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "garment-${Clock.System.now().toEpochMilliseconds()}" }

class FirebaseCustomGarmentTypeRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
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

    @Suppress("ReturnCount")
    override suspend fun upsert(
        userId: String,
        name: String
    ): Result<CustomGarmentType, DataError.Network> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error(DataError.Network.UNKNOWN)
        val now = Clock.System.now().toEpochMilliseconds()
        val docId = sanitizeCustomGarmentDocId(trimmed)
        val dto = CustomGarmentTypeDto(
            id = docId,
            name = trimmed,
            createdAt = now,
            lastUsedAt = now,
        )
        val accepted = offlineWrites.enqueue("upsertCustomGarmentType id=$docId") {
            collection(userId).document(docId).set(
                customGarmentUpsertFields(
                    id = docId,
                    name = trimmed,
                    lastUsedAt = now,
                ),
                merge = true,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(dto.toCustomGarmentType())
    }

    override suspend fun touch(
        userId: String,
        id: String
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("touchCustomGarmentType id=$id") {
            collection(userId).document(id).update("lastUsedAt" to now)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }
}

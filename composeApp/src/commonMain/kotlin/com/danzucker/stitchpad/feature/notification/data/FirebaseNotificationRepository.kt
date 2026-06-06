package com.danzucker.stitchpad.feature.notification.data

import com.danzucker.stitchpad.core.data.dto.NotificationDto
import com.danzucker.stitchpad.core.data.mapper.toNotification
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "NotificationRepo"

class FirebaseNotificationRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
) : NotificationRepository {

    private fun collection(userId: String) =
        firestore.collection("users").document(userId).collection("notifications")

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override fun observeNotifications(userId: String): Flow<Result<List<Notification>, DataError.Network>> =
        collection(userId).snapshots()
            .map { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching {
                            doc.data(NotificationDto.serializer()).toNotification(doc.id)
                        }.getOrNull()
                    }
                    .sortedByDescending { it.createdAt }
                Result.Success(list) as Result<List<Notification>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeNotifications failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    // The GitLive Firebase SDK doesn't support `whereEqualTo(field, false)` cleanly
    // across platforms (same constraint as archived-orders filtering in
    // FirebaseOrderRepository). Count client-side from the full collection snapshot —
    // the per-user notification set is small enough that this is fine.
    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override fun observeUnreadCount(userId: String): Flow<Int> =
        collection(userId).snapshots()
            .map { snapshot ->
                snapshot.documents.count { doc ->
                    runCatching {
                        doc.data(NotificationDto.serializer()).isRead
                    }.getOrDefault(true).not()
                }
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeUnreadCount failed" }
                emit(0)
            }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("markAsRead userId=$userId id=$notificationId") {
            collection(userId).document(notificationId).set(mapOf("isRead" to true), merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }

    // Fetch the full collection, filter unread client-side (same platform-safety
    // rationale as observeUnreadCount), then fire-and-forget an update per doc.
    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun markAllRead(userId: String): EmptyResult<DataError.Network> {
        return try {
            val snapshot = collection(userId).get()
            snapshot.documents.forEach { doc ->
                val isRead = runCatching {
                    doc.data(NotificationDto.serializer()).isRead
                }.getOrDefault(true)
                if (!isRead) {
                    offlineWrites.enqueue("markAllRead userId=$userId id=${doc.id}") {
                        collection(userId).document(doc.id).set(mapOf("isRead" to true), merge = true)
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "markAllRead failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}

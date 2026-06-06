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

    // Derives the unread count from the same observeNotifications flow so there is
    // a single Firestore snapshot listener feeding both consumers. The dashboard
    // gets its own independent listener because it calls this method directly.
    override fun observeUnreadCount(userId: String): Flow<Int> =
        observeNotifications(userId).map { result ->
            when (result) {
                is Result.Success -> result.data.count { !it.isRead }
                is Result.Error -> 0
            }
        }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("markAsRead userId=$userId id=$notificationId") {
            collection(userId).document(notificationId).set(mapOf("isRead" to true), merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun markAllRead(userId: String, notificationIds: List<String>): EmptyResult<DataError.Network> {
        if (notificationIds.isEmpty()) return Result.Success(Unit)
        notificationIds.forEach { id ->
            offlineWrites.enqueue("markAllRead userId=$userId id=$id") {
                collection(userId).document(id).set(mapOf("isRead" to true), merge = true)
            }
        }
        return Result.Success(Unit)
    }
}

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

private const val TAG = "NotificationRepo"

// Backoff between snapshot-listener retries when Firestore errors out
// (permission-denied transient, network blip, deserialization crash).
// retryWhen keeps the listener alive so a transient failure doesn't leave
// the inbox permanently frozen until the ViewModel is recreated.
private const val SNAPSHOT_RETRY_DELAY_MS = 5_000L

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
            // retryWhen (not .catch) so the Firestore listener keeps running
            // after a transient failure (permission-denied, network blip,
            // deserialization crash). .catch + emit would END the flow and
            // leave the inbox + bell permanently frozen until the ViewModel
            // is recreated. Here we emit a Result.Error so the UI shows an
            // error state, then retry after a delay to self-heal.
            .retryWhen { cause, _ ->
                AppLogger.e(tag = TAG, throwable = cause) {
                    "observeNotifications failed; emitting error + retrying"
                }
                emit(Result.Error(DataError.Network.UNKNOWN))
                delay(SNAPSHOT_RETRY_DELAY_MS)
                true // keep the listener alive
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

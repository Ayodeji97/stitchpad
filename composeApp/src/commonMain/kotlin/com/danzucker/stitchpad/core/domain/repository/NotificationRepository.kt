package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    /** Live feed, newest first. */
    fun observeNotifications(userId: String): Flow<Result<List<Notification>, DataError.Network>>

    /** Live unread count (drives the dashboard bell). */
    fun observeUnreadCount(userId: String): Flow<Int>

    suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network>

    suspend fun markAllRead(userId: String): EmptyResult<DataError.Network>
}

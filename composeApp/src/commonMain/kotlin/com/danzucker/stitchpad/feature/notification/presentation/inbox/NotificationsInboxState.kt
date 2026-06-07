package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.presentation.UiText

data class NotificationsInboxState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}

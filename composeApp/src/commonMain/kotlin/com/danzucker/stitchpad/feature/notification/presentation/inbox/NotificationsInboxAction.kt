package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification

sealed interface NotificationsInboxAction {
    data object OnBackClick : NotificationsInboxAction
    data class OnNotificationClick(val notification: Notification) : NotificationsInboxAction
    data object OnMarkAllReadClick : NotificationsInboxAction
}

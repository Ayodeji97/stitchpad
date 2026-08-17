package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.presentation.UiText

data class NotificationsInboxState(
    val notifications: List<Notification> = emptyList(),
    /**
     * Unread rows INCLUDING ones hidden from display (unrecognised types — see the
     * UNKNOWN filter in the ViewModel).
     *
     * Gating "Mark all read" on the VISIBLE count alone made the dashboard bell
     * permanently unclearable: the bell counts unread docs server-side and knows
     * nothing about the filter, so a tailor whose only unread row was a type this
     * build does not recognise saw a badge, opened an empty inbox, and had no button
     * to clear it. `STAFF_PENDING` is exactly such a type and is already in production.
     */
    val totalUnreadCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}

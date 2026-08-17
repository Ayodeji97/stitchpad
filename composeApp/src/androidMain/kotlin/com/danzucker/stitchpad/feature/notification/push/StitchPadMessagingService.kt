package com.danzucker.stitchpad.feature.notification.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.danzucker.stitchpad.MainActivity
import com.danzucker.stitchpad.R
import com.danzucker.stitchpad.navigation.PushTargetParser
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val PUSH_TARGET_EXTRA = "target"
const val PUSH_TARGET_INBOX = "inbox"
const val PUSH_ORDER_ID_EXTRA = "orderId"
private const val DAILY_REMINDER_NOTIFICATION_ID = 2001
private const val ANNOUNCEMENT_NOTIFICATION_ID = 2002

/**
 * PendingIntent request codes. These MUST differ per notification id.
 *
 * With FLAG_UPDATE_CURRENT, two PendingIntents built with the same request code
 * are the same object: building the announcement's intent would rewrite the
 * extras of the still-live daily-summary intent, so tapping this morning's digest
 * would route to the announcement's target instead.
 */
private const val DAILY_REMINDER_REQUEST_CODE = 0
private const val ANNOUNCEMENT_REQUEST_CODE = 1

class StitchPadMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Return immediately: FCM's wakelock budget is tight, and registration
        // needs network + auth — both of which WorkManager can wait for instead
        // of this callback blocking on them. Unique work with REPLACE means a
        // newer token supersedes any still-queued registration.
        PushTokenRegistrationWorker.enqueue(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Backgrounded/killed apps: FCM auto-displays the `notification` payload. This
        // path is the FOREGROUND case only — post it ourselves so the tailor still sees it.
        val notification = message.notification ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val target = message.data[PUSH_TARGET_EXTRA] ?: PUSH_TARGET_INBOX
        val orderId = message.data[PUSH_ORDER_ID_EXTRA]
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(PUSH_TARGET_EXTRA, target)
            if (orderId != null) putExtra(PUSH_ORDER_ID_EXTRA, orderId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Trust the server's channel when it names one, so adding a future channel
        // needs a config change rather than an app release. Unknown ids fall back
        // to daily reminders — the channel every install is guaranteed to have.
        val channelId = when (notification.channelId) {
            ANNOUNCEMENTS_CHANNEL_ID -> ANNOUNCEMENTS_CHANNEL_ID
            else -> DAILY_REMINDERS_CHANNEL_ID
        }
        val isAnnouncement = channelId == ANNOUNCEMENTS_CHANNEL_ID

        // Per-order pushes (target == "order") must not collapse into one another or into the
        // daily summary: derive a stable id from the orderId and use it for BOTH the
        // notification id and the PendingIntent request code, so distinct orders get distinct
        // notifications + tap targets. Non-order pushes (inbox / to_collect summary)
        // intentionally keep sharing the one daily-reminder id/request-code — a daily summary
        // is meant to collapse to a single notification.
        //
        // Announcements get their own id so a tip never REPLACES a visible daily
        // summary, while two announcements still collapse onto each other — one
        // promo in the shade is plenty.
        val perOrderId = orderId?.takeIf { target == PushTargetParser.TARGET_ORDER }?.hashCode()
        val requestCode = perOrderId
            ?: if (isAnnouncement) ANNOUNCEMENT_REQUEST_CODE else DAILY_REMINDER_REQUEST_CODE
        val notificationId = perOrderId
            ?: if (isAnnouncement) ANNOUNCEMENT_NOTIFICATION_ID else DAILY_REMINDER_NOTIFICATION_ID
        val pending = PendingIntent.getActivity(
            this,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(notificationId, built)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the areNotificationsEnabled() check and notify().
        }
    }
}

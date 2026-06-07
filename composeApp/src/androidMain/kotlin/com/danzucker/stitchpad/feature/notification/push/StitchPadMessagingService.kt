package com.danzucker.stitchpad.feature.notification.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.danzucker.stitchpad.MainActivity
import com.danzucker.stitchpad.R
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val PUSH_TARGET_EXTRA = "target"
const val PUSH_TARGET_INBOX = "inbox"
private const val DAILY_REMINDER_NOTIFICATION_ID = 2001

class StitchPadMessagingService : FirebaseMessagingService(), KoinComponent {
    private val authRepository: AuthRepository by inject()
    private val registrar: PushTokenRegistrar by inject()

    override fun onNewToken(token: String) {
        // FCM holds a wakelock for the duration of this callback (background thread),
        // so block until the refreshed token is persisted rather than fire-and-forget.
        runBlocking {
            val userId = authRepository.getCurrentUser()?.id ?: return@runBlocking
            registrar.register(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Backgrounded/killed apps: FCM auto-displays the `notification` payload. This
        // path is the FOREGROUND case only — post it ourselves so the tailor still sees it.
        val notification = message.notification ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val target = message.data[PUSH_TARGET_EXTRA] ?: PUSH_TARGET_INBOX
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(PUSH_TARGET_EXTRA, target)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(this, DAILY_REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(DAILY_REMINDER_NOTIFICATION_ID, built)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the areNotificationsEnabled() check and notify().
        }
    }
}

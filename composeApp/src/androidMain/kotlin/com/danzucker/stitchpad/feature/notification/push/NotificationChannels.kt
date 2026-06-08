package com.danzucker.stitchpad.feature.notification.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val DAILY_REMINDERS_CHANNEL_ID = "daily_reminders"

@Suppress("ReturnCount")
fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(DAILY_REMINDERS_CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            DAILY_REMINDERS_CHANNEL_ID,
            "Daily reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Your morning summary of deadlines and money owed" },
    )
}

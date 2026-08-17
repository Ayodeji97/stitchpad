package com.danzucker.stitchpad.feature.notification.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.danzucker.stitchpad.R

/** Deadlines and money owed. The channel a tailor must never mute by accident. */
const val DAILY_REMINDERS_CHANNEL_ID = "daily_reminders"

/**
 * Tips, feature discovery, and release announcements — the engagement push.
 *
 * Separate from [DAILY_REMINDERS_CHANNEL_ID] on purpose: sharing one channel
 * means a tailor annoyed by a tip mutes their overdue-order reminders along with
 * it, trading real retention for reach.
 */
const val ANNOUNCEMENTS_CHANNEL_ID = "announcements"

/**
 * Creates every notification channel the app posts to. Called from
 * [com.danzucker.stitchpad.StitchPadApplication.onCreate].
 *
 * Each channel is created unconditionally on every launch. `createNotificationChannel`
 * is idempotent, and once a user has adjusted a channel the OS ignores our
 * importance/sound values — so re-creating is safe and preserves their choices.
 *
 * This used to early-return when the daily-reminders channel already existed,
 * which meant an EXISTING install could never receive a newly added channel: the
 * app would post to an id the device had never heard of. Do not reintroduce a
 * whole-function guard here; add channels to this list instead.
 */
fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

    manager.createNotificationChannel(
        NotificationChannel(
            DAILY_REMINDERS_CHANNEL_ID,
            context.getString(R.string.channel_daily_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_daily_reminders_description) },
    )

    manager.createNotificationChannel(
        // IMPORTANCE_LOW: shows in the shade, no sound, no heads-up. A promo that
        // buzzes a working tailor's phone is what gets an app muted wholesale.
        NotificationChannel(
            ANNOUNCEMENTS_CHANNEL_ID,
            context.getString(R.string.channel_announcements_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.channel_announcements_description) },
    )
}

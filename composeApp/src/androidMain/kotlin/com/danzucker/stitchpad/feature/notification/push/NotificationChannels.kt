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
 *
 * The `_v2` suffix is load-bearing. Android locks a channel's importance the first
 * time the app creates it, and no later code change can raise it — so devices that
 * had created the original `announcements` channel at IMPORTANCE_LOW would have been
 * stuck there permanently. Minting a new id is the ONLY way to ship a different
 * importance. Keep this in lockstep with ANNOUNCEMENTS_CHANNEL_ID in
 * functions/src/notifications/fcm.ts, and never edit an id in place — mint the next
 * one instead.
 */
const val ANNOUNCEMENTS_CHANNEL_ID = "announcements_v2"

/**
 * The original announcements channel, created at IMPORTANCE_LOW during testing.
 * Deleted on launch so a tailor who ran that build does not end up with two
 * identical "Tips & announcements" rows in Android's notification settings, one of
 * them dead. Harmless on devices that never had it.
 */
private const val LEGACY_ANNOUNCEMENTS_CHANNEL_ID = "announcements"

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

    // Drop the IMPORTANCE_LOW original so testers aren't left with two identical
    // "Tips & announcements" rows in settings. No-op if it was never created.
    manager.deleteNotificationChannel(LEGACY_ANNOUNCEMENTS_CHANNEL_ID)

    manager.createNotificationChannel(
        // IMPORTANCE_DEFAULT, matching daily reminders. IMPORTANCE_LOW was tried first
        // and is too quiet to work: no sound and no heads-up means a tailor mid-job
        // never notices it, which defeats the point of an activation nudge. What keeps
        // this from becoming spam is not the volume — it is that this channel is
        // separately mutable from order reminders, capped at twice a week, capped again
        // per campaign, and backed by its own opt-out toggle.
        NotificationChannel(
            ANNOUNCEMENTS_CHANNEL_ID,
            context.getString(R.string.channel_announcements_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_announcements_description) },
    )
}

package com.danzucker.stitchpad.core.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * Forwards WARNING/ERROR/ASSERT logs to Firebase Crashlytics.
 * - Messages become breadcrumbs via [FirebaseCrashlytics.log].
 * - Throwables on WARNING+ are reported as non-fatals via [FirebaseCrashlytics.recordException].
 *
 * Debug/Info logs stay local — they would just flood the dashboard.
 */
class CrashlyticsAntilog(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
) : Antilog() {

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?
    ) {
        if (priority < LogLevel.WARNING) return

        val line = buildString {
            append(priority.name)
            tag?.let { append('/').append(it) }
            message?.let { append(": ").append(it) }
        }
        crashlytics.log(line)

        if (throwable != null) {
            crashlytics.recordException(throwable)
        }
    }
}

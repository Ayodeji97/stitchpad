package com.danzucker.stitchpad.core.logging

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * Adds WARNING+ breadcrumbs to crash reports.
 *
 * Non-fatals are recorded only through [AppLogger.reportNonFatal] so expected
 * failures, such as invalid login credentials, do not flood Crashlytics.
 */
class CrashReportingAntilog(
    private val crashReporter: CrashReporter,
) : Antilog() {

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        if (priority < LogLevel.WARNING) return

        val line = buildString {
            append(priority.name)
            tag?.let { append('/').append(it) }
            message?.let { append(": ").append(it) }
            throwable?.let {
                append(" throwable=")
                append(it::class.simpleName ?: "Throwable")
            }
        }
        crashReporter.log(line)
    }
}

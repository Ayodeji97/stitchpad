package com.danzucker.stitchpad.core.logging

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * Forwards WARNING+ logs to crash reporting:
 * - every WARNING+ line becomes a breadcrumb via [CrashReporter.log]
 * - a WARNING+ log carrying a throwable is also recorded as a non-fatal via
 *   [CrashReporter.recordNonFatal], preserving the throwable's stack
 *
 * Debug/Info logs stay local so they don't flood the dashboard.
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

        if (throwable != null) {
            crashReporter.recordNonFatal(throwable = throwable, message = message)
        }
    }
}

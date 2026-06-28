package com.danzucker.stitchpad.core.logging

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Thin wrapper around Napier so call sites don't couple to a specific logger.
 * Add a Firebase Crashlytics antilog (or similar) via [extraAntilogs] when
 * release-time error reporting lands.
 *
 * Rules for log messages: log error class + message + stable IDs. Never log
 * tokens, API keys, full URLs, storage paths, or anything user-identifying.
 */
object AppLogger {
    private var crashReporter: CrashReporter = NoOpCrashReporter

    fun init(
        crashReporter: CrashReporter = NoOpCrashReporter,
        extraAntilogs: List<Antilog> = emptyList(),
    ) {
        this.crashReporter = crashReporter
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())
        extraAntilogs.forEach { Napier.base(it) }
    }

    fun d(tag: String? = null, message: () -> String) {
        Napier.d(tag = tag, message = message)
    }

    fun i(tag: String? = null, message: () -> String) {
        Napier.i(tag = tag, message = message)
    }

    fun w(tag: String? = null, throwable: Throwable? = null, message: () -> String) {
        Napier.w(tag = tag, throwable = throwable, message = message)
    }

    fun e(tag: String? = null, throwable: Throwable? = null, message: () -> String) {
        Napier.e(tag = tag, throwable = throwable, message = message)
    }

    fun reportNonFatal(tag: String? = null, throwable: Throwable, message: () -> String) {
        val logMessage = message()
        Napier.e(tag = tag, throwable = throwable) { logMessage }
        crashReporter.recordNonFatal(
            name = throwable::class.simpleName ?: "Throwable",
            message = logMessage,
            stackTrace = throwable.stackTraceToString(),
        )
    }

    fun setCrashUserId(userId: String?) {
        crashReporter.setUserId(userId)
    }

    fun setCrashCustomKey(key: String, value: String) {
        crashReporter.setCustomKey(key, value)
    }
}

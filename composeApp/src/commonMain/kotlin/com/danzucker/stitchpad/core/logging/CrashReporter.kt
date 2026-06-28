package com.danzucker.stitchpad.core.logging

/**
 * Platform Crashlytics bridge used from shared code.
 *
 * Keep values stable and low-cardinality. Do not pass email addresses, tokens,
 * full URLs, storage paths, or other user-identifying strings.
 */
interface CrashReporter {
    fun log(message: String)
    fun recordNonFatal(name: String, message: String?, stackTrace: String?)
    fun setUserId(userId: String?)
    fun setCustomKey(key: String, value: String)
}

object NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun recordNonFatal(name: String, message: String?, stackTrace: String?) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

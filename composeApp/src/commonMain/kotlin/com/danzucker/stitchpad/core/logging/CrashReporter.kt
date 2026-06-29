package com.danzucker.stitchpad.core.logging

/**
 * Platform Crashlytics bridge used from shared code.
 *
 * Keep values stable and low-cardinality. Do not pass email addresses, tokens,
 * full URLs, storage paths, or other user-identifying strings.
 */
interface CrashReporter {
    fun log(message: String)

    /**
     * Records a handled (non-fatal) failure. The [throwable] is passed through so
     * the platform reporter can preserve the original stack frames (Android records
     * the exception directly; iOS stringifies it for the Crashlytics NSError).
     */
    fun recordNonFatal(throwable: Throwable, message: String?)
    fun setUserId(userId: String?)
    fun setCustomKey(key: String, value: String)
}

object NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun recordNonFatal(throwable: Throwable, message: String?) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

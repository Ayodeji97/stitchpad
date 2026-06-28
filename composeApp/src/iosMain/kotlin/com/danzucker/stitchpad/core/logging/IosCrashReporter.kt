package com.danzucker.stitchpad.core.logging

interface NativeCrashReporter {
    fun log(message: String)
    fun recordNonFatal(name: String, message: String?, stackTrace: String?)
    fun setUserId(userId: String)
    fun setCustomKey(key: String, value: String)
}

var iosNativeCrashReporter: NativeCrashReporter? = null

class IosCrashReporter(
    private val nativeCrashReporter: NativeCrashReporter,
) : CrashReporter {

    override fun log(message: String) {
        nativeCrashReporter.log(message)
    }

    override fun recordNonFatal(name: String, message: String?, stackTrace: String?) {
        nativeCrashReporter.recordNonFatal(name = name, message = message, stackTrace = stackTrace)
    }

    override fun setUserId(userId: String?) {
        nativeCrashReporter.setUserId(userId.orEmpty())
    }

    override fun setCustomKey(key: String, value: String) {
        nativeCrashReporter.setCustomKey(key = key, value = value)
    }
}

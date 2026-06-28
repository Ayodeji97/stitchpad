package com.danzucker.stitchpad.core.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics

class AndroidCrashReporter(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : CrashReporter {

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun recordNonFatal(name: String, message: String?, stackTrace: String?) {
        crashlytics.recordException(
            KmpNonFatalException(
                name = name,
                detail = message,
                remoteStackTrace = stackTrace,
            )
        )
    }

    override fun setUserId(userId: String?) {
        crashlytics.setUserId(userId.orEmpty())
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
}

private class KmpNonFatalException(
    name: String,
    detail: String?,
    private val remoteStackTrace: String?,
) : RuntimeException("$name${detail?.let { ": $it" }.orEmpty()}") {
    override fun toString(): String = message.orEmpty()

    override fun printStackTrace() {
        super.printStackTrace()
        remoteStackTrace?.let(::println)
    }
}

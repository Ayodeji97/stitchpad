package com.danzucker.stitchpad.core.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics

class AndroidCrashReporter(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : CrashReporter {

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        // Record the original throwable so Crashlytics keeps the real stack frames
        // (and groups by them). The message is added as a breadcrumb for context.
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable)
    }

    override fun setUserId(userId: String?) {
        crashlytics.setUserId(userId.orEmpty())
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
}

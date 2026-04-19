package com.danzucker.stitchpad

import android.app.Application
import android.content.pm.ApplicationInfo
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.logging.CrashlyticsAntilog
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.android.ext.koin.androidContext

class StitchPadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // Disable Crashlytics' built-in uncaught-exception handler for debug builds so
        // local crashes don't pollute the production dashboard.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !isDebuggable
        AppLogger.init(
            extraAntilogs = if (isDebuggable) emptyList() else listOf(CrashlyticsAntilog())
        )
        initKoin {
            androidContext(this@StitchPadApplication)
        }
    }
}

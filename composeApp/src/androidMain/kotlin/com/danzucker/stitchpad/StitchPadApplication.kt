package com.danzucker.stitchpad

import android.app.Application
import android.content.pm.ApplicationInfo
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.logging.CrashlyticsAntilog
import org.koin.android.ext.koin.androidContext

class StitchPadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppLogger.init(
            extraAntilogs = if (isDebuggable) emptyList() else listOf(CrashlyticsAntilog())
        )
        initKoin {
            androidContext(this@StitchPadApplication)
        }
    }
}

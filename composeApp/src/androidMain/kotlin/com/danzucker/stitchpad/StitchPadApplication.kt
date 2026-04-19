package com.danzucker.stitchpad

import android.app.Application
import com.danzucker.stitchpad.core.logging.AppLogger
import org.koin.android.ext.koin.androidContext

class StitchPadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init()
        initKoin {
            androidContext(this@StitchPadApplication)
        }
    }
}

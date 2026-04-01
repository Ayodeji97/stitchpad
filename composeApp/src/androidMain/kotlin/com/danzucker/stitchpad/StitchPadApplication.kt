package com.danzucker.stitchpad

import android.app.Application

class StitchPadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}

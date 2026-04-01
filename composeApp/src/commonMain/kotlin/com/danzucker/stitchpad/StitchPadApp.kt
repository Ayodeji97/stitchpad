package com.danzucker.stitchpad

import com.danzucker.stitchpad.di.authDataModule
import com.danzucker.stitchpad.di.authPresentationModule
import com.danzucker.stitchpad.di.coreModule
import com.danzucker.stitchpad.di.onboardingModule
import com.danzucker.stitchpad.di.platformModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            coreModule,
            authDataModule,
            authPresentationModule,
            onboardingModule,
            platformModule
        )
    }
}

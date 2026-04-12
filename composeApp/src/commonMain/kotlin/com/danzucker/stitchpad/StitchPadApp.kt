package com.danzucker.stitchpad

import com.danzucker.stitchpad.di.authDataModule
import com.danzucker.stitchpad.di.authPresentationModule
import com.danzucker.stitchpad.di.coreModule
import com.danzucker.stitchpad.di.customerDataModule
import com.danzucker.stitchpad.di.customerPresentationModule
import com.danzucker.stitchpad.di.measurementDataModule
import com.danzucker.stitchpad.di.measurementPresentationModule
import com.danzucker.stitchpad.di.onboardingModule
import com.danzucker.stitchpad.di.platformModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(platformConfig: KoinAppDeclaration = {}) {
    startKoin {
        platformConfig()
        modules(
            coreModule,
            authDataModule,
            authPresentationModule,
            onboardingModule,
            customerDataModule,
            customerPresentationModule,
            measurementDataModule,
            measurementPresentationModule,
            platformModule
        )
    }
}

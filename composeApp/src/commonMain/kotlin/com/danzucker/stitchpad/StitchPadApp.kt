package com.danzucker.stitchpad

import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.di.analyticsModule
import com.danzucker.stitchpad.di.authDataModule
import com.danzucker.stitchpad.di.authPresentationModule
import com.danzucker.stitchpad.di.configDataModule
import com.danzucker.stitchpad.di.configPresentationModule
import com.danzucker.stitchpad.di.coreModule
import com.danzucker.stitchpad.di.customerDataModule
import com.danzucker.stitchpad.di.customerPresentationModule
import com.danzucker.stitchpad.di.dashboardPresentationModule
import com.danzucker.stitchpad.di.debugModule
import com.danzucker.stitchpad.di.freemiumModule
import com.danzucker.stitchpad.di.giftModule
import com.danzucker.stitchpad.di.goalsDataModule
import com.danzucker.stitchpad.di.goalsPresentationModule
import com.danzucker.stitchpad.di.measurementDataModule
import com.danzucker.stitchpad.di.measurementPresentationModule
import com.danzucker.stitchpad.di.notificationDataModule
import com.danzucker.stitchpad.di.notificationPresentationModule
import com.danzucker.stitchpad.di.onboardingModule
import com.danzucker.stitchpad.di.orderDataModule
import com.danzucker.stitchpad.di.orderPresentationModule
import com.danzucker.stitchpad.di.platformModule
import com.danzucker.stitchpad.di.referralModule
import com.danzucker.stitchpad.di.reportsPresentationModule
import com.danzucker.stitchpad.di.settingsDataModule
import com.danzucker.stitchpad.di.settingsPresentationModule
import com.danzucker.stitchpad.di.smartDataModule
import com.danzucker.stitchpad.di.smartPresentationModule
import com.danzucker.stitchpad.di.styleDataModule
import com.danzucker.stitchpad.di.stylePresentationModule
import com.danzucker.stitchpad.di.tutorialsModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(platformConfig: KoinAppDeclaration = {}) {
    startKoin {
        platformConfig()
        modules(
            coreModule,
            analyticsModule,
            configDataModule,
            configPresentationModule,
            authDataModule,
            authPresentationModule,
            onboardingModule,
            customerDataModule,
            customerPresentationModule,
            measurementDataModule,
            measurementPresentationModule,
            styleDataModule,
            stylePresentationModule,
            notificationDataModule,
            notificationPresentationModule,
            orderDataModule,
            orderPresentationModule,
            dashboardPresentationModule,
            goalsDataModule,
            goalsPresentationModule,
            reportsPresentationModule,
            settingsDataModule,
            settingsPresentationModule,
            smartDataModule,
            smartPresentationModule,
            freemiumModule,
            giftModule,
            referralModule,
            tutorialsModule,
            platformModule
        )
        if (isDebugBuild) {
            modules(debugModule)
        }
    }
}

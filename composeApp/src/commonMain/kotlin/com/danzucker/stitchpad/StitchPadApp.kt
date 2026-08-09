package com.danzucker.stitchpad

import com.danzucker.stitchpad.core.config.AUTH_EMULATOR_PORT
import com.danzucker.stitchpad.core.config.FIRESTORE_EMULATOR_PORT
import com.danzucker.stitchpad.core.config.STORAGE_EMULATOR_PORT
import com.danzucker.stitchpad.core.config.USE_FIREBASE_EMULATOR
import com.danzucker.stitchpad.core.config.firebaseEmulatorHost
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
import com.danzucker.stitchpad.di.reviewModule
import com.danzucker.stitchpad.di.settingsDataModule
import com.danzucker.stitchpad.di.settingsPresentationModule
import com.danzucker.stitchpad.di.smartDataModule
import com.danzucker.stitchpad.di.smartPresentationModule
import com.danzucker.stitchpad.di.staffModule
import com.danzucker.stitchpad.di.styleDataModule
import com.danzucker.stitchpad.di.stylePresentationModule
import com.danzucker.stitchpad.di.toCollectPresentationModule
import com.danzucker.stitchpad.di.tutorialsModule
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * QA-only: point Firebase Auth + Firestore + Storage at the local emulators when
 * [USE_FIREBASE_EMULATOR] is flipped on in a DEBUG build. Called before startKoin
 * (and before any Koin single touches Firebase) so it lands before first use.
 * Guarded by [isDebugBuild] so a release build can never connect to an emulator.
 */
private fun connectFirebaseEmulatorsIfEnabled() {
    if (!isDebugBuild || !USE_FIREBASE_EMULATOR) return
    val host = firebaseEmulatorHost()
    Firebase.firestore.useEmulator(host, FIRESTORE_EMULATOR_PORT)
    Firebase.auth.useEmulator(host, AUTH_EMULATOR_PORT)
    Firebase.storage.useEmulator(host, STORAGE_EMULATOR_PORT)
}

fun initKoin(platformConfig: KoinAppDeclaration = {}) {
    connectFirebaseEmulatorsIfEnabled()
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
            staffModule,
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
            toCollectPresentationModule,
            tutorialsModule,
            reviewModule,
            platformModule
        )
        if (isDebugBuild) {
            modules(debugModule)
        }
    }
}

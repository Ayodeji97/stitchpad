package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.sharing.DialerLauncher
import com.danzucker.stitchpad.core.sharing.OrderReceiptSharer
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.auth.data.IosSsoCredentialProvider
import com.danzucker.stitchpad.feature.auth.data.NativeGoogleSignInLauncher
import com.danzucker.stitchpad.feature.auth.data.SsoCredentialProvider
import com.danzucker.stitchpad.feature.measurement.data.MeasurementPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Set from iosApp's AppDelegate BEFORE doInitKoin is invoked. The Swift launcher
 * holds GIDSignIn and ASAuthorizationController calls that aren't reachable
 * from Kotlin/Native directly.
 */
var iosNativeGoogleSignInLauncher: NativeGoogleSignInLauncher? = null

actual val platformModule: Module = module {
    single { OnboardingPreferences() } bind OnboardingPreferencesStore::class
    single { MeasurementPreferences() } bind MeasurementPreferencesStore::class
    single { OrderReceiptSharer() }
    single { WhatsAppLauncher() }
    single { DialerLauncher() }
    single<SsoCredentialProvider> {
        val launcher = iosNativeGoogleSignInLauncher
            ?: error(
                "iosNativeGoogleSignInLauncher must be set from Swift before doInitKoin. " +
                    "Check iOSApp.swift's AppDelegate.didFinishLaunchingWithOptions."
            )
        IosSsoCredentialProvider(googleLauncher = launcher)
    }
}
